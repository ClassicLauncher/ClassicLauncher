package net.classiclauncher.launcher;

/**
 * Provides the launcher's runtime version, read from the shaded JAR manifest.
 *
 * <p>
 * {@link #VERSION} is populated from the {@code Implementation-Version} manifest attribute written by the Shade plugin.
 * When running inside an IDE (no JAR manifest), it falls back to {@code "0.0.0"}.
 *
 * <p>
 * Use {@link #isAtLeast(String)} to gate features or extensions on a minimum version.
 */
public final class LauncherVersion {

	/**
	 * The running launcher version string (e.g. {@code "1.2.3"} or {@code "1.0-SNAPSHOT"}). Falls back to
	 * {@code "0.0.0"} when no manifest is present (IDE / unit-test execution).
	 */
	public static final String VERSION;

	static {
		String v = LauncherVersion.class.getPackage().getImplementationVersion();
		VERSION = (v != null && !v.isEmpty()) ? v : "0.0.0";
	}

	private LauncherVersion() {
	}

	/**
	 * Returns {@code true} if the running launcher version is greater than or equal to {@code minVersion} using
	 * standard major.minor.patch comparison.
	 *
	 * <p>
	 * Non-numeric suffixes (e.g. {@code "-SNAPSHOT"}) are stripped before comparison.
	 *
	 * @param minVersion
	 *            minimum required version string (e.g. {@code "1.0.0"})
	 * @return {@code true} if {@link #VERSION} satisfies the minimum requirement
	 */
	public static boolean isAtLeast(String minVersion) {
		if (minVersion == null || minVersion.isEmpty()) return true;
		return compareVersions(VERSION, minVersion) >= 0;
	}

	/**
	 * Compares two semver-style version strings.
	 *
	 * @return negative if {@code a < b}, zero if equal, positive if {@code a > b}
	 */
	private static int compareVersions(String a, String b) {
		String[] partsA = a.split("\\.");
		String[] partsB = b.split("\\.");
		int len = Math.max(partsA.length, partsB.length);
		for (int i = 0; i < len; i++) {
			int numA = i < partsA.length ? parseVersionPart(partsA[i]) : 0;
			int numB = i < partsB.length ? parseVersionPart(partsB[i]) : 0;
			if (numA != numB) return numA - numB;
		}
		return 0;
	}

	/**
	 * Extracts the leading integer from a version component, ignoring suffixes like {@code "-SNAPSHOT"} or
	 * {@code "-RC1"}.
	 */
	private static int parseVersionPart(String part) {
		try {
			return Integer.parseInt(part.replaceAll("[^0-9]", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

}
