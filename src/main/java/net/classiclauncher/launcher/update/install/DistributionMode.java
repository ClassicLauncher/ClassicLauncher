package net.classiclauncher.launcher.update.install;

/**
 * Whether the launcher is running as a plain executable JAR or as a native installer package (jpackage).
 *
 * <p>
 * {@link #current()} is the single canonical location for the {@code jpackage.app-path} detection. All code that needs
 * to branch on distribution type (e.g. {@link ArtifactSelector}, {@code JavaManager}) must call this method rather than
 * re-checking the system property inline.
 */
public enum DistributionMode {

	/** Running as a plain {@code .jar} file invoked directly with {@code java -jar}. */
	JAR,

	/** Running as a native installer package produced by jpackage (DMG, MSI, DEB, RPM, etc.). */
	INSTALLER;

	private static final DistributionMode CURRENT = detect();

	/** Returns the distribution mode of the currently running launcher. */
	public static DistributionMode current() {
		return CURRENT;
	}

	private static DistributionMode detect() {
		return System.getProperty("jpackage.app-path") != null ? INSTALLER : JAR;
	}

}
