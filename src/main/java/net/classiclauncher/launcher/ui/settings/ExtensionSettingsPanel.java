package net.classiclauncher.launcher.ui.settings;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.List;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

import net.classiclauncher.launcher.extension.ExtensionManifest;
import net.classiclauncher.launcher.extension.ExtensionRecord;
import net.classiclauncher.launcher.extension.Extensions;

/**
 * Settings page for managing launcher extensions.
 *
 * <p>
 * Displays installed extensions as a compact grid of {@link ExtensionCard} widgets. Each card shows the extension icon,
 * name, installed version, a toggleable status indicator, and a kebab menu for Update / Remove / View Details.
 *
 * <p>
 * This panel is responsible only for the header, grid layout, and the install flow (URL input, dependency resolution,
 * confirmation dialogs). All single-card interaction and rendering is delegated to {@link ExtensionCard}.
 */
public class ExtensionSettingsPanel extends SettingsPage {

	private static final int GAP = 8;

	private final Extensions extensions;
	private final JPanel gridPanel;

	public ExtensionSettingsPanel(Extensions extensions) {
		super("extensions", "Extensions", 30);
		this.extensions = extensions;

		// ── Scrollable grid (body) ────────────────────────────────────────────
		// A standard FlowLayout does not recalculate preferred height when the container
		// width changes, so cards get clipped inside a JScrollPane. This custom panel
		// overrides getPreferredSize to compute the wrapped height from the viewport width,
		// ensuring the scroll pane always shows the full grid without horizontal overflow.
		gridPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, GAP, GAP)) {

			@Override
			public Dimension getPreferredSize() {
				int width = getParent() != null ? getParent().getWidth() : getWidth();
				if (width <= 0) return super.getPreferredSize();

				Insets insets = getInsets();
				int availableWidth = width - insets.left - insets.right;
				int x = GAP;
				int y = GAP;
				int rowHeight = 0;
				for (int i = 0; i < getComponentCount(); i++) {
					Dimension d = getComponent(i).getPreferredSize();
					if (x + d.width + GAP > availableWidth && x > GAP) {
						x = GAP;
						y += rowHeight + GAP;
						rowHeight = 0;
					}
					x += d.width + GAP;
					rowHeight = Math.max(rowHeight, d.height);
				}
				y += rowHeight + GAP;
				return new Dimension(width, y + insets.top + insets.bottom);
			}

		};
		gridPanel.setBackground(Color.WHITE);
		gridPanel.setOpaque(true);

		JScrollPane scroll = new JScrollPane(gridPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(Color.WHITE);
		scroll.getViewport().addChangeListener(e -> {
			gridPanel.revalidate();
			gridPanel.repaint();
		});

		// ── Footer buttons ───────────────────────────────────────────────────
		JButton installButton = new JButton("Install Extension");
		installButton.addActionListener(e -> promptInstall());

		JButton installLocalButton = new JButton("Install Local Extension");
		installLocalButton.addActionListener(e -> promptLocalInstall());

		buildPage(
				new PageLayout().body(scroll).footerAction(installButton).footerAction(installLocalButton).noStatus());

		refreshGrid();

		// Refresh the grid whenever any extension is installed (including via the
		// custom URI handler, which bypasses this panel's own install flow entirely).
		extensions.addInstallListener(() -> SwingUtilities.invokeLater(this::refreshGrid));
	}

	// ── Grid management ───────────────────────────────────────────────────────

	/**
	 * Rebuilds all extension cards from the current record list. Passed as a callback to each {@link ExtensionCard} so
	 * cards can trigger a full refresh after any mutation.
	 */
	private void refreshGrid() {
		gridPanel.removeAll();
		for (ExtensionRecord record : extensions.getAll()) {
			gridPanel.add(new ExtensionCard(record, extensions, this::refreshGrid));
		}
		gridPanel.revalidate();
		gridPanel.repaint();
		revalidate();
		repaint();
	}

	// ── Install flow ──────────────────────────────────────────────────────────

	/**
	 * Opens an install dialog with a URL text field and a "Local" button. The URL field lets the user paste a remote
	 * manifest URL (existing flow). The "Local" button opens file choosers for a manifest YAML and extension JAR on
	 * disk.
	 */
	private void promptInstall() {
		JTextField urlField = new JTextField();

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		JLabel label = new JLabel("Enter the extension manifest URL:");
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(label);
		content.add(Box.createVerticalStrut(4));
		urlField.setAlignmentX(Component.LEFT_ALIGNMENT);
		urlField.setMaximumSize(new Dimension(Integer.MAX_VALUE, urlField.getPreferredSize().height));
		content.add(urlField);

		int result = JOptionPane.showConfirmDialog(this, content, "Install Extension", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);
		if (result != JOptionPane.OK_OPTION) return;

		String url = urlField.getText();
		if (url == null || url.trim().isEmpty()) return;
		final String manifestUrl = url.trim();

		new SwingWorker<List<Extensions.ResolvedDependency>, Void>() {

			@Override
			protected List<Extensions.ResolvedDependency> doInBackground() throws Exception {
				return extensions.resolveInstallPlan(manifestUrl);
			}

			@Override
			protected void done() {
				try {
					confirmAndInstall(get());
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					JOptionPane.showMessageDialog(ExtensionSettingsPanel.this,
							"Failed to resolve extension dependencies:\n" + cause.getMessage(), "Resolution Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}

		}.execute();
	}

	/**
	 * Local install flow: opens a file chooser for the manifest YAML, then for the JAR. Dependencies declared in the
	 * manifest are still downloaded from their repositories.
	 */
	private void promptLocalInstall() {
		JFileChooser manifestChooser = new JFileChooser();
		manifestChooser.setDialogTitle("Select Extension Manifest");
		manifestChooser.setFileFilter(new FileNameExtensionFilter("YAML Manifest (*.yml, *.yaml)", "yml", "yaml"));
		if (manifestChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		File manifestFile = manifestChooser.getSelectedFile();

		ExtensionManifest manifest;
		try {
			manifest = ExtensionManifest.fromFile(manifestFile);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "Failed to parse manifest:\n" + ex.getMessage(), "Manifest Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		JFileChooser jarChooser = new JFileChooser(manifestFile.getParentFile());
		jarChooser.setDialogTitle("Select Extension JAR for \"" + manifest.getName() + "\"");
		jarChooser.setFileFilter(new FileNameExtensionFilter("JAR Files (*.jar)", "jar"));
		if (jarChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		File jarFile = jarChooser.getSelectedFile();

		InstallProgressDialog progressDialog = new InstallProgressDialog(this);
		final ExtensionManifest finalManifest = manifest;

		new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground() throws Exception {
				extensions.installLocal(finalManifest, jarFile, progressDialog.getListener());
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
					progressDialog.markSuccess();
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					progressDialog.markFailed(cause.getMessage());
				}
			}

		}.execute();

		progressDialog.setVisible(true); // blocks until user clicks OK
		if (progressDialog.isSuccessful()) {
			refreshGrid();
			JOptionPane.showMessageDialog(this, "A restart of the launcher is required for this change to take effect.",
					"Restart Required", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private void confirmAndInstall(List<Extensions.ResolvedDependency> plan) {
		if (plan.isEmpty()) {
			JOptionPane.showMessageDialog(this, "This extension is already installed.", "Already Installed",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		for (int i = 0; i < plan.size() - 1; i++) {
			if (!showDependencyConfirmDialog(plan.get(i))) {
				JOptionPane.showMessageDialog(this, "Installation cancelled.", "Cancelled",
						JOptionPane.INFORMATION_MESSAGE);
				return;
			}
		}
		final List<Extensions.ResolvedDependency> accepted = plan;

		InstallProgressDialog dialog = new InstallProgressDialog(this);

		new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground() throws Exception {
				for (int i = 0; i < accepted.size(); i++) {
					Extensions.ResolvedDependency dep = accepted.get(i);
					dialog.getListener().onStep("Installing " + (i + 1) + " / " + accepted.size() + ": "
							+ dep.manifest.getName() + " " + dep.manifest.getVersion());
					extensions.installManifest(dep.manifest, dialog.getListener());
				}
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
					dialog.markSuccess();
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					dialog.markFailed(cause.getMessage());
				}
			}

		}.execute();

		dialog.setVisible(true); // blocks until user clicks OK
		if (dialog.isSuccessful()) {
			refreshGrid();
			JOptionPane.showMessageDialog(this, "A restart of the launcher is required for this change to take effect.",
					"Restart Required", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	private boolean showDependencyConfirmDialog(Extensions.ResolvedDependency dep) {
		ExtensionManifest manifest = dep.manifest;
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		String requirer = dep.requiredBy != null ? dep.requiredBy : "The requested extension";
		JLabel headerLabel = new JLabel(
				"<html><b>" + ExtensionCard.escapeHtml(requirer) + "</b> requires the following extension:</html>");
		headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(headerLabel);
		content.add(Box.createVerticalStrut(10));

		JLabel nameLabel = new JLabel(
				"<html><b style='font-size:110%'>" + ExtensionCard.escapeHtml(manifest.getName()) + "</b></html>");
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(nameLabel);

		String desc = manifest.getDescription();
		if (desc != null && !desc.isEmpty()) {
			content.add(Box.createVerticalStrut(4));
			JLabel descLabel = new JLabel("<html>" + ExtensionCard.escapeHtml(desc) + "</html>");
			descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			content.add(descLabel);
		}

		content.add(Box.createVerticalStrut(4));
		JLabel versionLabel = new JLabel("Version: " + manifest.getVersion());
		versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 11f));
		versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(versionLabel);

		String pageUrl = manifest.getPageUrl();
		if (pageUrl != null && !pageUrl.isEmpty()) {
			content.add(Box.createVerticalStrut(8));
			JButton pageLink = new JButton("<html><a href=''>" + ExtensionCard.escapeHtml(pageUrl) + "</a></html>");
			pageLink.setBorderPainted(false);
			pageLink.setContentAreaFilled(false);
			pageLink.setFocusPainted(false);
			pageLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			pageLink.setAlignmentX(Component.LEFT_ALIGNMENT);
			pageLink.setHorizontalAlignment(SwingConstants.LEFT);
			final String finalUrl = pageUrl;
			pageLink.addActionListener(e -> {
				try {
					Desktop.getDesktop().browse(new URI(finalUrl));
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(this, "Could not open URL: " + finalUrl, "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			});
			content.add(pageLink);
		}

		return JOptionPane.showConfirmDialog(this, content, "Required Extension", JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
	}

}
