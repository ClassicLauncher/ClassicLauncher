package net.classiclauncher.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides the launcher's runtime version and GitHub repository, resolved from {@code launcher-version.properties} on
 * the classpath (a Maven-filtered resource written at build time).
 *
 * <ol>
 * <li>{@code launcher-version.properties} on the classpath — present in both the shaded JAR and {@code target/classes},
 * so it works correctly when running from an IDE without a manifest.
 * <li>The {@code Implementation-Version} attribute in the JAR manifest (written by the Shade plugin). Kept as a
 * secondary source for {@link #VERSION} for backwards compatibility.
 * <li>Hard-coded fallback {@code "0.0.0"} — only reached if neither source is available.
 * </ol>
 *
 * <p>
 * Use {@link #isAtLeast(String)} to gate features or extensions on a minimum version. Use
 * {@link #compare(String, String)} for general semver comparisons (e.g. in the update checker).
 */
public final class LauncherVersion {

	/**
	 * The running launcher version string (e.g. {@code "1.2.3"} or {@code "1.0-SNAPSHOT"}). Falls back to
	 * {@code "0.0.0"} when no version source is available.
	 */
	public static final String VERSION;

	/**
	 * The GitHub repository in {@code owner/name} form (e.g. {@code "ClassicLauncher/ClassicLauncher"}). Used by the
	 * auto-update system to fetch release information. Empty string when not configured.
	 */
	public static final String GITHUB_REPO;

	static {
		Properties props = loadProperties();
		VERSION = resolveVersion(props);
		GITHUB_REPO = resolveGithubRepo(props);
	}

	private LauncherVersion() {
	}

	private static Properties loadProperties() {
		try (InputStream in = LauncherVersion.class.getClassLoader()
				.getResourceAsStream("launcher-version.properties")) {
			if (in != null) {
				Properties props = new Properties();
				props.load(in);
				return props;
			}
		} catch (IOException ignored) {
		}
		return new Properties();
	}

	private static String resolveVersion(Properties props) {
		// 1. Maven-filtered properties file — works in IDE (target/classes) and in the shaded JAR.
		String v = props.getProperty("version");
		if (v != null && !v.isEmpty() && !v.startsWith("${")) {
			return v;
		}

		// 2. JAR manifest Implementation-Version (written by the Shade plugin).
		String manifest = LauncherVersion.class.getPackage().getImplementationVersion();
		if (manifest != null && !manifest.isEmpty()) {
			return manifest;
		}

		// 3. Safe fallback.
		return "0.0.0";
	}

	private static String resolveGithubRepo(Properties props) {
		String repo = props.getProperty("github.repo");
		if (repo != null && !repo.isEmpty() && !repo.startsWith("${")) {
			return repo;
		}
		return "";
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
		return compare(VERSION, minVersion) >= 0;
	}

	/**
	 * Compares two semver-style version strings.
	 *
	 * <p>
	 * Non-numeric suffixes (e.g. {@code "-SNAPSHOT"}) are stripped before comparison. Used by the update checker to
	 * determine whether a release is newer than the installed version.
	 *
	 * @param a
	 *            first version string
	 * @param b
	 *            second version string
	 * @return negative if {@code a < b}, zero if equal, positive if {@code a > b}
	 */
	public static int compare(String a, String b) {
		String[] partsA = (a != null ? a : "0").split("\\.");
		String[] partsB = (b != null ? b : "0").split("\\.");
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
