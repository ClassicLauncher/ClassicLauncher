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
 * Settings panel for managing launcher extensions.
 *
 * <p>
 * Displays installed extensions as a compact grid of {@link ExtensionCard} widgets. Each card shows the extension icon,
 * name, installed version, a toggleable status indicator, and a kebab menu for Update / Remove / View Details.
 *
 * <p>
 * This panel is responsible only for the header, grid layout, and the install flow (URL input, dependency resolution,
 * confirmation dialogs). All single-card interaction and rendering is delegated to {@link ExtensionCard}.
 */
public class ExtensionSettingsPanel extends JPanel {

	private static final int GAP = 8;

	private final Extensions extensions;
	private final JPanel gridPanel;

	public ExtensionSettingsPanel(Extensions extensions) {
		this.extensions = extensions;
		setLayout(new BorderLayout());

		// ── North: header ──────────────────────────────────────────────────────
		JPanel header = new JPanel(new BorderLayout());
		header.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));

		JLabel title = new JLabel("Installed Extensions");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
		header.add(title, BorderLayout.WEST);

		JButton addButton = new JButton("Add Extension…");
		addButton.addActionListener(e -> promptInstall());
		header.add(addButton, BorderLayout.EAST);

		add(header, BorderLayout.NORTH);

		// ── Center: scrollable grid ────────────────────────────────────────────
		gridPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, GAP, GAP));
		gridPanel.setBackground(Color.WHITE);
		gridPanel.setOpaque(true);

		JScrollPane scroll = new JScrollPane(gridPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(Color.WHITE);
		add(scroll, BorderLayout.CENTER);

		refreshGrid();
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

		JButton localButton = new JButton("Local…");
		localButton.setToolTipText("Install from a local manifest file and JAR");

		Object[] options = {localButton, "OK", "Cancel"};
		JOptionPane pane = new JOptionPane(content, JOptionPane.PLAIN_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null,
				options, "OK");

		JDialog dialog = pane.createDialog(this, "Add Extension");

		localButton.addActionListener(e -> {
			dialog.setVisible(false);
			promptLocalInstall();
		});

		dialog.setVisible(true);

		Object selected = pane.getValue();
		if (!"OK".equals(selected)) return;

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
					refreshGrid();
					progressDialog.markSuccess();
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					progressDialog.markFailed(cause.getMessage());
				}
			}

		}.execute();

		progressDialog.setVisible(true);
		if (progressDialog.isSuccessful()) {
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
					refreshGrid();
					dialog.markSuccess();
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					dialog.markFailed(cause.getMessage());
				}
			}

		}.execute();

		dialog.setVisible(true); // blocks EDT (modal) while worker runs in background
		// After OK is clicked: check if we need to show restart notice
		// markSuccess() leaves the dialog with a success state; we show the restart
		// dialog once the modal returns (i.e. user clicked OK).
		if (dialog.isSuccessful()) {
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
