package net.classiclauncher.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides the launcher's runtime version, resolved from (in priority order):
 *
 * <ol>
 * <li>{@code launcher-version.properties} on the classpath — a Maven-filtered resource written at build time with the
 * literal {@code project.version}. Present in both the shaded JAR and {@code target/classes}, so it works correctly
 * when running from an IDE without a manifest.
 * <li>The {@code Implementation-Version} attribute in the JAR manifest (written by the Shade plugin). Kept as a
 * secondary source for backwards compatibility.
 * <li>Hard-coded fallback {@code "0.0.0"} — only reached if neither source is available (e.g. running an unbuilt
 * project directly from source).
 * </ol>
 *
 * <p>
 * Use {@link #isAtLeast(String)} to gate features or extensions on a minimum version.
 */
public final class LauncherVersion {

	/**
	 * The running launcher version string (e.g. {@code "1.2.3"} or {@code "1.0-SNAPSHOT"}). Falls back to
	 * {@code "0.0.0"} when no version source is available.
	 */
	public static final String VERSION;

	static {
		VERSION = resolveVersion();
	}

	private LauncherVersion() {
	}

	private static String resolveVersion() {
		// 1. Maven-filtered properties file — works in IDE (target/classes) and in the shaded JAR.
		try (InputStream in = LauncherVersion.class.getClassLoader()
				.getResourceAsStream("launcher-version.properties")) {
			if (in != null) {
				Properties props = new Properties();
				props.load(in);
				String v = props.getProperty("version");
				// If Maven filtering did not run, the value is the raw placeholder — ignore it.
				if (v != null && !v.isEmpty() && !v.startsWith("${")) {
					return v;
				}
			}
		} catch (IOException ignored) {
		}

		// 2. JAR manifest Implementation-Version (written by the Shade plugin).
		String manifest = LauncherVersion.class.getPackage().getImplementationVersion();
		if (manifest != null && !manifest.isEmpty()) {
			return manifest;
		}

		// 3. Safe fallback.
		return "0.0.0";
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
