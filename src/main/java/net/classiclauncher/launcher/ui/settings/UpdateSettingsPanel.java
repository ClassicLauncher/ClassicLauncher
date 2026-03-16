package net.classiclauncher.launcher.ui.settings;

import java.awt.*;

import javax.swing.*;

import net.classiclauncher.launcher.LauncherVersion;
import net.classiclauncher.launcher.settings.LauncherSettings;
import net.classiclauncher.launcher.ui.update.UpdateDialog;
import net.classiclauncher.launcher.update.ReleaseSource;
import net.classiclauncher.launcher.update.UpdateChecker;
import net.classiclauncher.launcher.update.UpdatePlan;

/**
 * Settings page for configuring the auto-update system.
 *
 * <p>
 * Body content (top to bottom):
 * <ol>
 * <li>Checkbox — enable/disable automatic update checks on startup.</li>
 * <li>Skipped version row — label, current skipped version, and a Clear button.</li>
 * </ol>
 *
 * <p>
 * Footer: "Check Now" button (left) and status label (right).
 */
public class UpdateSettingsPanel extends SettingsPage {

	private final LauncherSettings settings;
	private final ReleaseSource releaseSource;
	private final JLabel skippedValueLabel;
	private final JCheckBox enabledCheckbox;

	/**
	 * Creates an update settings page.
	 *
	 * @param settings
	 *            launcher settings
	 * @param releaseSource
	 *            the release source for "Check Now"; may be {@code null} if not configured
	 */
	public UpdateSettingsPanel(LauncherSettings settings, ReleaseSource releaseSource) {
		super("updates", "Updates", 40);
		this.settings = settings;
		this.releaseSource = releaseSource;

		// ── Settings rows (body) ─────────────────────────────────────────────
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
			setStatus("Skipped version cleared.");
		});
		skipRow.add(clearSkipButton);
		rows.add(skipRow);

		// Wrap in a BorderLayout.NORTH so the rows stay pinned to the top
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(rows, BorderLayout.NORTH);

		// ── Check Now button (footer action) ─────────────────────────────────
		JButton checkNowButton = new JButton("Check Now");
		checkNowButton.addActionListener(e -> handleCheckNow(checkNowButton));

		buildPage(new PageLayout().body(wrapper).footerAction(checkNowButton));
	}

	private void handleCheckNow(JButton checkNowButton) {
		checkNowButton.setEnabled(false);
		setStatus("Checking for updates\u2026");

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
						setStatus("Update check not configured.");
					} else if (!plan.hasUpdate()) {
						setStatus("You are up to date (" + plan.getCurrentVersion() + ").");
					} else {
						setStatus("Update available: " + plan.latestVersion());
						Window ancestor = SwingUtilities.getWindowAncestor(UpdateSettingsPanel.this);
						new UpdateDialog(ancestor, plan, settings).setVisible(true);
					}
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					setStatus("Check failed: " + cause.getMessage());
				} finally {
					checkNowButton.setEnabled(true);
				}
			}

		}.execute();
	}

}
