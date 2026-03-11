package net.classiclauncher.launcher.extension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.utano.ymlite.config.YmlConfig;
import lombok.Getter;
import net.classiclauncher.launcher.LauncherContext;

/**
 * Parsed representation of a remote extension manifest YAML file.
 *
 * <p>
 * The remote YAML format is:
 *
 * <pre>
 * name: My Extension
 * description: Adds custom game support
 * minLauncherVersion: "1.0.0"
 * pageUrl: https://example.com/my-extension
 * maven:
 *   groupId: com.example
 *   artifactId: my-ext
 *   version: 1.2.3
 * repositories:
 *   0:
 *     url: https://repo.example.com/maven2/
 * dependencies:
 *   0:
 *     groupId: com.dep
 *     artifactId: dep-lib
 *     version: 1.0.0
 *     repositories:
 *       0:
 *         url: https://repo.dep.com/maven2/
 * requiredExtensions:
 *   0:
 *     url: https://example.com/base-extension/manifest.yml
 * </pre>
 *
 * <p>
 * Use {@link #fetch(String)} to download and parse a manifest from a URL. The raw YAML is cached at
 * {@code <dataDir>/extensions/manifests/<url-hash>.yml} since {@link YmlConfig} requires a backing file.
 *
 * <p>
 * {@link #getManifestUrl()} returns the URL this manifest was fetched from — used by {@link Extensions} to detect
 * already-installed extensions and to store the source URL in the {@link ExtensionRecord}.
 */
@Getter
public final class ExtensionManifest {

	/**
	 * -- GETTER --
	 *  The URL this manifest was fetched from. Never
	 * ; may be empty for manually constructed instances.
	 */
	private final String manifestUrl;
	private final String name;
	private final String description;
	private final String minLauncherVersion;
	/**
	 * -- GETTER --
	 *  Optional URL of a page with details, screenshots, or documentation for this extension. May be empty.
	 */
	private final String pageUrl;
	private final String groupId;
	private final String artifactId;
	private final String version;
	private final List<String> repositoryUrls;
	private final List<DependencySpec> dependencies;
	/**
	 * -- GETTER --
	 *  Other extensions that must be installed before this one. Each entry points to a remote manifest URL; the launcher
	 *  fetches that manifest and recursively resolves its own requirements before installing this extension.
	 */
	private final List<RequiredExtensionSpec> requiredExtensions;

	private ExtensionManifest(String manifestUrl, String name, String description, String minLauncherVersion,
			String pageUrl, String groupId, String artifactId, String version, List<String> repositoryUrls,
			List<DependencySpec> dependencies, List<RequiredExtensionSpec> requiredExtensions) {
		this.manifestUrl = manifestUrl != null ? manifestUrl : "";
		this.name = name;
		this.description = description;
		this.minLauncherVersion = minLauncherVersion;
		this.pageUrl = pageUrl != null ? pageUrl : "";
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.repositoryUrls = Collections.unmodifiableList(repositoryUrls);
		this.dependencies = Collections.unmodifiableList(dependencies);
		this.requiredExtensions = Collections.unmodifiableList(requiredExtensions);
	}

	// ── Getters ───────────────────────────────────────────────────────────────

	/**
	 * Returns the Maven coordinate key ({@code groupId:artifactId}) that uniquely identifies this extension.
	 */
	public String getCoordinateKey() {
		return groupId + ":" + artifactId;
	}

	// ── Factory ───────────────────────────────────────────────────────────────

	/**
	 * Downloads the manifest at the given URL, writes it to a local cache file, and parses it into an
	 * {@link ExtensionManifest}.
	 *
	 * @param url
	 *            the remote manifest URL
	 * @return the parsed manifest with {@link #getManifestUrl()} set to {@code url}
	 * @throws IOException
	 *             if the download fails, the file cannot be written, or required fields (groupId, artifactId, version)
	 *             are missing/empty
	 */
	public static ExtensionManifest fetch(String url) throws IOException {
		if (url == null || url.trim().isEmpty()) throw new IOException("Manifest URL must not be null or empty");

		File cacheDir = LauncherContext.getInstance().resolve("extensions", "manifests");
		cacheDir.mkdirs();

		String hash = String.valueOf(Math.abs(url.hashCode()));
		File cacheFile = new File(cacheDir, hash + ".yml");

		// Download manifest to cache file
		try (InputStream in = new URL(url).openStream(); FileOutputStream out = new FileOutputStream(cacheFile)) {
			byte[] buf = new byte[4096];
			int n;
			while ((n = in.read(buf)) != -1) {
				out.write(buf, 0, n);
			}
		}

		YmlConfig config = new YmlConfig(cacheFile);
		config.load();

		String name = config.getString("name", "");
		String description = config.getString("description", "");
		String minLauncherVersion = config.getString("minLauncherVersion", "0.0.0");
		String pageUrl = config.getString("pageUrl", "");
		String groupId = config.getString("maven.groupId", "");
		String artifactId = config.getString("maven.artifactId", "");
		String version = config.getString("maven.version", "");

		if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty())
			throw new IOException("Manifest at " + url + " is missing required maven coordinates");

		List<String> repositoryUrls = new ArrayList<>();
		for (int i = 0;; i++) {
			String repoUrl = config.getString("repositories." + i + ".url", "");
			if (repoUrl.isEmpty()) break;
			repositoryUrls.add(repoUrl);
		}

		List<DependencySpec> dependencies = new ArrayList<>();
		for (int i = 0;; i++) {
			String depGroupId = config.getString("dependencies." + i + ".groupId", "");
			if (depGroupId.isEmpty()) break;
			String depArtifactId = config.getString("dependencies." + i + ".artifactId", "");
			String depVersion = config.getString("dependencies." + i + ".version", "");
			List<String> depRepos = new ArrayList<>();
			for (int j = 0;; j++) {
				String depRepoUrl = config.getString("dependencies." + i + ".repositories." + j + ".url", "");
				if (depRepoUrl.isEmpty()) break;
				depRepos.add(depRepoUrl);
			}
			dependencies.add(new DependencySpec(depGroupId, depArtifactId, depVersion, depRepos));
		}

		List<RequiredExtensionSpec> requiredExtensions = new ArrayList<>();
		for (int i = 0;; i++) {
			String reqUrl = config.getString("requiredExtensions." + i + ".url", "");
			if (reqUrl.isEmpty()) break;
			requiredExtensions.add(new RequiredExtensionSpec(reqUrl));
		}

		return new ExtensionManifest(url, name, description, minLauncherVersion, pageUrl, groupId, artifactId, version,
				repositoryUrls, dependencies, requiredExtensions);
	}

	/**
	 * Parses an {@link ExtensionManifest} from a local YAML file without any network access.
	 *
	 * <p>
	 * The returned manifest has an empty {@link #getManifestUrl()} since it was not fetched from a remote URL. This is
	 * used by the local-install flow where the user provides the manifest file and extension JAR directly from disk.
	 *
	 * @param file
	 *            the local manifest YAML file
	 * @return the parsed manifest
	 * @throws IOException
	 *             if the file cannot be read or required fields are missing/empty
	 */
	public static ExtensionManifest fromFile(File file) throws IOException {
		if (file == null || !file.exists()) throw new IOException("Manifest file does not exist: " + file);

		YmlConfig config = new YmlConfig(file);
		config.load();

		String name = config.getString("name", "");
		String description = config.getString("description", "");
		String minLauncherVersion = config.getString("minLauncherVersion", "0.0.0");
		String pageUrl = config.getString("pageUrl", "");
		String groupId = config.getString("maven.groupId", "");
		String artifactId = config.getString("maven.artifactId", "");
		String version = config.getString("maven.version", "");

		if (groupId.isEmpty() || artifactId.isEmpty() || version.isEmpty())
			throw new IOException("Manifest file is missing required maven coordinates: " + file.getName());

		List<String> repositoryUrls = new ArrayList<>();
		for (int i = 0;; i++) {
			String repoUrl = config.getString("repositories." + i + ".url", "");
			if (repoUrl.isEmpty()) break;
			repositoryUrls.add(repoUrl);
		}

		List<DependencySpec> dependencies = new ArrayList<>();
		for (int i = 0;; i++) {
			String depGroupId = config.getString("dependencies." + i + ".groupId", "");
			if (depGroupId.isEmpty()) break;
			String depArtifactId = config.getString("dependencies." + i + ".artifactId", "");
			String depVersion = config.getString("dependencies." + i + ".version", "");
			List<String> depRepos = new ArrayList<>();
			for (int j = 0;; j++) {
				String depRepoUrl = config.getString("dependencies." + i + ".repositories." + j + ".url", "");
				if (depRepoUrl.isEmpty()) break;
				depRepos.add(depRepoUrl);
			}
			dependencies.add(new DependencySpec(depGroupId, depArtifactId, depVersion, depRepos));
		}

		List<RequiredExtensionSpec> requiredExtensions = new ArrayList<>();
		for (int i = 0;; i++) {
			String reqUrl = config.getString("requiredExtensions." + i + ".url", "");
			if (reqUrl.isEmpty()) break;
			requiredExtensions.add(new RequiredExtensionSpec(reqUrl));
		}

		return new ExtensionManifest("", name, description, minLauncherVersion, pageUrl, groupId, artifactId, version,
				repositoryUrls, dependencies, requiredExtensions);
	}

	// ── Inner classes ─────────────────────────────────────────────────────────

	/**
	 * A transitive Maven dependency required by the extension at runtime. These are downloaded by the launcher's
	 * {@code LibraryManager} before the extension JAR is scanned.
	 */
	@Getter
	public static final class DependencySpec {

		private final String groupId;
		private final String artifactId;
		private final String version;
		private final List<String> repositoryUrls;

		public DependencySpec(String groupId, String artifactId, String version, List<String> repositoryUrls) {
			this.groupId = groupId;
			this.artifactId = artifactId;
			this.version = version;
			this.repositoryUrls = Collections
					.unmodifiableList(repositoryUrls != null ? repositoryUrls : new ArrayList<String>());
		}

	}

	/**
	 * A pointer to another extension that must be installed before this one.
	 *
	 * <p>
	 * The launcher fetches the manifest at {@link #getUrl()}, resolves that extension's own requirements recursively,
	 * and prompts the user to confirm each new dependency before installing the full chain.
	 */
	@Getter
	public static final class RequiredExtensionSpec {

		/**
		 * -- GETTER --
		 *  The manifest URL of the required extension. Never null or empty.
		 */
		private final String url;

		public RequiredExtensionSpec(String url) {
			if (url == null || url.trim().isEmpty())
				throw new IllegalArgumentException("RequiredExtensionSpec URL must not be null or empty");
			this.url = url.trim();
		}

	}

}
