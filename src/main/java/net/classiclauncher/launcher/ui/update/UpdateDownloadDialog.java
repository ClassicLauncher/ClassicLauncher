package net.classiclauncher.launcher.ui.update;

import java.awt.*;

import javax.swing.*;

import net.classiclauncher.launcher.update.install.UpdateInstaller;

/**
 * Modal progress dialog displayed while a release asset is being downloaded.
 *
 * <p>
 * Follows the {@link net.classiclauncher.launcher.ui.settings.InstallProgressDialog} pattern:
 * <ul>
 * <li>Status label updated on each step.</li>
 * <li>Log area capturing download events.</li>
 * <li>Determinate progress bar (switches to indeterminate when {@code Content-Length} is unknown).</li>
 * <li>Bytes label showing downloaded / total.</li>
 * <li>OK button enabled only when done or failed.</li>
 * </ul>
 */
public class UpdateDownloadDialog extends JDialog {

	private static final int PROGRESS_THROTTLE_MS = 60;

	private final JLabel statusLabel;
	private final JTextArea logArea;
	private final JProgressBar progressBar;
	private final JLabel bytesLabel;
	private final JButton okButton;

	private volatile long lastProgressUpdate = 0;
	private boolean successful = false;

	public UpdateDownloadDialog(Window owner, String assetName) {
		super(owner, "Downloading Update", Dialog.ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		setResizable(false);

		// ── Status label ───────────────────────────────────────────────────────
		statusLabel = new JLabel("Preparing download…");
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));

		// ── Log area ───────────────────────────────────────────────────────────
		logArea = new JTextArea(8, 44);
		logArea.setEditable(false);
		logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
		logArea.setMargin(new Insets(4, 6, 4, 6));
		logArea.setBackground(new Color(0xF8F8F8));
		JScrollPane logScroll = new JScrollPane(logArea, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		logScroll.setBorder(BorderFactory.createLineBorder(new Color(0xCCCCCC), 1));

		// ── Progress bar ───────────────────────────────────────────────────────
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(false);
		progressBar.setIndeterminate(true);

		// ── Bytes label ────────────────────────────────────────────────────────
		bytesLabel = new JLabel(" ");
		bytesLabel.setFont(bytesLabel.getFont().deriveFont(Font.PLAIN, 10f));
		bytesLabel.setForeground(new Color(0x555555));
		bytesLabel.setHorizontalAlignment(SwingConstants.CENTER);

		// ── OK button ──────────────────────────────────────────────────────────
		okButton = new JButton("OK");
		okButton.setEnabled(false);
		okButton.addActionListener(e -> dispose());

		// ── Layout ─────────────────────────────────────────────────────────────
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
		setLocationRelativeTo(owner);
	}

	/**
	 * Returns a {@link UpdateInstaller.DownloadListener} whose callbacks update this dialog's UI. Progress updates are
	 * throttled to at most one UI refresh per {@value PROGRESS_THROTTLE_MS} ms to prevent EDT flooding.
	 */
	public UpdateInstaller.DownloadListener getListener() {
		return new UpdateInstaller.DownloadListener() {

			@Override
			public void onDownloadStarted(String fileName, long totalBytes) {
				SwingUtilities.invokeLater(() -> {
					String msg = "⬇  " + fileName + (totalBytes > 0 ? " (" + formatBytes(totalBytes) + ")" : "");
					logArea.append(msg + "\n");
					scrollToBottom();
					statusLabel.setText("Downloading " + fileName + "…");
					if (totalBytes > 0) {
						progressBar.setIndeterminate(false);
						progressBar.setValue(0);
						progressBar.setMaximum(100);
						bytesLabel.setText("0 B / " + formatBytes(totalBytes));
					} else {
						progressBar.setIndeterminate(true);
						bytesLabel.setText(fileName);
					}
				});
			}

			@Override
			public void onDownloadProgress(long bytesDownloaded, long totalBytes) {
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
			public void onDownloadCompleted(String fileName) {
				SwingUtilities.invokeLater(() -> {
					logArea.append("   ✓ " + fileName + " downloaded\n");
					scrollToBottom();
					progressBar.setIndeterminate(false);
					progressBar.setValue(100);
					bytesLabel.setText(" ");
				});
			}

		};
	}

	/** Marks the download as successful and enables the OK button. Must be called on the EDT. */
	public void markSuccess() {
		successful = true;
		statusLabel.setText("Download complete. Installing…");
		statusLabel.setForeground(new Color(0x2E7D32));
		progressBar.setIndeterminate(false);
		progressBar.setValue(100);
		bytesLabel.setText(" ");
		logArea.append("✓ Download complete.\n");
		scrollToBottom();
		okButton.setEnabled(true);
		okButton.requestFocusInWindow();
	}

	/** Marks the download as failed and enables the OK button. Must be called on the EDT. */
	public void markFailed(String errorMessage) {
		statusLabel.setText("Download failed.");
		statusLabel.setForeground(new Color(0xC62828));
		progressBar.setIndeterminate(false);
		progressBar.setValue(0);
		bytesLabel.setText(" ");
		logArea.append("✗ Error: " + (errorMessage != null ? errorMessage : "Unknown error") + "\n");
		scrollToBottom();
		okButton.setEnabled(true);
		okButton.requestFocusInWindow();
	}

	/** Returns {@code true} if the download completed successfully. */
	public boolean isSuccessful() {
		return successful;
	}

	private void scrollToBottom() {
		logArea.setCaretPosition(logArea.getDocument().getLength());
	}

	private static String formatBytes(long bytes) {
		if (bytes < 1024L) return bytes + " B";
		if (bytes < 1024L * 1024L) return String.format("%.1f KB", bytes / 1024.0);
		if (bytes < 1024L * 1024L * 1024L) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
		return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
	}

}
