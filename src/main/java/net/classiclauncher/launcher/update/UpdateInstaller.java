package net.classiclauncher.launcher.update;

import java.awt.Component;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

import javax.swing.JOptionPane;

import net.classiclauncher.launcher.LauncherVersion;
import net.classiclauncher.launcher.platform.Platform;

/**
 * Downloads a release asset and triggers the platform-specific installation.
 *
 * <p>
 * Per-platform install flow:
 * <ul>
 * <li><b>JAR</b> — downloads the new JAR to a temp file, writes a replacement shell/bat script, spawns it, and calls
 * {@link System#exit(0)}. The script waits for the JVM to fully exit, copies the new JAR over the old one, and
 * re-launches the launcher.</li>
 * <li><b>macOS INSTALLER</b> — downloads the DMG, calls {@code open <file>.dmg}, and exits.</li>
 * <li><b>Windows INSTALLER</b> — downloads the MSI, calls {@code msiexec /i <file>.msi}, and exits.</li>
 * <li><b>Linux INSTALLER</b> — downloads the package, shows a {@link JOptionPane} with the {@code dpkg -i} /
 * {@code rpm -i} command the user must run, then exits.</li>
 * </ul>
 *
 * <p>
 * All downloads report progress via {@link DownloadListener}.
 */
public class UpdateInstaller {

	private static final int CONNECT_TIMEOUT_MS = 5_000;
	private static final int READ_TIMEOUT_MS = 60_000; // large files may be slow
	private static final int MAX_REDIRECTS = 5;
	private static final int BUFFER_SIZE = 8192;

	/**
	 * Callback interface for download progress reporting.
	 */
	public interface DownloadListener {

		void onDownloadStarted(String fileName, long totalBytes);

		void onDownloadProgress(long bytesDownloaded, long totalBytes);

		void onDownloadCompleted(String fileName);

	}

	/**
	 * Downloads the given asset and installs it on the current platform.
	 *
	 * <p>
	 * This method blocks until the download is complete. Installation steps that are fire-and-forget (spawning a
	 * process) happen synchronously here; the JVM may exit at the end of this call.
	 *
	 * @param asset
	 *            the asset to download
	 * @param platform
	 *            the host OS
	 * @param mode
	 *            JAR or INSTALLER distribution
	 * @param listener
	 *            progress callbacks; must not be {@code null}
	 * @param uiParent
	 *            Swing parent component used for any error dialogs
	 * @throws IOException
	 *             on download failure
	 */
	public void install(AssetInfo asset, Platform platform, DistributionMode mode, DownloadListener listener,
			Component uiParent) throws IOException {
		File downloaded = download(asset, listener);
		try {
			applyInstall(downloaded, asset.getName(), platform, mode, uiParent);
		} catch (IOException e) {
			downloaded.delete();
			throw e;
		}
	}

	// ── Download ──────────────────────────────────────────────────────────────

	private File download(AssetInfo asset, DownloadListener listener) throws IOException {
		String urlString = asset.getDownloadUrl();
		if (urlString == null || urlString.isEmpty()) {
			throw new IOException("Asset download URL is empty");
		}

		URL url = new URL(urlString);
		validateScheme(url.getProtocol());

		int redirectCount = 0;
		HttpURLConnection conn = null;

		while (true) {
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestProperty("User-Agent", "ClassicLauncher/" + LauncherVersion.VERSION);

			int status = conn.getResponseCode();
			if (status >= 300 && status < 400) {
				conn.disconnect();
				if (redirectCount >= MAX_REDIRECTS) {
					throw new IOException("Too many redirects following download URL");
				}
				String location = conn.getHeaderField("Location");
				if (location == null || location.isEmpty()) {
					throw new IOException("Redirect with no Location header");
				}
				url = new URL(location);
				validateScheme(url.getProtocol());
				redirectCount++;
				continue;
			}

			if (status < 200 || status >= 300) {
				conn.disconnect();
				throw new IOException("Download failed with HTTP " + status);
			}
			break;
		}

		long totalBytes = conn.getContentLengthLong();
		String fileName = asset.getName();
		listener.onDownloadStarted(fileName, totalBytes);

		File tempFile = File.createTempFile("classiclauncher-update-", "-" + fileName);
		tempFile.deleteOnExit();

		try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(tempFile)) {
			byte[] buf = new byte[BUFFER_SIZE];
			long downloaded = 0;
			int read;
			while ((read = in.read(buf)) != -1) {
				out.write(buf, 0, read);
				downloaded += read;
				listener.onDownloadProgress(downloaded, totalBytes);
			}
		} finally {
			conn.disconnect();
		}

		listener.onDownloadCompleted(fileName);
		return tempFile;
	}

	// ── Installation ──────────────────────────────────────────────────────────

	private void applyInstall(File downloaded, String assetName, Platform platform, DistributionMode mode,
			Component uiParent) throws IOException {
		if (mode == DistributionMode.JAR) {
			installJar(downloaded);
		} else {
			switch (platform) {
				case MACOS :
					installMacOsDmg(downloaded);
					break;
				case WINDOWS :
					installWindowsMsi(downloaded);
					break;
				case LINUX :
					installLinuxPackage(downloaded, assetName, uiParent);
					break;
				default :
					// Unknown platform in INSTALLER mode — fall back to JAR logic
					installJar(downloaded);
			}
		}
	}

	private void installJar(File newJar) throws IOException {
		File currentJar = resolveCurrentJar();
		if (currentJar == null) {
			throw new IOException("Cannot determine current JAR location. Please replace the JAR manually: "
					+ newJar.getAbsolutePath());
		}

		boolean isWindows = Platform.current() == Platform.WINDOWS;
		File script = writeReplacementScript(newJar, currentJar, isWindows);

		ProcessBuilder pb;
		if (isWindows) {
			pb = new ProcessBuilder("cmd", "/c", script.getAbsolutePath());
		} else {
			pb = new ProcessBuilder("/bin/sh", script.getAbsolutePath());
		}
		pb.inheritIO();
		pb.start();

		System.exit(0);
	}

	private File writeReplacementScript(File newJar, File currentJar, boolean windows) throws IOException {
		String currentJarPath = currentJar.getAbsolutePath();
		String newJarPath = newJar.getAbsolutePath();
		String java = resolveJavaExecutable(windows);

		if (windows) {
			File script = File.createTempFile("cl-update-", ".bat");
			script.deleteOnExit();
			String content = "@echo off\r\n" + "timeout /t 2 /nobreak >nul\r\n" + "copy /y \""
					+ newJarPath.replace("\"", "\"\"") + "\" \"" + currentJarPath.replace("\"", "\"\"") + "\"\r\n"
					+ "start \"\" \"" + java.replace("\"", "\"\"") + "\" -jar \"" + currentJarPath.replace("\"", "\"\"")
					+ "\"\r\n";
			writeTextFile(script, content);
			return script;
		} else {
			File script = File.createTempFile("cl-update-", ".sh");
			script.deleteOnExit();
			String content = "#!/bin/sh\n" + "sleep 2\n" + "cp -f '" + newJarPath.replace("'", "'\\''") + "' '"
					+ currentJarPath.replace("'", "'\\''") + "'\n" + "'" + java.replace("'", "'\\''") + "' -jar '"
					+ currentJarPath.replace("'", "'\\''") + "' &\n";
			writeTextFile(script, content);
			makePosixExecutable(script);
			return script;
		}
	}

	private void installMacOsDmg(File dmg) throws IOException {
		new ProcessBuilder("open", dmg.getAbsolutePath()).start();
		System.exit(0);
	}

	private void installWindowsMsi(File msi) throws IOException {
		new ProcessBuilder("msiexec", "/i", msi.getAbsolutePath()).start();
		System.exit(0);
	}

	private void installLinuxPackage(File pkg, String assetName, Component uiParent) throws IOException {
		String command;
		if (assetName.endsWith(".deb")) {
			command = "sudo dpkg -i \"" + pkg.getAbsolutePath() + "\"";
		} else if (assetName.endsWith(".rpm")) {
			command = "sudo rpm -i \"" + pkg.getAbsolutePath() + "\"";
		} else {
			command = "# Unsupported package format. File saved to: " + pkg.getAbsolutePath();
		}

		JOptionPane.showMessageDialog(uiParent,
				"The update has been downloaded. Run the following command in a terminal to install it:\n\n" + command
						+ "\n\nFile location: " + pkg.getAbsolutePath(),
				"Install Update", JOptionPane.INFORMATION_MESSAGE);
		System.exit(0);
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private static File resolveCurrentJar() {
		try {
			java.security.CodeSource cs = UpdateInstaller.class.getProtectionDomain().getCodeSource();
			if (cs != null && cs.getLocation() != null) {
				File f = new File(cs.getLocation().toURI());
				if (f.isFile() && f.getName().endsWith(".jar")) {
					return f;
				}
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static String resolveJavaExecutable(boolean windows) {
		String javaHome = System.getProperty("java.home", "");
		if (!javaHome.isEmpty()) {
			String exe = javaHome + File.separator + "bin" + File.separator + (windows ? "java.exe" : "java");
			if (new File(exe).isFile()) return exe;
		}
		return windows ? "java.exe" : "java";
	}

	private static void writeTextFile(File file, String content) throws IOException {
		try (FileOutputStream out = new FileOutputStream(file)) {
			out.write(content.getBytes(StandardCharsets.UTF_8));
		}
	}

	private static void makePosixExecutable(File file) {
		try {
			Set<PosixFilePermission> perms = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
					PosixFilePermission.OWNER_EXECUTE, PosixFilePermission.GROUP_READ,
					PosixFilePermission.GROUP_EXECUTE, PosixFilePermission.OTHERS_READ,
					PosixFilePermission.OTHERS_EXECUTE);
			Files.setPosixFilePermissions(file.toPath(), perms);
		} catch (Exception ignored) {
			// If POSIX permissions are unsupported (e.g. Windows) or the call fails,
			// the script may still be executable if the OS allows it
		}
	}

	private static void validateScheme(String scheme) throws IOException {
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			throw new IOException("Only http and https schemes are allowed for downloads, got: " + scheme);
		}
	}

	// Kept to avoid needing ByteArrayOutputStream in download, but we stream to file directly — unused here.
	@SuppressWarnings("unused")
	private static byte[] readFully(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[BUFFER_SIZE];
		int read;
		while ((read = in.read(buf)) != -1)
			baos.write(buf, 0, read);
		return baos.toByteArray();
	}

}
