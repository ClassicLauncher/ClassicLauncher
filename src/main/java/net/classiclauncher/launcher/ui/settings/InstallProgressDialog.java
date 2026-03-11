package net.classiclauncher.launcher.ui.settings;

import java.awt.*;

import javax.swing.*;

import net.classiclauncher.launcher.extension.InstallProgressListener;

/**
 * Modal progress dialog displayed while an extension (and its dependency chain) is being installed.
 *
 * <p>
 * The dialog shows:
 * <ul>
 * <li>A scrollable log that captures each installation step and download event.</li>
 * <li>A progress bar for the current file download (determinate when the server supplies a {@code Content-Length}
 * header, indeterminate otherwise).</li>
 * <li>A bytes label showing how much of the current file has been downloaded.</li>
 * <li>An OK button that is disabled until the installation completes or fails.</li>
 * </ul>
 *
 * <p>
 * Typical usage:
 *
 * <pre>{@code
 * InstallProgressDialog dialog = new InstallProgressDialog(parentComponent);
 * InstallProgressListener listener = dialog.getListener();
 *
 * new SwingWorker<Void, Void>() {
 *
 * 	protected Void doInBackground() throws Exception {
 * 		for (Extensions.ResolvedDependency dep : plan) {
 * 			extensions.installManifest(dep.manifest, listener);
 * 		}
 * 		return null;
 * 	}
 *
 * 	protected void done() {
 * 		try {
 * 			get();
 * 			dialog.markSuccess();
 * 		} catch (Exception ex) {
 * 			dialog.markFailed(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
 * 		}
 * 	}
 *
 * }.execute();
 *
 * dialog.setVisible(true); // blocks EDT until the user clicks OK
 * }</pre>
 */
public class InstallProgressDialog extends JDialog {

	private static final int PROGRESS_THROTTLE_MS = 60;

	// ── UI components ─────────────────────────────────────────────────────────

	private final JLabel statusLabel;
	private final JTextArea logArea;
	private final JProgressBar progressBar;
	private final JLabel bytesLabel;
	private final JButton okButton;

	// ── State ─────────────────────────────────────────────────────────────────

	private boolean successful = false;
	private volatile long lastProgressUpdate = 0;

	// ── Constructor ───────────────────────────────────────────────────────────

	public InstallProgressDialog(Component parent) {
		super(SwingUtilities.getWindowAncestor(parent), "Installing Extension", Dialog.ModalityType.APPLICATION_MODAL);

		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setResizable(false);

		// ── Status label ─────────────────────────────────────────────────────
		statusLabel = new JLabel("Installing…");
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));

		// ── Log area ─────────────────────────────────────────────────────────
		logArea = new JTextArea(10, 44);
		logArea.setEditable(false);
		logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		logArea.setMargin(new Insets(4, 6, 4, 6));
		logArea.setBackground(new Color(0xF8F8F8));
		JScrollPane logScroll = new JScrollPane(logArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		logScroll.setBorder(BorderFactory.createLineBorder(new Color(0xCCCCCC), 1));

		// ── Progress bar ─────────────────────────────────────────────────────
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(false);
		progressBar.setIndeterminate(true);

		// ── Bytes label ───────────────────────────────────────────────────────
		bytesLabel = new JLabel(" ");
		bytesLabel.setFont(bytesLabel.getFont().deriveFont(Font.PLAIN, 10f));
		bytesLabel.setForeground(new Color(0x555555));
		bytesLabel.setHorizontalAlignment(SwingConstants.CENTER);

		// ── OK button ─────────────────────────────────────────────────────────
		okButton = new JButton("OK");
		okButton.setEnabled(false);
		okButton.addActionListener(e -> dispose());

		// ── Layout ────────────────────────────────────────────────────────────
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(statusLabel);
		content.add(Box.createVerticalStrut(8));

		logScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(logScroll);
		content.add(Box.createVerticalStrut(8));

		JPanel downloadPanel = new JPanel();
		downloadPanel.setLayout(new BoxLayout(downloadPanel, BoxLayout.Y_AXIS));
		downloadPanel.setOpaque(false);
		downloadPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

		progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, progressBar.getPreferredSize().height));
		downloadPanel.add(progressBar);
		downloadPanel.add(Box.createVerticalStrut(2));

		bytesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		downloadPanel.add(bytesLabel);
		content.add(downloadPanel);

		content.add(Box.createVerticalStrut(12));

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		buttonRow.setOpaque(false);
		buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttonRow.add(okButton);
		content.add(buttonRow);

		setContentPane(content);
		pack();
		setLocationRelativeTo(parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent));
	}

	// ── Public API ────────────────────────────────────────────────────────────

	/**
	 * Returns an {@link InstallProgressListener} whose callbacks update this dialog's UI. All callbacks dispatch to the
	 * EDT via {@code SwingUtilities.invokeLater}. Download progress updates are throttled to at most one UI refresh per
	 * {@value PROGRESS_THROTTLE_MS} ms to prevent EDT flooding.
	 */
	public InstallProgressListener getListener() {
		return new InstallProgressListener() {

			@Override
			public void onStep(String description) {
				SwingUtilities.invokeLater(() -> {
					logArea.append(description + "\n");
					scrollLogToBottom();
					statusLabel.setText(description);
					progressBar.setIndeterminate(true);
					bytesLabel.setText(" ");
				});
			}

			@Override
			public void onDownloadStarted(String label, long totalBytes) {
				SwingUtilities.invokeLater(() -> {
					String msg = "⬇  " + label + (totalBytes > 0 ? " (" + formatBytes(totalBytes) + ")" : "");
					logArea.append(msg + "\n");
					scrollLogToBottom();
					statusLabel.setText("Downloading " + label + "…");
					if (totalBytes > 0) {
						progressBar.setIndeterminate(false);
						progressBar.setValue(0);
						progressBar.setMaximum(100);
						bytesLabel.setText("0 B / " + formatBytes(totalBytes));
					} else {
						progressBar.setIndeterminate(true);
						bytesLabel.setText(label);
					}
				});
			}

			@Override
			public void onDownloadProgress(String label, long bytesDownloaded, long totalBytes) {
				long now = System.currentTimeMillis();
				if (now - lastProgressUpdate < PROGRESS_THROTTLE_MS) return;
				lastProgressUpdate = now;
				SwingUtilities.invokeLater(() -> {
					if (totalBytes > 0) {
						int pct = (int) Math.min(100L, bytesDownloaded * 100L / totalBytes);
						progressBar.setValue(pct);
						bytesLabel.setText(formatBytes(bytesDownloaded) + " / " + formatBytes(totalBytes));
					}
				});
			}

			@Override
			public void onDownloadCompleted(String label) {
				SwingUtilities.invokeLater(() -> {
					logArea.append("   ✓ " + label + " downloaded\n");
					scrollLogToBottom();
					progressBar.setIndeterminate(false);
					progressBar.setValue(100);
					bytesLabel.setText(" ");
				});
			}

			@Override
			public void onDownloadSkipped(String label) {
				SwingUtilities.invokeLater(() -> {
					logArea.append("   ✓ " + label + " (cached)\n");
					scrollLogToBottom();
				});
			}

		};
	}

	/**
	 * Returns {@code true} if the installation completed successfully (i.e. {@link #markSuccess()} was called). Can be
	 * checked after the dialog has been dismissed to decide whether to show a "Restart Required" notice.
	 */
	public boolean isSuccessful() {
		return successful;
	}

	/**
	 * Marks the installation as completed successfully. Must be called on the EDT (e.g. from a
	 * {@code SwingWorker.done()} method).
	 */
	public void markSuccess() {
		successful = true;
		statusLabel.setText("Installation complete.");
		statusLabel.setForeground(new Color(0x2E7D32));
		progressBar.setIndeterminate(false);
		progressBar.setValue(100);
		bytesLabel.setText(" ");
		logArea.append("✓ Installation complete.\n");
		scrollLogToBottom();
		okButton.setEnabled(true);
		okButton.requestFocusInWindow();
	}

	/**
	 * Marks the installation as failed. Must be called on the EDT (e.g. from a {@code SwingWorker.done()} method).
	 *
	 * @param errorMessage
	 *            human-readable error description
	 */
	public void markFailed(String errorMessage) {
		statusLabel.setText("Installation failed.");
		statusLabel.setForeground(new Color(0xC62828));
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
		bytesLabel.setText(" ");
		logArea.append("✗ Error: " + (errorMessage != null ? errorMessage : "Unknown error") + "\n");
		scrollLogToBottom();
		okButton.setEnabled(true);
		okButton.requestFocusInWindow();
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private void scrollLogToBottom() {
		logArea.setCaretPosition(logArea.getDocument().getLength());
	}

	/**
	 * Formats a byte count into a human-readable string (B, KB, MB, GB).
	 */
	private static String formatBytes(long bytes) {
		if (bytes < 1024L) return bytes + " B";
		if (bytes < 1024L * 1024L) return String.format("%.1f KB", bytes / 1024.0);
		if (bytes < 1024L * 1024L * 1024L) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
		return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
	}

}
