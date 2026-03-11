package net.classiclauncher.launcher.launch;

/**
 * Receives progress events and log messages from a {@link LaunchStrategy}.
 *
 * <p>
 * All methods may be called from a background thread; implementations must marshal UI updates to the EDT using
 * {@link javax.swing.SwingUtilities#invokeLater}.
 */
public interface LaunchProgress {

	/**
	 * Appends a human-readable log line to the launcher log.
	 *
	 * @param message
	 *            the log line (no trailing newline required)
	 */
	void log(String message);

	/**
	 * Sets the total number of files to be downloaded / verified. Called once before the first {@link #fileCompleted()}
	 * call so the progress bar can show a determinate percentage.
	 *
	 * @param total
	 *            the total file count; 0 if unknown
	 */
	void setTotalFiles(int total);

	/**
	 * Signals that one file has finished downloading or been verified as already present. Increments the progress bar
	 * by one unit.
	 */
	void fileCompleted();

	/**
	 * Reports mid-download progress for a single file (called every ~8 KB).
	 *
	 * @param fileName
	 *            display name of the file being downloaded
	 * @param bytes
	 *            bytes received so far
	 * @param totalBytes
	 *            total file size, or {@code -1} if the Content-Length is unknown
	 */
	void fileProgress(String fileName, long bytes, long totalBytes);

	/**
	 * Called when the game process exits (or preparation fails before the process starts).
	 *
	 * @param success
	 *            {@code true} if the process exited with code 0; {@code false} otherwise
	 */
	void onLaunchComplete(boolean success);

}
