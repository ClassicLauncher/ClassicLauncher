package net.classiclauncher.launcher.jre;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.update.install.DistributionMode;

/**
 * Manages the list of known Java runtime installations.
 *
 * <p>
 * Each installation is persisted as {@code <dataDir>/jre/<uuid>.yml}. At most one installation is marked as the
 * default; if none is marked, {@link #getDefault()} returns the first available installation.
 */
public class JavaManager {

	private final List<JavaInstallation> installations = new ArrayList<>();

	/**
	 * Loads all installations from {@code <dataDir>/jre/}.
	 */
	public void load() {
		installations.clear();
		File dir = LauncherContext.getInstance().resolve("jre");
		if (!dir.exists()) return;
		File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
		if (files == null) return;
		for (File file : files) {
			String id = file.getName().substring(0, file.getName().length() - 4);
			YmlConfig config = new YmlConfig(file);
			config.load();
			installations.add(JavaInstallation.fromConfig(id, config));
		}
	}

	/**
	 * Returns all known installations, with the jpackage-bundled JRE (if running from a jpackage app) prepended as the
	 * first entry.
	 */
	public List<JavaInstallation> getAll() {
		JavaInstallation bundled = detectBundledInstallation();
		if (bundled == null) return Collections.unmodifiableList(installations);
		List<JavaInstallation> result = new ArrayList<>();
		result.add(bundled);
		result.addAll(installations);
		return Collections.unmodifiableList(result);
	}

	/**
	 * Returns the installation explicitly marked as default, or the first available one, or {@link Optional#empty()} if
	 * none exist.
	 */
	public Optional<JavaInstallation> getDefault() {
		return installations.stream().filter(JavaInstallation::isDefaultInstallation).findFirst().map(Optional::of)
				.orElseGet(() -> installations.isEmpty() ? Optional.empty() : Optional.of(installations.get(0)));
	}

	/**
	 * Returns the installation with the given ID, or {@link Optional#empty()} if not found.
	 */
	public Optional<JavaInstallation> getById(String id) {
		return installations.stream().filter(i -> i.getId().equals(id)).findFirst();
	}

	/**
	 * Adds an installation and saves it to disk. Does nothing if an installation with the same canonical executable
	 * path already exists.
	 */
	public void add(JavaInstallation installation) {
		if (installations.stream()
				.anyMatch(i -> i.getExecutablePath().equalsIgnoreCase(installation.getExecutablePath()))) {
			return;
		}
		installations.add(installation);
		save(installation);
	}

	/**
	 * Removes the installation with the given ID and deletes its file. No-op for the built-in jpackage JRE.
	 */
	public void remove(String id) {
		if (JavaInstallation.BUNDLED_ID.equals(id)) return;
		installations.removeIf(i -> i.getId().equals(id));
		File file = new File(LauncherContext.getInstance().resolve("jre"), id + ".yml");
		if (file.exists()) file.delete();
	}

	/**
	 * Marks the installation with the given ID as default and saves all. All other installations have their default
	 * flag cleared.
	 */
	public void setDefault(String id) {
		List<JavaInstallation> updated = new ArrayList<>();
		for (JavaInstallation inst : installations) {
			updated.add(inst.withDefault(inst.getId().equals(id)));
		}
		installations.clear();
		installations.addAll(updated);
		for (JavaInstallation inst : installations) {
			save(inst);
		}
	}

	/**
	 * Runs auto-detection and adds any installations not already present. Returns the number of newly added
	 * installations.
	 */
	public int autoDetect() {
		List<JavaInstallation> detected = JavaDetector.detect();
		int added = 0;
		for (JavaInstallation inst : detected) {
			boolean exists = installations.stream()
					.anyMatch(i -> i.getExecutablePath().equalsIgnoreCase(inst.getExecutablePath()));
			if (!exists) {
				installations.add(inst);
				save(inst);
				added++;
			}
		}
		return added;
	}

	/**
	 * Returns the jpackage-bundled JRE when the launcher is running from a jpackage native installer, or {@code null}
	 * otherwise.
	 *
	 * <p>
	 * Detection is delegated to {@link DistributionMode#current()} — the single canonical location for the
	 * {@code jpackage.app-path} check. The executable path is derived from {@code java.home} and the version from
	 * {@code java.version} without spawning a subprocess.
	 */
	private static JavaInstallation detectBundledInstallation() {
		if (DistributionMode.current() == DistributionMode.JAR) return null;
		String javaHome = System.getProperty("java.home", "");
		if (javaHome.isEmpty()) return null;
		boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");
		String exe = javaHome + File.separator + "bin" + File.separator + (isWindows ? "java.exe" : "java");
		String version = System.getProperty("java.version", "");
		return JavaInstallation.bundled(exe, version);
	}

	private void save(JavaInstallation installation) {
		File dir = LauncherContext.getInstance().resolve("jre");
		dir.mkdirs();
		YmlConfig config = new YmlConfig(new File(dir, installation.getId() + ".yml"));
		installation.save(config);
	}

}
