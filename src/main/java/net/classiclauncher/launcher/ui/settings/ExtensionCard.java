package net.classiclauncher.launcher.ui.settings;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;
import java.util.List;

import javax.swing.*;
import javax.swing.border.Border;

import net.classiclauncher.launcher.extension.ExtensionRecord;
import net.classiclauncher.launcher.extension.Extensions;
import net.classiclauncher.launcher.ui.IconButton;
import net.classiclauncher.launcher.ui.ResourceLoader;

/**
 * Compact card widget representing a single installed extension.
 *
 * <p>
 * Displays the extension's icon (loaded from the disk cache, or a generated initials fallback), its name, installed
 * version, and an optional "Used by N extensions" label. Two interactive controls sit on the right:
 * <ul>
 * <li>A coloured <b>status dot</b> — green when enabled, red when disabled — that toggles the extension on click with a
 * confirmation dialog and cascade dependency checks.</li>
 * <li>A <b>kebab button</b> (⋮) that opens a popup menu with Update, Remove, and View Details actions.</li>
 * </ul>
 *
 * <p>
 * After any mutation (toggle, update, remove) the card invokes the {@code onRefresh} callback supplied at construction
 * so the parent panel can rebuild its grid.
 */
public class ExtensionCard extends JPanel {

	static final int WIDTH = 220;
	static final int HEIGHT = 72;

	private static final int ICON_SIZE = 36;
	private static final Color COLOR_BG = Color.WHITE;
	private static final Color COLOR_HOVER = new Color(0xF0F6FF);
	private static final Color COLOR_BORDER = new Color(0xDDDDDD);
	private static final Color COLOR_HOVER_BORDER = new Color(0x4A90D9);
	private static final Color COLOR_ENABLED = new Color(0x34A853);
	private static final Color COLOR_DISABLED = new Color(0xEA4335);
	private static final Color COLOR_NAME = new Color(0x222222);
	private static final Color COLOR_VERSION = new Color(0x777777);
	private static final Color COLOR_USED_BY = new Color(0x999999);

	private final ExtensionRecord record;
	private final Extensions extensions;
	private final Runnable onRefresh;

	public ExtensionCard(ExtensionRecord record, Extensions extensions, Runnable onRefresh) {
		this.record = record;
		this.extensions = extensions;
		this.onRefresh = onRefresh;
		buildLayout();
	}

	// ── Layout ────────────────────────────────────────────────────────────────

	private void buildLayout() {
		Border defaultBorder = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(COLOR_BORDER, 1),
				BorderFactory.createEmptyBorder(6, 8, 6, 2));
		Border hoverBorder = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(COLOR_HOVER_BORDER, 2),
				BorderFactory.createEmptyBorder(5, 7, 5, 1));

		setLayout(new BorderLayout(8, 0));
		setPreferredSize(new Dimension(WIDTH, HEIGHT));
		setMaximumSize(new Dimension(WIDTH, HEIGHT));
		setBackground(COLOR_BG);
		setOpaque(true);
		setBorder(defaultBorder);

		// Icon
		JLabel iconLabel = new JLabel(loadIcon());
		iconLabel.setPreferredSize(new Dimension(ICON_SIZE, ICON_SIZE));
		add(iconLabel, BorderLayout.WEST);

		// Name + version + optional "used by"
		add(buildTextPanel(), BorderLayout.CENTER);

		// Status dot + kebab
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		right.setOpaque(false);
		right.add(buildStatusDot());
		right.add(buildKebabButton());
		add(right, BorderLayout.EAST);

		// Hover effect on card body only (child controls handle their own hover)
		addMouseListener(new MouseAdapter() {

			@Override
			public void mouseEntered(MouseEvent e) {
				setBackground(COLOR_HOVER);
				setBorder(hoverBorder);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				setBackground(COLOR_BG);
				setBorder(defaultBorder);
			}

		});
	}

	private JPanel buildTextPanel() {
		JPanel panel = new JPanel();
		panel.add(Box.createVerticalStrut(6));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setAlignmentY(Component.CENTER_ALIGNMENT);

		JLabel name = new JLabel(record.getName());
		name.setFont(name.getFont().deriveFont(Font.BOLD, 11f));
		name.setForeground(COLOR_NAME);
		panel.add(name);

		JLabel version = new JLabel(record.getInstalledVersion());
		version.setFont(version.getFont().deriveFont(Font.PLAIN, 10f));
		version.setForeground(COLOR_VERSION);
		panel.add(version);

		List<ExtensionRecord> directDependents = extensions.getDirectDependents(record.getId());
		if (!directDependents.isEmpty()) {
			JLabel usedBy = new JLabel("Used by " + directDependents.size()
					+ (directDependents.size() == 1 ? " extension" : " extensions"));
			usedBy.setFont(usedBy.getFont().deriveFont(Font.PLAIN, 9f));
			usedBy.setForeground(COLOR_USED_BY);
			StringBuilder tooltip = new StringBuilder("<html>Required by:<br>");
			for (ExtensionRecord dep : directDependents) {
				tooltip.append("&nbsp;• ").append(escapeHtml(dep.getName())).append("<br>");
			}
			tooltip.append("</html>");
			usedBy.setToolTipText(tooltip.toString());
			panel.add(usedBy);
		}

		return panel;
	}

	// ── Status dot ────────────────────────────────────────────────────────────

	private JLabel buildStatusDot() {
		JLabel dot = new JLabel() {

			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(record.isEnabled() ? COLOR_ENABLED : COLOR_DISABLED);
				int d = Math.min(getWidth(), getHeight()) - 2;
				g2.fillOval((getWidth() - d) / 2, (getHeight() - d) / 2, d, d);
				g2.dispose();
			}

		};
		dot.setPreferredSize(new Dimension(14, 14));
		dot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		dot.setToolTipText(record.isEnabled() ? "Enabled — click to disable" : "Disabled — click to enable");
		dot.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				handleToggle();
			}

		});
		return dot;
	}

	private void handleToggle() {
		if (record.isEnabled()) {
			if (JOptionPane.showConfirmDialog(this, "Disable extension \"" + record.getName() + "\"?",
					"Disable Extension", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
				return;

			List<ExtensionRecord> dependents = extensions.getAllEnabledDependents(record.getId());
			if (!dependents.isEmpty() && !confirmCascade(dependents,
					"<html>The following extensions depend on <b>" + escapeHtml(record.getName())
							+ "</b> and will also be disabled:<br><br>",
					"Disable Extension", JOptionPane.WARNING_MESSAGE))
				return;

			for (ExtensionRecord dep : dependents)
				extensions.setEnabled(dep.getId(), false);
			extensions.setEnabled(record.getId(), false);

		} else {
			if (JOptionPane.showConfirmDialog(this, "Enable extension \"" + record.getName() + "\"?",
					"Enable Extension", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
				return;

			List<ExtensionRecord> requirements = extensions.getAllDisabledRequirements(record.getId());
			if (!requirements.isEmpty() && !confirmCascade(requirements,
					"<html><b>" + escapeHtml(record.getName())
							+ "</b> requires extensions that are currently disabled:<br><br>",
					"Enable Extension", JOptionPane.QUESTION_MESSAGE))
				return;

			for (ExtensionRecord req : requirements)
				extensions.setEnabled(req.getId(), true);
			extensions.setEnabled(record.getId(), true);
		}

		onRefresh.run();
		showRestartRequired();
	}

	/**
	 * Shows a cascade confirmation dialog listing affected extensions.
	 *
	 * @param affected
	 *            extensions that will also be changed
	 * @param htmlPreamble
	 *            opening HTML sentence (without the bullet list)
	 * @param title
	 *            dialog title
	 * @param messageType
	 *            {@link JOptionPane} message-type constant
	 * @return {@code true} if the user confirmed
	 */
	private boolean confirmCascade(List<ExtensionRecord> affected, String htmlPreamble, String title, int messageType) {
		StringBuilder msg = new StringBuilder(htmlPreamble);
		for (ExtensionRecord r : affected) {
			msg.append("&nbsp;&nbsp;• ").append(escapeHtml(r.getName())).append("<br>");
		}
		msg.append("<br>Do you want to continue?</html>");
		return JOptionPane.showConfirmDialog(this, new JLabel(msg.toString()), title, JOptionPane.YES_NO_OPTION,
				messageType) == JOptionPane.YES_OPTION;
	}

	// ── Kebab menu ────────────────────────────────────────────────────────────

	private IconButton buildKebabButton() {
		IconButton kebab = new IconButton("⋮", "Options").withFontStyle(Font.BOLD, 14f).withSize(24, 24);
		kebab.addActionListener(e -> showKebabMenu(kebab));
		return kebab;
	}

	private void showKebabMenu(Component source) {
		JPopupMenu menu = new JPopupMenu();

		JMenuItem update = new JMenuItem("Update");
		update.addActionListener(e -> performUpdate());
		menu.add(update);

		JMenuItem remove = new JMenuItem("Remove");
		remove.addActionListener(e -> performRemove());
		menu.add(remove);

		menu.addSeparator();

		JMenuItem details = new JMenuItem("View Details");
		details.setEnabled(record.hasPageUrl());
		if (record.hasPageUrl()) {
			String url = record.getPageUrl();
			details.addActionListener(e -> {
				try {
					Desktop.getDesktop().browse(new URI(url));
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(this, "Could not open URL:\n" + url, "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			});
		}
		menu.add(details);

		menu.show(source, 0, source.getHeight());
	}

	private void performUpdate() {
		new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground() throws Exception {
				extensions.update(record.getId());
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
					onRefresh.run();
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					JOptionPane.showMessageDialog(ExtensionCard.this,
							"Failed to update extension:\n" + cause.getMessage(), "Update Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}

		}.execute();
	}

	private void performRemove() {
		List<ExtensionRecord> dependents = extensions.getAllEnabledDependents(record.getId());
		Object message;
		if (dependents.isEmpty()) {
			message = "Remove extension \"" + record.getName() + "\"?";
		} else {
			StringBuilder sb = new StringBuilder("<html>Remove <b>").append(escapeHtml(record.getName()))
					.append("</b>?<br><br>The following extensions depend on it and will also be removed:<br><br>");
			for (ExtensionRecord dep : dependents) {
				sb.append("&nbsp;&nbsp;• ").append(escapeHtml(dep.getName())).append("<br>");
			}
			sb.append("</html>");
			message = new JLabel(sb.toString());
		}
		if (JOptionPane.showConfirmDialog(this, message, "Confirm Removal", JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
			return;

		for (ExtensionRecord dep : dependents)
			extensions.uninstall(dep.getId());
		extensions.uninstall(record.getId());
		onRefresh.run();
	}

	// ── Icon loading ──────────────────────────────────────────────────────────

	private Icon loadIcon() {
		File cached = extensions.getIconCacheFile(record.getId());
		if (cached != null) {
			ImageIcon icon = ResourceLoader.loadIconFromFile(cached, ICON_SIZE);
			if (icon != null) return icon;
		}
		return createInitialsIcon(record.getName());
	}

	/**
	 * Generates a circular icon with the extension's initials on a hash-stable background colour.
	 */
	private static Icon createInitialsIcon(String name) {
		String initials = buildInitials(name);
		int hash = name == null ? 0 : name.hashCode();
		float hue = ((hash & 0x7FFFFFFF) % 360) / 360f;
		Color bg = Color.getHSBColor(hue, 0.55f, 0.80f);

		BufferedImage img = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = img.createGraphics();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setColor(bg);
		g2.fillOval(0, 0, ICON_SIZE, ICON_SIZE);
		g2.setColor(Color.WHITE);
		g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(8, (int) (ICON_SIZE * 0.38f))));
		FontMetrics fm = g2.getFontMetrics();
		g2.drawString(initials, (ICON_SIZE - fm.stringWidth(initials)) / 2,
				(ICON_SIZE - fm.getHeight()) / 2 + fm.getAscent());
		g2.dispose();
		return new ImageIcon(img);
	}

	/**
	 * Extracts up to two uppercase initials from a name.
	 */
	private static String buildInitials(String name) {
		if (name == null || name.isEmpty()) return "?";
		String[] words = name.trim().split("\\s+");
		if (words.length == 1) return words[0].substring(0, Math.min(2, words[0].length())).toUpperCase();
		return (Character.toString(words[0].charAt(0)) + words[1].charAt(0)).toUpperCase();
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private void showRestartRequired() {
		JOptionPane.showMessageDialog(this, "A restart of the launcher is required for this change to take effect.",
				"Restart Required", JOptionPane.INFORMATION_MESSAGE);
	}

	static String escapeHtml(String text) {
		if (text == null) return "";
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

}
