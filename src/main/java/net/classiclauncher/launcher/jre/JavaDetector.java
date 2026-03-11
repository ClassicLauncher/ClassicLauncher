package net.classiclauncher.launcher.jre;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-platform Java installation detector.
 *
 * <p>
 * Scans common installation directories for the current OS and returns a deduplicated list of found
 * {@link JavaInstallation}s. Detection is done synchronously — callers should invoke this on a background thread.
 *
 * <p>
 * Detection sources per OS:
 * <ul>
 * <li><b>All platforms</b> — {@code System.getProperty("java.home")} (current JVM).</li>
 * <li><b>macOS</b> — {@code /usr/libexec/java_home -V}.</li>
 * <li><b>Linux</b> — scan {@code /usr/lib/jvm/} + {@code which java}.</li>
 * <li><b>Windows</b> — {@code %JAVA_HOME%}, {@code %ProgramFiles%\Java\*}, {@code %ProgramFiles%\Eclipse Adoptium\*},
 * {@code %ProgramFiles%\Microsoft\jdk*}.</li>
 * </ul>
 */
public final class JavaDetector {

	private JavaDetector() {
	}

	/**
	 * Runs detection for the current platform and returns all found installations. Duplicates (same resolved executable
	 * path) are collapsed.
	 */
	public static List<JavaInstallation> detect() {
		Map<String, JavaInstallation> byPath = new LinkedHashMap<>();

		// Always include the JVM that is running the launcher
		addCandidate(byPath, System.getProperty("java.home"), false);

		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("mac")) {
			detectMac(byPath);
		} else if (os.contains("win")) {
			detectWindows(byPath);
		} else {
			detectLinux(byPath);
		}

		return new ArrayList<>(byPath.values());
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Platform-specific detection
	// ─────────────────────────────────────────────────────────────────────────

	private static void detectMac(Map<String, JavaInstallation> byPath) {
		// /usr/libexec/java_home -V lists all JVMs; output goes to stderr
		try {
			Process p = new ProcessBuilder("/usr/libexec/java_home", "-V").redirectErrorStream(true).start();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					// Lines that describe a JVM look like:
					// 21.0.4 (x86_64) "Vendor" - "Name" /path/to/jvm
					String trimmed = line.trim();
					int lastSpace = trimmed.lastIndexOf(' ');
					if (lastSpace < 0) continue;
					String possiblePath = trimmed.substring(lastSpace + 1);
					if (!possiblePath.startsWith("/")) continue;
					addCandidate(byPath, possiblePath, false);
				}
			}
			p.waitFor();
		} catch (Exception ignored) {
			// Command not available — rely on java.home only
		}
	}

	private static void detectLinux(Map<String, JavaInstallation> byPath) {
		// Scan /usr/lib/jvm/ — each subdirectory that contains bin/java is a JVM
		File jvmDir = new File("/usr/lib/jvm");
		if (jvmDir.isDirectory()) {
			File[] entries = jvmDir.listFiles();
			if (entries != null) {
				for (File entry : entries) {
					if (entry.isDirectory()) {
						addCandidate(byPath, entry.getAbsolutePath(), false);
					}
				}
			}
		}

		// Also try `which java`
		try {
			Process p = new ProcessBuilder("which", "java").start();
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				String line = reader.readLine();
				if (line != null && !line.trim().isEmpty()) {
					// Resolve symlinks
					File javaFile = new File(line.trim()).getCanonicalFile();
					// which returns path to the binary; parent is bin/, grandparent is JAVA_HOME
					File binDir = javaFile.getParentFile();
					if (binDir != null && binDir.getName().equals("bin")) {
						addCandidate(byPath, binDir.getParent(), false);
					}
				}
			}
			p.waitFor();
		} catch (Exception ignored) {
		}
	}

	private static void detectWindows(Map<String, JavaInstallation> byPath) {
		// JAVA_HOME environment variable
		String javaHome = System.getenv("JAVA_HOME");
		if (javaHome != null && !javaHome.isEmpty()) {
			addCandidate(byPath, javaHome, false);
		}

		// Common installation directories
		String[] programFilesVars = {"ProgramFiles", "ProgramFiles(x86)"};
		String[] vendorDirs = {"Java", "Eclipse Adoptium", "Microsoft", "BellSoft", "Azul"};

		for (String pfVar : programFilesVars) {
			String pf = System.getenv(pfVar);
			if (pf == null || pf.isEmpty()) continue;
			for (String vendor : vendorDirs) {
				File vendorDir = new File(pf, vendor);
				if (!vendorDir.isDirectory()) continue;
				File[] entries = vendorDir.listFiles();
				if (entries == null) continue;
				for (File entry : entries) {
					if (entry.isDirectory()) {
						addCandidate(byPath, entry.getAbsolutePath(), false);
					}
				}
			}
		}
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Shared helpers
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Resolves the Java executable within a given JAVA_HOME-style directory (or the executable itself), queries its
	 * version, and adds it to {@code byPath} if it is a valid JVM and not already present.
	 */
	private static void addCandidate(Map<String, JavaInstallation> byPath, String javaHomePath, boolean isCurrentJvm) {
		if (javaHomePath == null || javaHomePath.isEmpty()) return;
		try {
			File javaHome = new File(javaHomePath).getCanonicalFile();
			if (!javaHome.exists()) return;

			// Resolve the java binary
			File javaBin = resolveJavaBinary(javaHome);
			if (javaBin == null || !javaBin.isFile()) return;

			String canonicalPath = javaBin.getCanonicalPath();
			if (byPath.containsKey(canonicalPath)) return;

			String version = queryVersion(javaBin.getAbsolutePath());
			String name = buildDisplayName(version, javaHome, isCurrentJvm);

			byPath.put(canonicalPath, JavaInstallation.detected(name, canonicalPath, version));
		} catch (Exception ignored) {
		}
	}

	/**
	 * Finds {@code <home>/bin/java} or {@code <home>/bin/java.exe}.
	 */
	private static File resolveJavaBinary(File home) {
		if (home.isFile()) {
			String name = home.getName();
			if (name.equals("java") || name.equals("java.exe")) return home;
			return null;
		}
		File binDir = new File(home, "bin");
		File java = new File(binDir, "java");
		if (java.isFile()) return java;
		File javaExe = new File(binDir, "java.exe");
		if (javaExe.isFile()) return javaExe;
		return null;
	}

	/**
	 * Runs {@code <javaBinary> -version} and extracts the version string from the first quoted token in the output
	 * (which java writes to stderr in all known implementations).
	 */
	public static String queryVersion(String javaBinaryPath) {
		try {
			Process p = new ProcessBuilder(javaBinaryPath, "-version").redirectErrorStream(true).start();
			String firstLine;
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
				firstLine = reader.readLine();
			}
			p.waitFor();
			if (firstLine == null) return "";
			int q1 = firstLine.indexOf('"');
			int q2 = firstLine.lastIndexOf('"');
			if (q1 >= 0 && q2 > q1) {
				return firstLine.substring(q1 + 1, q2);
			}
		} catch (Exception ignored) {
		}
		return "";
	}

	private static String buildDisplayName(String version, File javaHome, boolean isCurrentJvm) {
		String base = version.isEmpty() ? javaHome.getName() : "Java " + version;
		if (isCurrentJvm) base += " (launcher JVM)";
		return base + " (auto-detected)";
	}

}
