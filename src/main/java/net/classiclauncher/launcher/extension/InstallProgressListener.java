package net.classiclauncher.launcher.extension;

/**
 * Callback interface for tracking extension installation progress.
 *
 * <p>
 * Implementations are called from a background (non-EDT) thread. Any UI updates triggered by these callbacks must be
 * dispatched via {@code SwingUtilities.invokeLater}.
 *
 * <p>
 * A no-op implementation is available via {@link #NOOP} for callers that do not need progress tracking.
 */
public interface InstallProgressListener {

	/**
	 * Called when a new installation step begins.
	 *
	 * @param description
	 *            human-readable description of the current step (e.g. {@code "Downloading my-ext-1.2.3.jar..."})
	 */
	void onStep(String description);

	/**
	 * Called when a file download is about to begin.
	 *
	 * @param label
	 *            human-readable artifact label (e.g. {@code "my-ext-1.2.3.jar"})
	 * @param totalBytes
	 *            total file size in bytes, or {@code -1} if unknown
	 */
	void onDownloadStarted(String label, long totalBytes);

	/**
	 * Called periodically during a file download.
	 *
	 * @param label
	 *            artifact label
	 * @param bytesDownloaded
	 *            bytes written to disk so far
	 * @param totalBytes
	 *            total file size in bytes, or {@code -1} if unknown
	 */
	void onDownloadProgress(String label, long bytesDownloaded, long totalBytes);

	/**
	 * Called when a file download completes successfully.
	 *
	 * @param label
	 *            artifact label
	 */
	void onDownloadCompleted(String label);

	/**
	 * Called when a file was already present in the local cache and the download was skipped.
	 *
	 * @param label
	 *            artifact label
	 */
	void onDownloadSkipped(String label);

	/**
	 * No-op implementation used when progress tracking is not required.
	 */
	InstallProgressListener NOOP = new InstallProgressListener() {

		@Override
		public void onStep(String description) {
		}

		@Override
		public void onDownloadStarted(String label, long totalBytes) {
		}

		@Override
		public void onDownloadProgress(String label, long downloaded, long totalBytes) {
		}

		@Override
		public void onDownloadCompleted(String label) {
		}

		@Override
		public void onDownloadSkipped(String label) {
		}

	};

}
