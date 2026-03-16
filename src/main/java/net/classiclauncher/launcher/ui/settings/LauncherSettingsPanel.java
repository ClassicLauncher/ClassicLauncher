package net.classiclauncher.launcher.ui.settings;

import java.awt.*;

import javax.swing.*;

import net.classiclauncher.launcher.settings.LauncherSettings;
import net.classiclauncher.launcher.settings.LauncherStyle;

/**
 * Settings page for the top-level launcher configuration.
 *
 * <p>
 * Exposes two settings from {@link LauncherSettings}:
 * <ul>
 * <li><b>UI Style</b> — combo box selecting between {@link LauncherStyle#ALPHA} (2010 login window) and
 * {@link LauncherStyle#V1_1} (2013 tabbed interface). A restart is required for the change to take effect.</li>
 * <li><b>Update Notes URL</b> — text field; leave blank to hide the Update Notes tab in V1_1. Per-provider URLs set via
 * {@code AccountProvider.getUpdateNotesUrl()} take precedence over this value at runtime.</li>
 * </ul>
 *
 * <p>
 * Changes are persisted to {@code <dataDir>/settings.yml} only when the user clicks <em>Apply</em>.
 */
public class LauncherSettingsPanel extends SettingsPage {

	private final LauncherSettings launcherSettings;
	private final JComboBox<StyleChoice> styleCombo;
	private final JTextField updateNotesField;

	public LauncherSettingsPanel(LauncherSettings launcherSettings) {
		super("launcher", "Launcher", 10);
		this.launcherSettings = launcherSettings;

		// ── Form ──────────────────────────────────────────────────────────────
		JPanel form = new JPanel(new GridBagLayout());
		form.setOpaque(false);

		GridBagConstraints labelConstraints = labelConstraints(0);
		GridBagConstraints fieldConstraints = fieldConstraints(0);

		// Style
		form.add(new JLabel("UI Style:"), labelConstraints);
		styleCombo = new JComboBox<>(
				new StyleChoice[]{new StyleChoice(LauncherStyle.ALPHA, "Alpha  \u2013  2010 login window"),
						new StyleChoice(LauncherStyle.V1_1, "V1.1  \u2013  2013 tabbed interface")});
		preselectStyle(launcherSettings.getStyle());
		form.add(styleCombo, fieldConstraints);

		labelConstraints.gridy++;
		fieldConstraints.gridy++;
		form.add(new JLabel(""), labelConstraints);
		form.add(hintLabel("A restart is required for style changes to take effect."), fieldConstraints);

		// Update Notes URL
		labelConstraints.gridy++;
		fieldConstraints.gridy++;
		form.add(new JLabel("Update Notes URL:"), labelConstraints);
		updateNotesField = new JTextField(launcherSettings.getUpdateNotesUrl(), 32);
		form.add(updateNotesField, fieldConstraints);

		labelConstraints.gridy++;
		fieldConstraints.gridy++;
		form.add(new JLabel(""), labelConstraints);
		form.add(hintLabel("Leave blank to hide the Update Notes tab (V1.1 only)."), fieldConstraints);

		// Push form rows to the top by consuming remaining vertical space
		labelConstraints.gridy++;
		labelConstraints.weighty = 1.0;
		fieldConstraints.gridy++;
		fieldConstraints.weighty = 1.0;
		form.add(new JPanel(), labelConstraints);
		form.add(new JPanel(), fieldConstraints);

		// ── Apply button ─────────────────────────────────────────────────────
		JButton applyBtn = new JButton("Apply");
		applyBtn.addActionListener(e -> handleApply());

		buildPage(new PageLayout().body(form).footerAction(applyBtn));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Button handler
	// ─────────────────────────────────────────────────────────────────────────

	private void handleApply() {
		StyleChoice chosen = (StyleChoice) styleCombo.getSelectedItem();
		if (chosen != null) {
			launcherSettings.setStyle(chosen.style);
		}
		String url = updateNotesField.getText().trim();
		launcherSettings.setUpdateNotesUrl(url);
		setStatus("Settings saved.");
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Helpers
	// ─────────────────────────────────────────────────────────────────────────

	private void preselectStyle(LauncherStyle current) {
		for (int i = 0; i < styleCombo.getItemCount(); i++) {
			if (styleCombo.getItemAt(i).style == current) {
				styleCombo.setSelectedIndex(i);
				return;
			}
		}
	}

	private static GridBagConstraints labelConstraints(int row) {
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = row;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.insets = new Insets(6, 0, 0, 10);
		return c;
	}

	private static GridBagConstraints fieldConstraints(int row) {
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 1;
		c.gridy = row;
		c.anchor = GridBagConstraints.NORTHWEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.weightx = 1.0;
		c.insets = new Insets(4, 0, 0, 0);
		return c;
	}

	private static JLabel hintLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.ITALIC, 11f));
		Color disabled = UIManager.getColor("Label.disabledForeground");
		label.setForeground(disabled != null ? disabled : Color.GRAY);
		return label;
	}

	// ── StyleChoice ──────────────────────────────────────────────────────────

	private static final class StyleChoice {

		final LauncherStyle style;
		final String label;

		StyleChoice(LauncherStyle style, String label) {
			this.style = style;
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}

	}

}
