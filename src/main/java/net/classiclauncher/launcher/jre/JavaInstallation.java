package net.classiclauncher.launcher.jre;

import java.util.UUID;

import dev.utano.ymlite.config.YmlConfig;

/**
 * Represents a known Java runtime installation.
 *
 * <p>
 * Instances are persisted to {@code <dataDir>/jre/<uuid>.yml}. Each file stores: name, path, version, default flag, and
 * whether it was auto-detected.
 */
public class JavaInstallation {

	/**
	 * Fixed ID used for the jpackage-bundled JRE so it can be identified without equality checks.
	 */
	public static final String BUNDLED_ID = "bundled";

	private final String id;
	private final String displayName;
	private final String executablePath;
	private final String version;
	private final boolean defaultInstallation;
	private final boolean autoDetected;
	private final boolean builtIn;

	public JavaInstallation(String id, String displayName, String executablePath, String version,
			boolean defaultInstallation, boolean autoDetected, boolean builtIn) {
		this.id = id;
		this.displayName = displayName;
		this.executablePath = executablePath;
		this.version = version;
		this.defaultInstallation = defaultInstallation;
		this.autoDetected = autoDetected;
		this.builtIn = builtIn;
	}

	/**
	 * Creates a new auto-detected installation (not marked as default).
	 */
	public static JavaInstallation detected(String displayName, String executablePath, String version) {
		return new JavaInstallation(UUID.randomUUID().toString(), displayName, executablePath, version, false, true,
				false);
	}

	/**
	 * Creates a new manually-added installation (not marked as default).
	 */
	public static JavaInstallation manual(String executablePath, String version) {
		String displayName = version.isEmpty() ? executablePath : "Java " + version + " (manual)";
		return new JavaInstallation(UUID.randomUUID().toString(), displayName, executablePath, version, false, false,
				false);
	}

	/**
	 * Creates the built-in jpackage-bundled JRE entry. Uses {@link #BUNDLED_ID} as its ID so callers can identify it
	 * without string comparison on the path.
	 */
	public static JavaInstallation bundled(String executablePath, String version) {
		String displayName = version.isEmpty() ? "Bundled JRE" : "Bundled JRE (" + version + ")";
		return new JavaInstallation(BUNDLED_ID, displayName, executablePath, version, false, false, true);
	}

	public String getId() {
		return id;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getExecutablePath() {
		return executablePath;
	}

	public String getVersion() {
		return version;
	}

	public boolean isDefaultInstallation() {
		return defaultInstallation;
	}

	public boolean isAutoDetected() {
		return autoDetected;
	}

	/**
	 * Returns {@code true} if this installation represents the JRE bundled by jpackage. Built-in installations cannot
	 * be removed.
	 */
	public boolean isBuiltIn() {
		return builtIn;
	}

	/**
	 * Returns a copy of this installation with the default flag set to {@code isDefault}.
	 */
	public JavaInstallation withDefault(boolean isDefault) {
		return new JavaInstallation(id, displayName, executablePath, version, isDefault, autoDetected, builtIn);
	}

	public void save(YmlConfig config) {
		config.set("name", displayName);
		config.set("path", executablePath);
		config.set("version", version);
		config.set("default", String.valueOf(defaultInstallation));
		config.set("auto", String.valueOf(autoDetected));
		config.save();
	}

	public static JavaInstallation fromConfig(String id, YmlConfig config) {
		return new JavaInstallation(id, config.getString("name", "Unknown Java"), config.getString("path", ""),
				config.getString("version", ""), "true".equalsIgnoreCase(config.getString("default", "false")),
				"true".equalsIgnoreCase(config.getString("auto", "false")), false);
	}

	@Override
	public String toString() {
		return displayName;
	}

}
