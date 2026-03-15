package net.classiclauncher.launcher.update;

/**
 * Indicates how the launcher was distributed — as a plain JAR or as a native installer package produced by jpackage.
 *
 * <p>
 * Use {@link #current()} to determine the mode of the running launcher instance. Detection is performed once at
 * class-load time via the {@code jpackage.app-path} system property, which jpackage sets automatically in bundled
 * applications.
 *
 * <p>
 * This is the single canonical location for the jpackage detection check — both {@link ArtifactSelector} and
 * {@code JavaManager} delegate here rather than re-checking the raw system property.
 */
public enum DistributionMode {

	/** Running from a plain shaded JAR (e.g. from an IDE, or downloaded directly). */
	JAR,

	/** Running from a jpackage-produced native installer (DMG, MSI, DEB, RPM, or app-image). */
	INSTALLER;

	private static final DistributionMode CURRENT = detect();

	/**
	 * Returns the distribution mode for the running launcher instance.
	 */
	public static DistributionMode current() {
		return CURRENT;
	}

	private static DistributionMode detect() {
		return System.getProperty("jpackage.app-path") != null ? INSTALLER : JAR;
	}

}
