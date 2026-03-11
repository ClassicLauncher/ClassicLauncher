package net.classiclauncher.launcher.profile;

import dev.utano.ymlite.config.YmlConfig;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class Profile {

	private final String id;
	private final String name;

	/**
	 * The version ID to use when launching. Resolved via Api. Null = use latest.
	 */
	private final String versionId;

	/**
	 * The account ID to use. Null = use the account selected in AccountSettings.
	 */
	private final String accountId;

	/**
	 * Custom game directory. Null = use the default data directory.
	 */
	private final String gameDirectory;

	/**
	 * Custom window width in pixels. Null = not set.
	 */
	private final Integer resolutionWidth;

	/**
	 * Custom window height in pixels. Null = not set.
	 */
	private final Integer resolutionHeight;

	/**
	 * Whether to automatically prompt for crash-report assistance. Default: true.
	 */
	private final boolean autoCrashReport;

	/**
	 * Launcher window behaviour while the game is running. Default: CLOSE_LAUNCHER.
	 */
	private final LauncherVisibility launcherVisibility;

	/**
	 * Whether snapshot (development) versions are shown in the version picker. Default: false.
	 */
	private final boolean enableSnapshots;

	/**
	 * Whether old Beta versions (2010-2011) are shown in the version picker. Default: false.
	 */
	private final boolean enableBetaVersions;

	/**
	 * Whether old Alpha versions (2010) are shown in the version picker. Default: false.
	 */
	private final boolean enableAlphaVersions;

	/**
	 * Path to the Java executable to use. Null = use system Java.
	 */
	private final String javaExecutable;

	/**
	 * Extra JVM arguments to pass at launch. Null = none.
	 */
	private final String jvmArguments;

	public void save(YmlConfig config) {
		config.set("name", name);
		config.set("version", versionId != null ? versionId : "");
		config.set("account", accountId != null ? accountId : "");
		config.set("game-directory", gameDirectory != null ? gameDirectory : "");
		config.set("resolution.width", resolutionWidth != null ? resolutionWidth.toString() : "");
		config.set("resolution.height", resolutionHeight != null ? resolutionHeight.toString() : "");
		config.set("auto-crash-report", String.valueOf(autoCrashReport));
		config.set("launcher-visibility",
				launcherVisibility != null ? launcherVisibility.name() : LauncherVisibility.CLOSE_LAUNCHER.name());
		config.set("enable-snapshots", String.valueOf(enableSnapshots));
		config.set("enable-beta-versions", String.valueOf(enableBetaVersions));
		config.set("enable-alpha-versions", String.valueOf(enableAlphaVersions));
		config.set("java-executable", javaExecutable != null ? javaExecutable : "");
		config.set("jvm-arguments", jvmArguments != null ? jvmArguments : "");
		config.save();
	}

	public static Profile fromConfig(String id, YmlConfig config) {
		String rawVersion = config.getString("version", "");
		String rawAccount = config.getString("account", "");
		String rawGameDir = config.getString("game-directory", "");
		String rawWidth = config.getString("resolution.width", "");
		String rawHeight = config.getString("resolution.height", "");
		String rawVisibility = config.getString("launcher-visibility", LauncherVisibility.CLOSE_LAUNCHER.name());
		String rawJavaExe = config.getString("java-executable", "");
		String rawJvmArgs = config.getString("jvm-arguments", "");

		Integer resWidth = null;
		if (!rawWidth.isEmpty()) {
			try {
				resWidth = Integer.parseInt(rawWidth);
			} catch (NumberFormatException ignored) {
			}
		}
		Integer resHeight = null;
		if (!rawHeight.isEmpty()) {
			try {
				resHeight = Integer.parseInt(rawHeight);
			} catch (NumberFormatException ignored) {
			}
		}

		LauncherVisibility visibility;
		try {
			visibility = LauncherVisibility.valueOf(rawVisibility.toUpperCase());
		} catch (IllegalArgumentException e) {
			visibility = LauncherVisibility.CLOSE_LAUNCHER;
		}

		return Profile.builder().id(id).name(config.getString("name", "Default"))
				.versionId(rawVersion.isEmpty() ? null : rawVersion).accountId(rawAccount.isEmpty() ? null : rawAccount)
				.gameDirectory(rawGameDir.isEmpty() ? null : rawGameDir).resolutionWidth(resWidth)
				.resolutionHeight(resHeight)
				.autoCrashReport(!"false".equalsIgnoreCase(config.getString("auto-crash-report", "true")))
				.launcherVisibility(visibility)
				.enableSnapshots("true".equalsIgnoreCase(config.getString("enable-snapshots", "false")))
				.enableBetaVersions("true".equalsIgnoreCase(config.getString("enable-beta-versions", "false")))
				.enableAlphaVersions("true".equalsIgnoreCase(config.getString("enable-alpha-versions", "false")))
				.javaExecutable(rawJavaExe.isEmpty() ? null : rawJavaExe)
				.jvmArguments(rawJvmArgs.isEmpty() ? null : rawJvmArgs).build();
	}

}
