package net.classiclauncher.launcher.ui.update;

import java.awt.*;

import javax.swing.*;

import net.classiclauncher.launcher.LauncherVersion;
import net.classiclauncher.launcher.settings.LauncherSettings;
import net.classiclauncher.launcher.update.ReleaseSource;
import net.classiclauncher.launcher.update.UpdateChecker;
import net.classiclauncher.launcher.update.UpdatePlan;

/**
 * Settings section panel for configuring the auto-update system.
 *
 * <p>
 * Layout (top to bottom):
 * <ol>
 * <li>Checkbox — enable/disable automatic update checks on startup.</li>
 * <li>Skipped version row — label, current skipped version, and a Clear button.</li>
 * <li>Check Now button — immediately below the skipped version row.</li>
 * <li>Status label — result of the last manual check.</li>
 * </ol>
 *
 * <p>
 * Follows the {@link net.classiclauncher.launcher.ui.settings.JavaSettingsPanel} layout pattern.
 */
public class UpdateSettingsPanel extends JPanel {

	private final LauncherSettings settings;
	private final ReleaseSource releaseSource;
	private final JLabel skippedValueLabel;
	private final JLabel statusLabel;
	private final JCheckBox enabledCheckbox;

	/**
	 * Creates an update settings panel.
	 *
	 * @param settings
	 *            launcher settings
	 * @param releaseSource
	 *            the release source for "Check Now"; may be {@code null} if not configured
	 */
	public UpdateSettingsPanel(LauncherSettings settings, ReleaseSource releaseSource) {
		super(new BorderLayout(0, 10));
		this.settings = settings;
		this.releaseSource = releaseSource;

		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		// ── Title ─────────────────────────────────────────────────────────────
		JLabel title = new JLabel("Auto-Update");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		add(title, BorderLayout.NORTH);

		// ── Settings rows ──────────────────────────────────────────────────────
		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setOpaque(false);

		// Row 1: enabled checkbox
		enabledCheckbox = new JCheckBox("Check for updates on startup");
		enabledCheckbox.setSelected(settings.isUpdateCheckEnabled());
		enabledCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);
		enabledCheckbox.addActionListener(e -> settings.setUpdateCheckEnabled(enabledCheckbox.isSelected()));
		rows.add(enabledCheckbox);
		rows.add(Box.createVerticalStrut(8));

		// Row 2: skipped version
		JPanel skipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		skipRow.setOpaque(false);
		skipRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		skipRow.add(new JLabel("Skipped version:"));

		String skipped = settings.getSkippedVersion();
		skippedValueLabel = new JLabel(skipped != null && !skipped.isEmpty() ? skipped : "(none)");
		skippedValueLabel.setFont(skippedValueLabel.getFont().deriveFont(Font.ITALIC));
		skipRow.add(skippedValueLabel);

		JButton clearSkipButton = new JButton("Clear");
		clearSkipButton.addActionListener(e -> {
			settings.setSkippedVersion(null);
			skippedValueLabel.setText("(none)");
			status("Skipped version cleared.");
		});
		skipRow.add(clearSkipButton);
		rows.add(skipRow);
		rows.add(Box.createVerticalStrut(6));

		// Row 3: Check Now button (directly below skipped version)
		JPanel checkRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		checkRow.setOpaque(false);
		checkRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		JButton checkNowButton = new JButton("Check Now");
		checkNowButton.addActionListener(e -> handleCheckNow(checkNowButton));
		checkRow.add(checkNowButton);
		rows.add(checkRow);
		rows.add(Box.createVerticalStrut(4));

		// Row 4: status label (below Check Now)
		JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		statusRow.setOpaque(false);
		statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);

		statusLabel = new JLabel(" ");
		statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
		statusLabel.setForeground(new Color(0x555555));
		statusRow.add(statusLabel);
		rows.add(statusRow);

		// Wrap in a BorderLayout.NORTH so the rows stay pinned to the top
		// instead of stretching to fill the full height of the center area.
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(rows, BorderLayout.NORTH);
		add(wrapper, BorderLayout.CENTER);
	}

	private void handleCheckNow(JButton checkNowButton) {
		checkNowButton.setEnabled(false);
		status("Checking for updates\u2026");

		new SwingWorker<UpdatePlan, Void>() {

			@Override
			protected UpdatePlan doInBackground() throws Exception {
				// checkManual ignores the skipped-version filter so users always see available
				// updates when clicking "Check Now" explicitly.
				return UpdateChecker.checkManual(releaseSource, LauncherVersion.VERSION, settings);
			}

			@Override
			protected void done() {
				try {
					UpdatePlan plan = get();
					if (plan == null) {
						status("Update check not configured.");
					} else if (!plan.hasUpdate()) {
						status("You are up to date (" + plan.getCurrentVersion() + ").");
					} else {
						status("Update available: " + plan.latestVersion());
						Window ancestor = SwingUtilities.getWindowAncestor(UpdateSettingsPanel.this);
						new UpdateDialog(ancestor, plan, settings).setVisible(true);
					}
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					status("Check failed: " + cause.getMessage());
				} finally {
					checkNowButton.setEnabled(true);
				}
			}

		}.execute();
	}

	private void status(String message) {
		statusLabel.setText(message);
	}

}
