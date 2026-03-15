package net.classiclauncher.launcher.extension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import dev.utano.librarymanager.*;
import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.LauncherVersion;
import net.classiclauncher.launcher.settings.Settings;
import top.wavelength.betterreflection.BetterReflectionClass;
import top.wavelength.betterreflection.lookup.JarClassFinder;

/**
 * Manages the lifecycle of launcher extensions: installation, loading, updating and removal.
 *
 * <p>
 * Extension records are persisted as individual YAML files under {@code <dataDir>/extensions/<id>.yml}. Extension JARs
 * are stored in the standard LibraryManager layout under {@code <dataDir>/libs/maven/}. Cached extension icons are
 * stored under {@code <dataDir>/extensions/icons/}.
 *
 * <p>
 * Call {@link #load()} during startup to restore previously installed extensions, then call
 * {@link #loadAll(CustomURLClassLoader, Settings)} (from {@code Main}) to add their JARs to the classloader in
 * dependency order and invoke each extension's {@link LauncherExtension#onLoad}. Any issues detected during loading
 * (circular dependencies, missing JARs, load failures) are accumulated and accessible via {@link #getLoadIssues()}.
 *
 * <h3>Dependency chain installation</h3> Extensions can declare other extensions as requirements via the
 * {@code requiredExtensions} block in their manifest. Use {@link #resolveInstallPlan(String)} to fetch and
 * topologically sort the full closure of required manifests before showing confirmation dialogs. Each resolved manifest
 * is wrapped in a {@link ResolvedDependency} that carries the display name of the direct requirer so the UI can show a
 * meaningful prompt. Once the user confirms, call {@link #installManifest(ExtensionManifest)} for each entry in the
 * returned list — dependencies are ordered first so the root extension is installed last.
 *
 * <h3>Load ordering and cycle detection</h3> {@link #loadAll} sorts enabled extensions topologically by their
 * {@code requiredExtensions} so that dependencies are always added to the classloader before the extensions that need
 * them. Circular dependencies among enabled extensions are detected before loading; all extensions in a cycle are
 * automatically disabled and reported via {@link #getLoadIssues()}.
 *
 * <h3>Extension identity</h3> Extensions are identified by their Maven coordinates ({@code groupId:artifactId}), not by
 * the URL they were installed from. This ensures that an extension installed from {@code file://...} is correctly
 * recognised as the same extension declared in another extension's {@code requiredExtensions} via an {@code https://}
 * URL.
 */
public final class Extensions {

	private final List<ExtensionRecord> records = new ArrayList<>();
	private final List<String> loadIssues = new ArrayList<>();
	/**
	 * IDs of extensions installed in the current JVM session that have not yet been loaded into the classloader. Used
	 * by the UI to display an "orange / restart required" indicator on their cards.
	 */
	private final Set<String> pendingRestartIds = new HashSet<>();
	/**
	 * Listeners notified (on the calling thread) whenever an extension is successfully installed via
	 * {@link #installManifest} or {@link #installLocal}. Registered via {@link #addInstallListener}.
	 */
	private final List<Runnable> installListeners = new ArrayList<>();

	// ── Load ──────────────────────────────────────────────────────────────────

	/**
	 * Scans {@code <dataDir>/extensions/} for record YAML files and deserializes them. Should be called once at startup
	 * before {@link #loadAll}.
	 */
	public void load() {
		records.clear();
		File dir = LauncherContext.getInstance().resolve("extensions");
		System.out.println("[Extensions.load] Scanning: " + dir.getAbsolutePath() + " (exists=" + dir.exists() + ")");
		if (!dir.exists()) return;
		File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
		if (files == null) {
			System.out.println("[Extensions.load] No record files found");
			return;
		}
		System.out.println("[Extensions.load] Found " + files.length + " record file(s)");
		for (File file : files) {
			String id = file.getName().substring(0, file.getName().length() - 4);
			YmlConfig config = new YmlConfig(file);
			config.load();
			ExtensionRecord record = ExtensionRecord.fromConfig(id, config);
			System.out.println("[Extensions.load] Loaded record: name=" + record.getName() + " groupId="
					+ record.getGroupId() + " artifactId=" + record.getArtifactId() + " version="
					+ record.getInstalledVersion() + " enabled=" + record.isEnabled() + " deps="
					+ record.getDependencies().size() + " requiredExts=" + record.getRequiredExtensionUrls().size());
			records.add(record);
		}
	}

	// ── Install ───────────────────────────────────────────────────────────────

	/**
	 * Downloads and installs a single extension from the given manifest URL, without resolving or prompting for
	 * required extensions.
	 *
	 * <p>
	 * For installations that should respect the full dependency chain, use {@link #resolveInstallPlan(String)} to
	 * obtain a topologically sorted plan, prompt the user for each required dependency, and call
	 * {@link #installManifest(ExtensionManifest)} for each accepted entry.
	 *
	 * @param manifestUrl
	 *            the remote manifest YAML URL
	 * @throws IOException
	 *             if the manifest cannot be fetched, the launcher version requirement is not met, the extension is
	 *             already installed, or the download fails
	 */
	public void install(String manifestUrl) throws IOException {
		if (manifestUrl == null || manifestUrl.trim().isEmpty())
			throw new IOException("Manifest URL must not be null or empty");
		ExtensionManifest manifest = ExtensionManifest.fetch(manifestUrl.trim());
		installManifest(manifest);
	}

	/**
	 * Installs a pre-fetched {@link ExtensionManifest}. The manifest's {@link ExtensionManifest#getManifestUrl()} is
	 * used as the installation source URL.
	 *
	 * <p>
	 * This method is idempotent: if a record with the same Maven coordinates ({@code groupId:artifactId}) already
	 * exists, the call returns silently without error.
	 *
	 * <p>
	 * After downloading, the extension JAR is probed for {@code /icon.svg} and {@code /icon.png} (in that order). If
	 * either is found it is extracted to {@code <dataDir>/extensions/icons/<id>.<ext>} so the icon is available even
	 * when the extension is disabled.
	 *
	 * @param manifest
	 *            the fully parsed manifest (must have {@code manifestUrl} set)
	 * @throws IOException
	 *             if the launcher version requirement is not met or the download fails
	 */
	public void installManifest(ExtensionManifest manifest) throws IOException {
		installManifest(manifest, InstallProgressListener.NOOP);
	}

	/**
	 * Installs a pre-fetched {@link ExtensionManifest}, reporting progress to the supplied listener.
	 *
	 * <p>
	 * Listener callbacks are invoked from the calling thread (typically a background {@code SwingWorker} thread).
	 * Implementations that update UI components must dispatch via {@code SwingUtilities.invokeLater}.
	 *
	 * @param manifest
	 *            the fully parsed manifest (must have {@code manifestUrl} set)
	 * @param listener
	 *            progress listener; use {@link InstallProgressListener#NOOP} if not needed
	 * @throws IOException
	 *             if the launcher version requirement is not met or the download fails
	 */
	public void installManifest(ExtensionManifest manifest, InstallProgressListener listener) throws IOException {
		if (listener == null) listener = InstallProgressListener.NOOP;

		if (!LauncherVersion.isAtLeast(manifest.getMinLauncherVersion())) {
			throw new IOException("Extension \"" + manifest.getName() + "\" requires launcher version "
					+ manifest.getMinLauncherVersion() + " or newer. Current: " + LauncherVersion.VERSION);
		}

		// Idempotent: skip if already installed (matched by Maven coordinates)
		String incomingKey = manifest.getCoordinateKey();
		for (ExtensionRecord existing : records) {
			if (incomingKey.equals(existing.getCoordinateKey())) {
				return;
			}
		}

		listener.onStep("Downloading " + manifest.getName() + " " + manifest.getVersion() + "…");
		downloadArtifacts(manifest.getGroupId(), manifest.getArtifactId(), manifest.getVersion(),
				manifest.getRepositoryUrls(), manifest.getDependencies(), listener);

		String id = UUID.randomUUID().toString();
		ExtensionRecord record = ExtensionRecord.fromManifest(id, manifest);

		// Extract and cache the icon from the downloaded JAR
		String baseDir = LauncherContext.getInstance().getDataDir().getAbsolutePath();
		Dependency mainDep = new Dependency(record.getGroupId(), record.getArtifactId(), record.getInstalledVersion(),
				null);
		File mainJar = new File(mainDep.getLocalPath(baseDir));
		if (mainJar.exists()) {
			listener.onStep("Extracting extension icon…");
			extractIcon(record, mainJar);
		}

		listener.onStep("Saving extension record…");
		YmlConfig config = new YmlConfig(LauncherContext.getInstance().resolve("extensions", id + ".yml"));
		record.save(config);
		records.add(record);
		pendingRestartIds.add(id);
		fireInstallListeners();
	}

	/**
	 * Installs an extension from a local manifest file and a local JAR file.
	 *
	 * <p>
	 * The JAR is copied to the standard LibraryManager path
	 * ({@code <dataDir>/libs/maven/<groupId>/<artifactId>/<version>/...}) so the extension loader finds it at startup.
	 * Runtime dependencies declared in the manifest are still downloaded from their configured repositories.
	 *
	 * <p>
	 * Required extensions declared in the manifest are <strong>not</strong> resolved by this method — the caller is
	 * responsible for installing them first (or they must already be installed).
	 *
	 * @param manifest
	 *            the manifest parsed from the local file (via {@link ExtensionManifest#fromFile})
	 * @param jarFile
	 *            the extension JAR on disk
	 * @param listener
	 *            progress listener; use {@link InstallProgressListener#NOOP} if not needed
	 * @throws IOException
	 *             if the launcher version requirement is not met, the JAR cannot be copied, or dependency download
	 *             fails
	 */
	public void installLocal(ExtensionManifest manifest, File jarFile, InstallProgressListener listener)
			throws IOException {
		if (listener == null) listener = InstallProgressListener.NOOP;

		if (!LauncherVersion.isAtLeast(manifest.getMinLauncherVersion())) {
			throw new IOException("Extension \"" + manifest.getName() + "\" requires launcher version "
					+ manifest.getMinLauncherVersion() + " or newer. Current: " + LauncherVersion.VERSION);
		}

		if (!jarFile.exists()) {
			throw new IOException("Extension JAR not found: " + jarFile.getAbsolutePath());
		}

		// Idempotent: skip if already installed (matched by Maven coordinates)
		String incomingKey = manifest.getCoordinateKey();
		for (ExtensionRecord existing : records) {
			if (incomingKey.equals(existing.getCoordinateKey())) {
				return;
			}
		}

		// Copy the JAR to the standard LibraryManager path
		listener.onStep("Copying " + jarFile.getName() + " to local library cache…");
		String baseDir = LauncherContext.getInstance().getDataDir().getAbsolutePath();
		Dependency mainDep = new Dependency(manifest.getGroupId(), manifest.getArtifactId(), manifest.getVersion(),
				null);
		File targetJar = new File(mainDep.getLocalPath(baseDir));
		targetJar.getParentFile().mkdirs();
		try (InputStream in = new java.io.FileInputStream(jarFile);
				FileOutputStream out = new FileOutputStream(targetJar)) {
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) != -1)
				out.write(buf, 0, n);
		}

		// Download runtime dependencies (libraries like Gson, MinecraftAuth, etc.)
		if (!manifest.getDependencies().isEmpty()) {
			listener.onStep("Downloading runtime dependencies…");
			downloadArtifacts(manifest.getGroupId(), manifest.getArtifactId(), manifest.getVersion(),
					manifest.getRepositoryUrls(), manifest.getDependencies(), listener);
		}

		String id = UUID.randomUUID().toString();
		ExtensionRecord record = ExtensionRecord.fromManifest(id, manifest);

		// Extract and cache the icon from the JAR
		if (targetJar.exists()) {
			listener.onStep("Extracting extension icon…");
			extractIcon(record, targetJar);
		}

		listener.onStep("Saving extension record…");
		YmlConfig config = new YmlConfig(LauncherContext.getInstance().resolve("extensions", id + ".yml"));
		record.save(config);
		records.add(record);
		pendingRestartIds.add(id);
		fireInstallListeners();
	}

	// ── Dependency chain resolution ───────────────────────────────────────────

	/**
	 * Resolves the full transitive closure of required extensions for the given manifest URL and returns an ordered
	 * install plan.
	 *
	 * <p>
	 * The returned list is topologically sorted so that each extension's requirements appear before it — the extension
	 * at {@code manifestUrl} is always last. Extensions that are already installed (matched by Maven coordinates) are
	 * silently excluded from the plan.
	 *
	 * <p>
	 * Each entry is wrapped in a {@link ResolvedDependency} carrying the display name of the direct requirer so the UI
	 * can show "Extension X requires Y" in its confirmation dialog. The root extension has
	 * {@link ResolvedDependency#requiredBy} set to {@code null}.
	 *
	 * <p>
	 * Circular dependency chains are detected and reported as an {@link IOException}.
	 *
	 * @param manifestUrl
	 *            the root manifest URL
	 * @return unmodifiable list of {@link ResolvedDependency} in install order (deps first, root last)
	 * @throws IOException
	 *             if any manifest in the chain cannot be fetched or a cycle is detected
	 */
	public List<ResolvedDependency> resolveInstallPlan(String manifestUrl) throws IOException {
		if (manifestUrl == null || manifestUrl.trim().isEmpty())
			throw new IOException("Manifest URL must not be null or empty");

		List<ResolvedDependency> result = new ArrayList<>();
		// grey: coordinate keys currently being visited — any re-encounter means a cycle
		Set<String> grey = new LinkedHashSet<>();
		// black: coordinate keys already added to result — skip on re-encounter (handles diamonds)
		Set<String> black = new HashSet<>();

		resolveRecursive(manifestUrl.trim(), null, grey, black, result);
		return Collections.unmodifiableList(result);
	}

	/**
	 * Recursive helper for {@link #resolveInstallPlan(String)}. Fetches the manifest at the given URL, uses its Maven
	 * coordinates for deduplication and cycle detection, and recurses into its required extensions.
	 *
	 * @param url
	 *            the manifest URL to fetch and resolve
	 * @param requiredBy
	 *            display name of the extension that listed this URL as a requirement, or {@code null} for the root
	 */
	private void resolveRecursive(String url, String requiredBy, Set<String> grey, Set<String> black,
			List<ResolvedDependency> result) throws IOException {
		// Fetch the manifest to discover its Maven coordinates
		ExtensionManifest manifest = ExtensionManifest.fetch(url);
		String coordKey = manifest.getCoordinateKey();

		// Already resolved in this plan (diamond dependency)
		if (black.contains(coordKey)) return;

		// Already installed on disk — skip
		for (ExtensionRecord record : records) {
			if (coordKey.equals(record.getCoordinateKey())) return;
		}

		// Cycle detection
		if (grey.contains(coordKey)) {
			throw new IOException("Circular dependency detected in extension requirements. "
					+ "The following chain leads back to an already-visited extension:\n" + String.join(" → ", grey)
					+ " → " + coordKey);
		}

		grey.add(coordKey);

		// Recursively resolve each required extension (depth-first)
		for (ExtensionManifest.RequiredExtensionSpec req : manifest.getRequiredExtensions()) {
			resolveRecursive(req.getUrl(), manifest.getName(), grey, black, result);
		}

		grey.remove(coordKey);
		black.add(coordKey);
		result.add(new ResolvedDependency(manifest, requiredBy));
	}

	// ── Uninstall ─────────────────────────────────────────────────────────────

	/**
	 * Removes the extension with the given ID. The record file, the extension's own JAR, and the cached icon file are
	 * deleted. Transitive dependency JARs are left in place (they may be shared with other extensions).
	 *
	 * @param id
	 *            the extension record ID
	 */
	public void uninstall(String id) {
		Optional<ExtensionRecord> opt = getById(id);
		if (!opt.isPresent()) return;
		ExtensionRecord record = opt.get();

		// Delete record file
		File recordFile = LauncherContext.getInstance().resolve("extensions", id + ".yml");
		if (recordFile.exists()) recordFile.delete();

		// Delete the extension's own JAR (dep JARs may be shared — leave them)
		String baseDir = LauncherContext.getInstance().getDataDir().getAbsolutePath();
		Dependency mainDep = new Dependency(record.getGroupId(), record.getArtifactId(), record.getInstalledVersion(),
				null);
		File jar = new File(mainDep.getLocalPath(baseDir));
		if (jar.exists()) jar.delete();

		// Delete cached icon
		deleteIconCache(id);

		// Delete cached manifest (if the extension was installed from a URL)
		deleteManifestCache(record.getManifestUrl());

		records.removeIf(r -> r.getId().equals(id));
	}

	// ── Update ────────────────────────────────────────────────────────────────

	/**
	 * Re-fetches the manifest for the given extension and, if a newer version is available, downloads the updated JAR,
	 * overwrites the record, and re-extracts the icon cache.
	 *
	 * @param id
	 *            the extension record ID
	 * @throws IOException
	 *             if the manifest cannot be fetched or the download fails
	 */
	public void update(String id) throws IOException {
		Optional<ExtensionRecord> opt = getById(id);
		if (!opt.isPresent()) throw new IOException("Extension not found: " + id);
		ExtensionRecord record = opt.get();

		ExtensionManifest manifest = ExtensionManifest.fetch(record.getManifestUrl());
		downloadArtifacts(manifest.getGroupId(), manifest.getArtifactId(), manifest.getVersion(),
				manifest.getRepositoryUrls(), manifest.getDependencies(), InstallProgressListener.NOOP);

		// Replace record in list
		records.removeIf(r -> r.getId().equals(id));
		ExtensionRecord updated = ExtensionRecord.fromManifest(id, manifest);
		updated.setEnabled(record.isEnabled());
		updated.setAutoUpdate(record.isAutoUpdate());
		YmlConfig config = new YmlConfig(LauncherContext.getInstance().resolve("extensions", id + ".yml"));
		updated.save(config);
		records.add(updated);

		// Re-extract icon from updated JAR
		String baseDir = LauncherContext.getInstance().getDataDir().getAbsolutePath();
		Dependency mainDep = new Dependency(updated.getGroupId(), updated.getArtifactId(),
				updated.getInstalledVersion(), null);
		File mainJar = new File(mainDep.getLocalPath(baseDir));
		if (mainJar.exists()) {
			deleteIconCache(id);
			extractIcon(updated, mainJar);
		}
	}

	// ── Check for updates ─────────────────────────────────────────────────────

	/**
	 * Re-fetches manifests for all installed extensions and returns records for which a newer version is available.
	 * Records whose manifest URL is empty or whose fetch fails are silently skipped.
	 *
	 * @return unmodifiable list of records that have a newer version available
	 */
	public List<ExtensionRecord> checkForUpdates() {
		List<ExtensionRecord> outdated = new ArrayList<>();
		for (ExtensionRecord record : records) {
			if (record.getManifestUrl() == null || record.getManifestUrl().isEmpty()) continue;
			try {
				ExtensionManifest manifest = ExtensionManifest.fetch(record.getManifestUrl());
				if (isNewerVersion(record.getInstalledVersion(), manifest.getVersion())) {
					outdated.add(record);
				}
			} catch (IOException e) {
				System.err
						.println("Could not fetch manifest for extension " + record.getName() + ": " + e.getMessage());
			}
		}
		return Collections.unmodifiableList(outdated);
	}

	// ── Load all ──────────────────────────────────────────────────────────────

	/**
	 * For each enabled extension (in dependency order): adds its JAR and dependency JARs to {@code classLoader}, scans
	 * the JAR for {@link LauncherExtension} implementations, instantiates them, and calls
	 * {@link LauncherExtension#onLoad(Settings)}.
	 *
	 * <h3>Load ordering</h3> Enabled extensions are topologically sorted by their {@code requiredExtensions} before
	 * loading, ensuring that a dependency's JAR is always on the classpath before the extensions that depend on it are
	 * scanned.
	 *
	 * <h3>Cycle detection</h3> If circular dependencies are found among enabled extensions, all extensions in each
	 * cycle are automatically disabled and saved to disk. An entry is added to {@link #getLoadIssues()} for each
	 * affected extension.
	 *
	 * <h3>Failure handling</h3> If a single extension fails to load (missing JAR, scan error, {@code onLoad}
	 * exception), it is automatically disabled, saved, and an issue is recorded. Loading continues for the remaining
	 * extensions.
	 *
	 * <p>
	 * Call {@link #getLoadIssues()} after this method returns to retrieve the full list of issues for display to the
	 * user.
	 *
	 * @param classLoader
	 *            the URL classloader used for the launcher's runtime
	 * @param settings
	 *            the fully initialized settings instance
	 */
	public void loadAll(CustomURLClassLoader classLoader, Settings settings) {
		loadIssues.clear();
		String baseDir = LauncherContext.getInstance().getDataDir().getAbsolutePath();
		System.out.println(
				"[Extensions.loadAll] Starting — " + records.size() + " record(s) in memory, baseDir=" + baseDir);

		// Build coordinateKey → record index for dependency graph traversal
		Map<String, ExtensionRecord> byCoord = buildCoordinateIndex();

		// Separate enabled records
		List<ExtensionRecord> enabled = new ArrayList<>();
		for (ExtensionRecord r : records) {
			if (r.isEnabled()) enabled.add(r);
		}

		// Detect cycles among enabled extensions; disable all in a cycle
		Set<String> cyclicIds = detectCycles(enabled, byCoord);
		for (String cyclicId : cyclicIds) {
			Optional<ExtensionRecord> opt = getById(cyclicId);
			if (opt.isPresent()) {
				ExtensionRecord r = opt.get();
				r.setEnabled(false);
				saveRecord(r);
				loadIssues.add("Extension \"" + r.getName()
						+ "\" was disabled because it is part of a circular dependency chain.");
				System.err.println("[Extensions.loadAll] Disabled cyclic extension: " + r.getName());
			}
		}

		// Re-filter enabled (some may now be disabled due to cycles)
		enabled.clear();
		for (ExtensionRecord r : records) {
			if (r.isEnabled()) enabled.add(r);
		}

		// Topologically sort so dependencies load before dependents
		List<ExtensionRecord> sorted = topoSort(enabled, byCoord);

		// Load each extension in dependency order
		for (ExtensionRecord record : sorted) {
			System.out.println(
					"[Extensions.loadAll] Processing: " + record.getName() + " (enabled=" + record.isEnabled() + ")");
			try {
				Dependency mainDep = new Dependency(record.getGroupId(), record.getArtifactId(),
						record.getInstalledVersion(), null);
				File mainJar = new File(mainDep.getLocalPath(baseDir));
				System.out.println("[Extensions.loadAll]   Main JAR path: " + mainJar.getAbsolutePath() + " (exists="
						+ mainJar.exists() + ")");

				if (!mainJar.exists()) {
					String msg = "Extension \"" + record.getName()
							+ "\" was disabled because its JAR was not found — re-install the extension.";
					System.err.println("[Extensions.loadAll]   " + msg);
					record.setEnabled(false);
					saveRecord(record);
					loadIssues.add(msg);
					continue;
				}

				classLoader.addURL(mainJar.toURI().toURL());
				System.out.println("[Extensions.loadAll]   Main JAR added to classloader");

				for (ExtensionManifest.DependencySpec dep : record.getDependencies()) {
					Dependency depObj = new Dependency(dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), null);
					File depJar = new File(depObj.getLocalPath(baseDir));
					System.out.println("[Extensions.loadAll]   Dep JAR: " + depObj + " path=" + depJar.getAbsolutePath()
							+ " (exists=" + depJar.exists() + ")");
					if (depJar.exists()) {
						classLoader.addURL(depJar.toURI().toURL());
						System.out.println("[Extensions.loadAll]   Dep JAR added to classloader");
					}
				}

				System.out.println("[Extensions.loadAll]   Scanning JAR for LauncherExtension implementations...");
				List<BetterReflectionClass<LauncherExtension>> found = new JarClassFinder<LauncherExtension>("")
						.recursive(true).jarFile(mainJar).classLoader(classLoader).ofType(LauncherExtension.class)
						.findClasses();
				System.out.println("[Extensions.loadAll]   Found " + found.size() + " LauncherExtension class(es)");
				for (BetterReflectionClass<LauncherExtension> brc : found) {
					System.out.println("[Extensions.loadAll]   Instantiating: " + brc.getName());
					LauncherExtension ext = brc.newInstance();
					System.out.println("[Extensions.loadAll]   Calling onLoad on: " + ext.getClass().getName());
					ext.onLoad(settings);
					System.out.println("[Extensions.loadAll]   onLoad returned for: " + ext.getClass().getName());
				}

			} catch (ZipException e) {
				// Corrupt JAR — delete so next launch triggers a clean re-download
				String baseDir2 = LauncherContext.getInstance().getDataDir().getAbsolutePath();
				Dependency mainDep = new Dependency(record.getGroupId(), record.getArtifactId(),
						record.getInstalledVersion(), null);
				File mainJar = new File(mainDep.getLocalPath(baseDir2));
				if (mainJar.exists()) mainJar.delete();
				String msg = "Extension \"" + record.getName()
						+ "\" was disabled because its JAR is corrupt — re-install the extension.";
				System.err.println("[Extensions.loadAll]   " + msg);
				record.setEnabled(false);
				saveRecord(record);
				loadIssues.add(msg);
			} catch (Exception e) {
				String msg = "Extension \"" + record.getName() + "\" was disabled because it failed to load: "
						+ e.getMessage();
				System.err.println("[Extensions.loadAll]   " + msg);
				e.printStackTrace();
				record.setEnabled(false);
				saveRecord(record);
				loadIssues.add(msg);
			}
		}
		System.out.println("[Extensions.loadAll] Done — " + loadIssues.size() + " issue(s)");
	}

	// ── Query ─────────────────────────────────────────────────────────────────

	/**
	 * Returns an unmodifiable view of all installed extension records.
	 */
	public List<ExtensionRecord> getAll() {
		return Collections.unmodifiableList(records);
	}

	/**
	 * Returns the record with the given ID, or empty if not found.
	 */
	public Optional<ExtensionRecord> getById(String id) {
		return records.stream().filter(r -> r.getId().equals(id)).findFirst();
	}

	/**
	 * Returns issues detected during the last call to {@link #loadAll}: circular dependencies, missing JARs, corrupt
	 * JARs, and load failures. Each entry is a human-readable sentence suitable for display in a dialog.
	 *
	 * @return unmodifiable list of issue strings; empty if loading was clean
	 */
	public List<String> getLoadIssues() {
		return Collections.unmodifiableList(loadIssues);
	}

	/**
	 * Returns {@code true} if the extension with the given ID was installed in the current JVM session and has not yet
	 * been loaded into the classloader (i.e. a launcher restart is required for it to become active).
	 *
	 * @param id
	 *            the extension record ID
	 */
	public boolean isPendingRestart(String id) {
		return id != null && pendingRestartIds.contains(id);
	}

	/**
	 * Registers a listener that is called (on the installing thread) whenever an extension is successfully installed
	 * via {@link #installManifest} or {@link #installLocal}. Intended for UI components that need to refresh their view
	 * when a new extension appears — callers should dispatch to the EDT themselves if required.
	 *
	 * @param listener
	 *            the callback to invoke; must not be {@code null}
	 */
	public void addInstallListener(Runnable listener) {
		if (listener != null) installListeners.add(listener);
	}

	private void fireInstallListeners() {
		for (Runnable listener : installListeners) {
			try {
				listener.run();
			} catch (Exception e) {
				System.err.println("[Extensions] Install listener threw an exception: " + e.getMessage());
			}
		}
	}

	// ── Enable / disable helpers ──────────────────────────────────────────────

	/**
	 * Sets the enabled state of the extension with the given ID and persists the change to disk.
	 *
	 * @param id
	 *            the extension record ID
	 * @param enabled
	 *            the new enabled state
	 */
	public void setEnabled(String id, boolean enabled) {
		Optional<ExtensionRecord> opt = getById(id);
		if (!opt.isPresent()) return;
		opt.get().setEnabled(enabled);
		saveRecord(opt.get());
	}

	/**
	 * Returns all installed extensions (regardless of enabled state) that directly require the given extension (matched
	 * by Maven coordinates via cached manifests).
	 *
	 * <p>
	 * Used by the UI to show "Used by N extensions" on each card.
	 *
	 * @param id
	 *            the extension record ID
	 * @return unmodifiable list of direct dependent records
	 */
	public List<ExtensionRecord> getDirectDependents(String id) {
		Optional<ExtensionRecord> opt = getById(id);
		if (!opt.isPresent()) return Collections.emptyList();
		String targetCoord = opt.get().getCoordinateKey();
		if (targetCoord == null || targetCoord.equals(":")) return Collections.emptyList();
		List<ExtensionRecord> result = new ArrayList<>();
		for (ExtensionRecord r : records) {
			if (r.getId().equals(id)) continue;
			for (String reqUrl : r.getRequiredExtensionUrls()) {
				String reqCoord = resolveUrlToCoordinateKey(reqUrl);
				if (targetCoord.equals(reqCoord)) {
					result.add(r);
					break;
				}
			}
		}
		return Collections.unmodifiableList(result);
	}

	/**
	 * Returns all enabled extensions that transitively depend on the extension with the given ID (matched by Maven
	 * coordinates via cached manifests).
	 *
	 * <p>
	 * The returned list is suitable for showing the user which extensions will also be disabled when they disable the
	 * target extension.
	 *
	 * @param id
	 *            the extension record ID
	 * @return unmodifiable list of enabled dependent records; empty if there are none
	 */
	public List<ExtensionRecord> getAllEnabledDependents(String id) {
		List<ExtensionRecord> result = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		visited.add(id);
		collectEnabledDependents(id, visited, result);
		return Collections.unmodifiableList(result);
	}

	/**
	 * Returns all installed extensions that the given extension transitively requires but that are currently disabled.
	 *
	 * <p>
	 * The returned list is suitable for showing the user which extensions will also be enabled when they enable the
	 * target extension.
	 *
	 * @param id
	 *            the extension record ID
	 * @return unmodifiable list of disabled required records; empty if there are none
	 */
	public List<ExtensionRecord> getAllDisabledRequirements(String id) {
		Map<String, ExtensionRecord> byCoord = buildCoordinateIndex();
		List<ExtensionRecord> result = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		collectDisabledRequirements(id, byCoord, visited, result);
		return Collections.unmodifiableList(result);
	}

	// ── Icon cache ────────────────────────────────────────────────────────────

	/**
	 * Returns the cached icon file for the given extension ID, or {@code null} if no icon was extracted (the extension
	 * has no icon, or has never been installed).
	 *
	 * <p>
	 * Checks for {@code <dataDir>/extensions/icons/<id>.svg} first, then {@code .png}.
	 *
	 * @param id
	 *            the extension record ID
	 * @return the icon {@link File}, or {@code null} if absent
	 */
	public File getIconCacheFile(String id) {
		File iconsDir = LauncherContext.getInstance().resolve("extensions", "icons");
		File svg = new File(iconsDir, id + ".svg");
		if (svg.exists()) return svg;
		File png = new File(iconsDir, id + ".png");
		if (png.exists()) return png;
		return null;
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	/**
	 * Extracts {@code /icon.svg} or {@code /icon.png} (whichever exists first) from the given extension JAR and saves
	 * it to {@code <dataDir>/extensions/icons/<id>.<ext>}. If neither entry is found the method returns silently.
	 */
	private void extractIcon(ExtensionRecord record, File mainJar) {
		File iconsDir = LauncherContext.getInstance().resolve("extensions", "icons");
		iconsDir.mkdirs();
		String[] candidates = {"icon.svg", "icon.png"};
		for (String candidate : candidates) {
			try (JarFile jf = new JarFile(mainJar)) {
				JarEntry entry = jf.getJarEntry(candidate);
				if (entry == null) continue;
				String ext = candidate.substring(candidate.lastIndexOf('.'));
				File iconFile = new File(iconsDir, record.getId() + ext);
				try (InputStream in = jf.getInputStream(entry); FileOutputStream out = new FileOutputStream(iconFile)) {
					byte[] buf = new byte[4096];
					int n;
					while ((n = in.read(buf)) != -1)
						out.write(buf, 0, n);
				}
				System.out.println("[Extensions.extractIcon] Extracted " + candidate + " for extension \""
						+ record.getName() + "\"");
				return;
			} catch (IOException e) {
				System.err.println("[Extensions.extractIcon] Could not extract icon for \"" + record.getName() + "\": "
						+ e.getMessage());
			}
		}
	}

	/**
	 * Deletes the cached manifest file for the given manifest URL (if non-empty). The file is at
	 * {@code <dataDir>/extensions/manifests/<Math.abs(url.hashCode())>.yml}.
	 */
	private void deleteManifestCache(String manifestUrl) {
		if (manifestUrl == null || manifestUrl.isEmpty()) return;
		File cacheDir = LauncherContext.getInstance().resolve("extensions", "manifests");
		String hash = String.valueOf(Math.abs(manifestUrl.hashCode()));
		File cacheFile = new File(cacheDir, hash + ".yml");
		if (cacheFile.exists()) {
			cacheFile.delete();
			System.out.println("[Extensions] Deleted cached manifest: " + cacheFile.getName() + " for " + manifestUrl);
		}
	}

	/**
	 * Deletes all cached icon files for the given extension ID.
	 */
	private void deleteIconCache(String id) {
		File iconsDir = LauncherContext.getInstance().resolve("extensions", "icons");
		for (String ext : new String[]{".svg", ".png"}) {
			File f = new File(iconsDir, id + ext);
			if (f.exists()) f.delete();
		}
	}

	/**
	 * Persists the current state of an extension record to its YAML file.
	 */
	private void saveRecord(ExtensionRecord record) {
		File file = LauncherContext.getInstance().resolve("extensions", record.getId() + ".yml");
		YmlConfig config = new YmlConfig(file);
		if (file.exists()) config.load();
		record.save(config);
	}

	/**
	 * Builds a {@code coordinateKey → ExtensionRecord} map for all records with valid Maven coordinates.
	 */
	private Map<String, ExtensionRecord> buildCoordinateIndex() {
		Map<String, ExtensionRecord> index = new HashMap<>();
		for (ExtensionRecord r : records) {
			String key = r.getCoordinateKey();
			if (key != null && !key.equals(":")) {
				index.put(key, r);
				System.out.println("[Extensions] coordinateIndex: " + key + " → " + r.getName());
			}
		}
		return index;
	}

	/**
	 * Resolves a manifest URL to a Maven coordinate key ({@code groupId:artifactId}) by reading the cached manifest
	 * file at {@code <dataDir>/extensions/manifests/<url-hash>.yml}. Returns {@code null} if the cached manifest does
	 * not exist or cannot be parsed.
	 */
	private String resolveUrlToCoordinateKey(String manifestUrl) {
		if (manifestUrl == null || manifestUrl.isEmpty()) return null;
		File cacheDir = LauncherContext.getInstance().resolve("extensions", "manifests");
		String hash = String.valueOf(Math.abs(manifestUrl.hashCode()));
		File cacheFile = new File(cacheDir, hash + ".yml");
		if (!cacheFile.exists()) {
			System.out.println("[Extensions] resolveUrlToCoordinateKey: no cached manifest for " + manifestUrl
					+ " (expected " + cacheFile.getName() + ")");
			return null;
		}
		try {
			YmlConfig config = new YmlConfig(cacheFile);
			config.load();
			String groupId = config.getString("maven.groupId", "");
			String artifactId = config.getString("maven.artifactId", "");
			if (groupId.isEmpty() || artifactId.isEmpty()) {
				System.out.println("[Extensions] resolveUrlToCoordinateKey: cached manifest " + cacheFile.getName()
						+ " missing maven coordinates");
				return null;
			}
			String key = groupId + ":" + artifactId;
			System.out.println("[Extensions] resolveUrlToCoordinateKey: " + manifestUrl + " → " + key + " (from "
					+ cacheFile.getName() + ")");
			return key;
		} catch (Exception e) {
			System.err.println("[Extensions] resolveUrlToCoordinateKey: failed to read " + cacheFile.getName() + ": "
					+ e.getMessage());
			return null;
		}
	}

	/**
	 * Resolves a required-extension URL to the installed {@link ExtensionRecord} that matches by Maven coordinates.
	 * Reads the cached manifest for the URL to discover its {@code groupId:artifactId}, then looks up the coordinate in
	 * the given index.
	 *
	 * <p>
	 * If the cached manifest resolves to a coordinate key that does not match any installed record (stale cache), the
	 * method falls back to matching the URL directly against each record's {@link ExtensionRecord#getManifestUrl()}.
	 *
	 * @return the matching record, or {@code null} if the URL cannot be resolved or no matching record is installed
	 */
	private ExtensionRecord resolveRequiredExtension(String reqUrl, Map<String, ExtensionRecord> byCoord) {
		String coordKey = resolveUrlToCoordinateKey(reqUrl);
		if (coordKey != null) {
			ExtensionRecord record = byCoord.get(coordKey);
			if (record != null) {
				System.out.println("[Extensions] resolveRequiredExtension: " + reqUrl + " → " + record.getName()
						+ " (via coordinate " + coordKey + ")");
				return record;
			}
			System.out.println("[Extensions] resolveRequiredExtension: coordinate " + coordKey
					+ " from cache does not match any installed record — cache may be stale");
		}

		// Fallback: match by record's manifestUrl
		for (ExtensionRecord r : records) {
			if (reqUrl.equals(r.getManifestUrl())) {
				System.out.println("[Extensions] resolveRequiredExtension: " + reqUrl + " → " + r.getName()
						+ " (via manifestUrl match)");
				return r;
			}
		}

		System.out.println("[Extensions] resolveRequiredExtension: unable to resolve " + reqUrl);
		return null;
	}

	// ── Cycle detection ───────────────────────────────────────────────────────

	/**
	 * Detects cycles among the given enabled records and returns the IDs of all records that participate in at least
	 * one cycle.
	 */
	private Set<String> detectCycles(List<ExtensionRecord> enabled, Map<String, ExtensionRecord> byCoord) {
		Set<String> allCyclic = new HashSet<>();
		Set<String> visited = new HashSet<>();
		LinkedHashSet<String> inStack = new LinkedHashSet<>();
		for (ExtensionRecord r : enabled) {
			if (!visited.contains(r.getId())) {
				dfsCycle(r, byCoord, visited, inStack, allCyclic);
			}
		}
		return allCyclic;
	}

	private void dfsCycle(ExtensionRecord r, Map<String, ExtensionRecord> byCoord, Set<String> visited,
			LinkedHashSet<String> inStack, Set<String> cyclic) {
		if (inStack.contains(r.getId())) {
			// The cycle consists of r and everything after it in the current DFS path
			boolean marking = false;
			for (String id : inStack) {
				if (id.equals(r.getId())) marking = true;
				if (marking) cyclic.add(id);
			}
			return;
		}
		if (visited.contains(r.getId())) return;

		inStack.add(r.getId());
		for (String reqUrl : r.getRequiredExtensionUrls()) {
			ExtensionRecord dep = resolveRequiredExtension(reqUrl, byCoord);
			if (dep != null && dep.isEnabled()) {
				dfsCycle(dep, byCoord, visited, inStack, cyclic);
			}
		}
		inStack.remove(r.getId());
		visited.add(r.getId());
	}

	// ── Topological sort ──────────────────────────────────────────────────────

	/**
	 * Returns the given enabled records in dependency-first topological order. Assumes no cycles exist in the input
	 * (call {@link #detectCycles} first).
	 */
	private List<ExtensionRecord> topoSort(List<ExtensionRecord> enabled, Map<String, ExtensionRecord> byCoord) {
		List<ExtensionRecord> result = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		System.out.println("[Extensions.topoSort] Sorting " + enabled.size() + " enabled extension(s)");
		for (ExtensionRecord r : enabled) {
			System.out.println("[Extensions.topoSort]   " + r.getName() + " coord=" + r.getCoordinateKey()
					+ " requiredExtUrls=" + r.getRequiredExtensionUrls().size());
			if (!visited.contains(r.getId())) {
				dfsSort(r, byCoord, visited, result);
			}
		}
		System.out.println("[Extensions.topoSort] Result order:");
		for (int i = 0; i < result.size(); i++) {
			System.out.println("[Extensions.topoSort]   " + i + ": " + result.get(i).getName());
		}
		return result;
	}

	private void dfsSort(ExtensionRecord r, Map<String, ExtensionRecord> byCoord, Set<String> visited,
			List<ExtensionRecord> result) {
		visited.add(r.getId());
		for (String reqUrl : r.getRequiredExtensionUrls()) {
			System.out.println("[Extensions.dfsSort] " + r.getName() + " requires URL: " + reqUrl);
			ExtensionRecord dep = resolveRequiredExtension(reqUrl, byCoord);
			if (dep == null) {
				System.out.println("[Extensions.dfsSort]   → NOT RESOLVED (extension may not be installed)");
			} else if (!dep.isEnabled()) {
				System.out.println("[Extensions.dfsSort]   → resolved to " + dep.getName() + " but it is DISABLED");
			} else if (visited.contains(dep.getId())) {
				System.out.println("[Extensions.dfsSort]   → resolved to " + dep.getName() + " (already visited)");
			} else {
				System.out.println("[Extensions.dfsSort]   → resolved to " + dep.getName() + " — recursing");
				dfsSort(dep, byCoord, visited, result);
			}
		}
		result.add(r);
	}

	// ── Transitive dependency / dependent traversal ───────────────────────────

	private void collectEnabledDependents(String id, Set<String> visited, List<ExtensionRecord> result) {
		Optional<ExtensionRecord> opt = getById(id);
		if (!opt.isPresent()) return;
		String targetCoord = opt.get().getCoordinateKey();
		if (targetCoord == null || targetCoord.equals(":")) return;
		for (ExtensionRecord r : records) {
			if (!r.isEnabled() || visited.contains(r.getId())) continue;
			for (String reqUrl : r.getRequiredExtensionUrls()) {
				String reqCoord = resolveUrlToCoordinateKey(reqUrl);
				if (targetCoord.equals(reqCoord)) {
					visited.add(r.getId());
					result.add(r);
					collectEnabledDependents(r.getId(), visited, result);
					break;
				}
			}
		}
	}

	private void collectDisabledRequirements(String id, Map<String, ExtensionRecord> byCoord, Set<String> visited,
			List<ExtensionRecord> result) {
		Optional<ExtensionRecord> opt = getById(id);
		if (!opt.isPresent()) return;
		for (String reqUrl : opt.get().getRequiredExtensionUrls()) {
			ExtensionRecord dep = resolveRequiredExtension(reqUrl, byCoord);
			if (dep != null && !dep.isEnabled() && !visited.contains(dep.getId())) {
				visited.add(dep.getId());
				result.add(dep);
				collectDisabledRequirements(dep.getId(), byCoord, visited, result);
			}
		}
	}

	// ── Artifact download ─────────────────────────────────────────────────────

	/**
	 * Downloads the main artifact and all declared dependency artifacts via LibraryManager. Creates a fresh
	 * {@link LibraryManager} each time so download state does not bleed across calls.
	 *
	 * @param listener
	 *            progress listener; must not be {@code null} (use {@link InstallProgressListener#NOOP})
	 */
	private void downloadArtifacts(String groupId, String artifactId, String version, List<String> repoUrls,
			List<ExtensionManifest.DependencySpec> deps, InstallProgressListener listener) throws IOException {
		String baseDir = LauncherContext.getInstance().getDataDir().getAbsolutePath();
		LibraryManager lm = new LibraryManager(baseDir);

		lm.setDownloadListener(new DownloadListener() {

			@Override
			public void onDownloadStarted(Dependency dep, long totalBytes) {
				listener.onDownloadStarted(dep.getJarName(), totalBytes);
			}

			@Override
			public void onDownloadProgress(Dependency dep, long bytesDownloaded, long totalBytes) {
				listener.onDownloadProgress(dep.getJarName(), bytesDownloaded, totalBytes);
			}

			@Override
			public void onDownloadCompleted(Dependency dep) {
				listener.onDownloadCompleted(dep.getJarName());
			}

			@Override
			public void onDownloadSkipped(Dependency dep) {
				listener.onDownloadSkipped(dep.getJarName());
			}

		});

		Set<String> addedRepoUrls = new HashSet<>();
		for (String repoUrl : repoUrls) {
			if (addedRepoUrls.add(repoUrl)) {
				lm.addRepository(new Repository(repoUrl));
			}
		}
		for (ExtensionManifest.DependencySpec dep : deps) {
			for (String repoUrl : dep.getRepositoryUrls()) {
				if (addedRepoUrls.add(repoUrl)) {
					lm.addRepository(new Repository(repoUrl));
				}
			}
		}

		lm.addDependency(new Dependency(groupId, artifactId, version, null));
		for (ExtensionManifest.DependencySpec dep : deps) {
			lm.addDependency(new Dependency(dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), null));
		}

		lm.downloadLibraries();
	}

	/**
	 * Returns {@code true} if {@code available} is a higher semver than {@code current}.
	 */
	private static boolean isNewerVersion(String current, String available) {
		if (current == null || current.isEmpty()) return true;
		if (available == null || available.isEmpty()) return false;
		String[] partsA = current.split("\\.");
		String[] partsB = available.split("\\.");
		int len = Math.max(partsA.length, partsB.length);
		for (int i = 0; i < len; i++) {
			int numA = i < partsA.length ? parseVersionPart(partsA[i]) : 0;
			int numB = i < partsB.length ? parseVersionPart(partsB[i]) : 0;
			if (numA != numB) return numB > numA;
		}
		return false;
	}

	private static int parseVersionPart(String part) {
		try {
			return Integer.parseInt(part.replaceAll("[^0-9]", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// ── Public value types ────────────────────────────────────────────────────

	/**
	 * An entry in an install plan returned by {@link #resolveInstallPlan(String)}.
	 *
	 * <p>
	 * Wraps a fetched {@link ExtensionManifest} with the display name of the extension that directly required it, so
	 * the UI can show a meaningful prompt such as "Extension 'Microsoft Account' requires 'Minecraft Java Game'".
	 *
	 * <p>
	 * Entries are ordered so that each extension's requirements appear before it in the list. The root extension (the
	 * one originally requested by the user) is always the last entry and has {@link #requiredBy} set to {@code null}.
	 */
	public static final class ResolvedDependency {

		/**
		 * The fetched manifest for this extension.
		 */
		public final ExtensionManifest manifest;

		/**
		 * Display name of the extension that declared this one as a requirement, or {@code null} if this is the root
		 * extension the user explicitly requested.
		 */
		public final String requiredBy;

		ResolvedDependency(ExtensionManifest manifest, String requiredBy) {
			this.manifest = manifest;
			this.requiredBy = requiredBy;
		}

	}

}
