package net.classiclauncher.launcher.ui;

import java.awt.*;
import java.net.URI;
import java.util.List;

import javax.swing.*;

import net.classiclauncher.launcher.extension.ExtensionManifest;
import net.classiclauncher.launcher.extension.Extensions;
import net.classiclauncher.launcher.ui.settings.InstallProgressDialog;
import net.classiclauncher.launcher.uri.ExtensionInstallRequest;

/**
 * Standalone modal dialog shown when a {@code classiclauncher://install} URI triggers an extension install from outside
 * the launcher's normal settings UI.
 *
 * <h3>Usage</h3>
 *
 * <pre>{@code
 *
 * ExtensionUriConfirmDialog dialog = new ExtensionUriConfirmDialog(ownerWindow, request, extensions);
 * boolean installed = dialog.showAndWait(); // must be called on the EDT
 * }</pre>
 *
 * <p>
 * The dialog first fetches (or reads) the manifest in a background thread while showing a loading indicator. Once the
 * manifest is available the user sees extension details and can confirm or cancel the install.
 */
public final class ExtensionUriConfirmDialog extends JDialog {

	private final ExtensionInstallRequest request;
	private final Extensions extensions;

	// ── Loading panel (North) ──────────────────────────────────────────────────
	private final JPanel loadingPanel;

	// ── Details panel (Center, hidden during fetch) ────────────────────────────
	private final JPanel detailsPanel;
	private final JLabel sourceLabel;
	private final JLabel nameLabel;
	private final JLabel descLabel;
	private final JLabel versionLabel;
	private final JButton pageUrlButton;

	// ── Buttons (South) ────────────────────────────────────────────────────────
	private final JButton cancelButton;
	private final JButton installButton;

	// ── State ─────────────────────────────────────────────────────────────────
	private volatile boolean installed = false;
	private ExtensionManifest fetchedManifest = null;

	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Creates the dialog. The manifest is not fetched until {@link #showAndWait()} is called.
	 *
	 * @param owner
	 *            the parent window (may be null)
	 * @param request
	 *            the parsed install request describing what to install
	 * @param extensions
	 *            the Extensions manager used to resolve plans and perform installs
	 */
	public ExtensionUriConfirmDialog(Window owner, ExtensionInstallRequest request, Extensions extensions) {
		super(owner, "Install Extension", ModalityType.APPLICATION_MODAL);
		this.request = request;
		this.extensions = extensions;

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setResizable(false);

		// ── Loading panel ─────────────────────────────────────────────────────
		JLabel fetchLabel = new JLabel("Fetching extension manifest…");
		JProgressBar fetchBar = new JProgressBar();
		fetchBar.setIndeterminate(true);

		loadingPanel = new JPanel();
		loadingPanel.setLayout(new BoxLayout(loadingPanel, BoxLayout.Y_AXIS));
		loadingPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));
		fetchLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		loadingPanel.add(fetchLabel);
		loadingPanel.add(Box.createVerticalStrut(6));
		fetchBar.setAlignmentX(Component.LEFT_ALIGNMENT);
		fetchBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, fetchBar.getPreferredSize().height));
		loadingPanel.add(fetchBar);

		// ── Details panel ─────────────────────────────────────────────────────
		sourceLabel = new JLabel();
		nameLabel = new JLabel();
		descLabel = new JLabel();
		versionLabel = new JLabel();
		pageUrlButton = new JButton();
		pageUrlButton.setBorderPainted(false);
		pageUrlButton.setContentAreaFilled(false);
		pageUrlButton.setFocusPainted(false);
		pageUrlButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		pageUrlButton.setHorizontalAlignment(SwingConstants.LEFT);
		pageUrlButton.setVisible(false);

		detailsPanel = new JPanel();
		detailsPanel.setLayout(new BoxLayout(detailsPanel, BoxLayout.Y_AXIS));
		detailsPanel.setVisible(false);

		sourceLabel.setFont(sourceLabel.getFont().deriveFont(Font.PLAIN, 11f));
		sourceLabel.setForeground(new Color(0x555555));
		sourceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		detailsPanel.add(sourceLabel);
		detailsPanel.add(Box.createVerticalStrut(10));

		nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, nameLabel.getFont().getSize() * 1.1f));
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		detailsPanel.add(nameLabel);

		detailsPanel.add(Box.createVerticalStrut(4));
		descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		detailsPanel.add(descLabel);

		detailsPanel.add(Box.createVerticalStrut(4));
		versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 11f));
		versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		detailsPanel.add(versionLabel);

		pageUrlButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		detailsPanel.add(Box.createVerticalStrut(8));
		detailsPanel.add(pageUrlButton);

		// ── Button row ────────────────────────────────────────────────────────
		cancelButton = new JButton("Cancel");
		installButton = new JButton("Install");
		installButton.setEnabled(false);

		cancelButton.addActionListener(e -> dispose());

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttonRow.add(cancelButton);
		buttonRow.add(installButton);

		// ── Root content ──────────────────────────────────────────────────────
		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

		JPanel north = new JPanel(new BorderLayout());
		north.add(loadingPanel, BorderLayout.CENTER);
		content.add(north, BorderLayout.NORTH);
		content.add(detailsPanel, BorderLayout.CENTER);
		content.add(buttonRow, BorderLayout.SOUTH);

		setContentPane(content);
		setMinimumSize(new Dimension(400, 0));
	}

	/**
	 * Fetches the manifest, populates the dialog, and blocks until the user dismisses it. Must be called on the EDT.
	 *
	 * @return {@code true} if the extension was successfully installed; {@code false} if the user cancelled or an error
	 *         occurred
	 */
	public boolean showAndWait() {
		// Start background manifest fetch
		new SwingWorker<ExtensionManifest, Void>() {

			@Override
			protected ExtensionManifest doInBackground() throws Exception {
				if (request.isRemote()) {
					return ExtensionManifest.fetch(request.getManifestUrl());
				} else {
					return ExtensionManifest.fromFile(request.getManifestFile());
				}
			}

			@Override
			protected void done() {
				try {
					fetchedManifest = get();
					populateDetails(fetchedManifest);
				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					dispose();
					JOptionPane.showMessageDialog(getOwner(),
							"Failed to fetch extension manifest:\n" + cause.getMessage(), "Manifest Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}

		}.execute();

		pack();
		setLocationRelativeTo(getOwner());
		setVisible(true); // blocks EDT (modal); nested event loop processes worker.done() above
		return installed;
	}

	// ── Private helpers ───────────────────────────────────────────────────────

	private void populateDetails(ExtensionManifest manifest) {
		// Source line
		if (request.isRemote()) {
			sourceLabel.setText("Source: " + request.getManifestUrl());
		} else {
			sourceLabel
					.setText("Local: " + request.getManifestFile().getName() + " + " + request.getJarFile().getName());
		}

		nameLabel.setText("<html><b style='font-size:110%'>" + escapeHtml(manifest.getName()) + "</b></html>");

		String desc = manifest.getDescription();
		if (desc != null && !desc.isEmpty()) {
			descLabel.setText("<html>" + escapeHtml(desc) + "</html>");
		} else {
			descLabel.setVisible(false);
		}

		versionLabel.setText("Version: " + manifest.getVersion());

		String pageUrl = manifest.getPageUrl();
		if (pageUrl != null && !pageUrl.isEmpty()) {
			pageUrlButton.setText("<html><a href=''>" + escapeHtml(pageUrl) + "</a></html>");
			final String finalUrl = pageUrl;
			pageUrlButton.addActionListener(e -> {
				try {
					Desktop.getDesktop().browse(new URI(finalUrl));
				} catch (Exception ex) {
					JOptionPane.showMessageDialog(this, "Could not open URL: " + finalUrl, "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			});
			pageUrlButton.setVisible(true);
		}

		// Wire install button now that we have the manifest
		installButton.addActionListener(e -> performInstall());

		// Swap loading → details
		loadingPanel.setVisible(false);
		detailsPanel.setVisible(true);
		installButton.setEnabled(true);
		pack();
		setLocationRelativeTo(getOwner());
	}

	private void performInstall() {
		if (fetchedManifest == null) return;

		// Prevent closing while install is running
		setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
		installButton.setEnabled(false);
		cancelButton.setEnabled(false);

		if (request.isRemote()) {
			performRemoteInstall();
		} else {
			performLocalInstall();
		}
	}

	private void performRemoteInstall() {
		final String manifestUrl = request.getManifestUrl();
		final InstallProgressDialog progressDialog = new InstallProgressDialog(this);

		new SwingWorker<Void, Void>() {

			private List<Extensions.ResolvedDependency> plan;

			@Override
			protected Void doInBackground() throws Exception {
				plan = extensions.resolveInstallPlan(manifestUrl);
				return null;
			}

			@Override
			protected void done() {
				try {
					List<Extensions.ResolvedDependency> resolvedPlan = plan;
					if (resolvedPlan == null) {
						// Retrieve any exception from get()
						get();
						return;
					}
					if (resolvedPlan.isEmpty()) {
						JOptionPane.showMessageDialog(ExtensionUriConfirmDialog.this,
								"This extension is already installed.", "Already Installed",
								JOptionPane.INFORMATION_MESSAGE);
						setDefaultCloseOperation(DISPOSE_ON_CLOSE);
						cancelButton.setEnabled(true);
						return;
					}

					// Confirm each dependency (all except the last, which is the root extension)
					for (int i = 0; i < resolvedPlan.size() - 1; i++) {
						if (!showDependencyConfirmDialog(resolvedPlan.get(i))) {
							JOptionPane.showMessageDialog(ExtensionUriConfirmDialog.this, "Installation cancelled.",
									"Cancelled", JOptionPane.INFORMATION_MESSAGE);
							setDefaultCloseOperation(DISPOSE_ON_CLOSE);
							cancelButton.setEnabled(true);
							return;
						}
					}

					// Install all accepted entries
					final List<Extensions.ResolvedDependency> accepted = resolvedPlan;
					new SwingWorker<Void, Void>() {

						@Override
						protected Void doInBackground() throws Exception {
							for (int i = 0; i < accepted.size(); i++) {
								Extensions.ResolvedDependency dep = accepted.get(i);
								progressDialog.getListener().onStep("Installing " + (i + 1) + " / " + accepted.size()
										+ ": " + dep.manifest.getName() + " " + dep.manifest.getVersion());
								extensions.installManifest(dep.manifest, progressDialog.getListener());
							}
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
						JOptionPane.showMessageDialog(ExtensionUriConfirmDialog.this,
								"A restart of the launcher is required for this change to take effect.",
								"Restart Required", JOptionPane.INFORMATION_MESSAGE);
						installed = true;
						dispose();
					} else {
						setDefaultCloseOperation(DISPOSE_ON_CLOSE);
						cancelButton.setEnabled(true);
					}

				} catch (Exception ex) {
					Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					JOptionPane.showMessageDialog(ExtensionUriConfirmDialog.this,
							"Failed to resolve extension dependencies:\n" + cause.getMessage(), "Resolution Error",
							JOptionPane.ERROR_MESSAGE);
					setDefaultCloseOperation(DISPOSE_ON_CLOSE);
					cancelButton.setEnabled(true);
				}
			}

		}.execute();
	}

	private void performLocalInstall() {
		if (!request.getManifestFile().exists()) {
			JOptionPane.showMessageDialog(this,
					"Manifest file not found:\n" + request.getManifestFile().getAbsolutePath(), "File Not Found",
					JOptionPane.ERROR_MESSAGE);
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			cancelButton.setEnabled(true);
			return;
		}
		if (!request.getJarFile().exists()) {
			JOptionPane.showMessageDialog(this, "Extension JAR not found:\n" + request.getJarFile().getAbsolutePath(),
					"File Not Found", JOptionPane.ERROR_MESSAGE);
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			cancelButton.setEnabled(true);
			return;
		}

		final InstallProgressDialog progressDialog = new InstallProgressDialog(this);
		final ExtensionManifest manifest = fetchedManifest;

		new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground() throws Exception {
				extensions.installLocal(manifest, request.getJarFile(), progressDialog.getListener());
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
			JOptionPane.showMessageDialog(this, "A restart of the launcher is required for this change to take effect.",
					"Restart Required", JOptionPane.INFORMATION_MESSAGE);
			installed = true;
			dispose();
		} else {
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);
			cancelButton.setEnabled(true);
		}
	}

	/**
	 * Shows a confirmation dialog for a required extension dependency.
	 *
	 * @param dep
	 *            the resolved dependency to confirm with the user
	 * @return {@code true} if the user accepted; {@code false} if they declined
	 */
	private boolean showDependencyConfirmDialog(Extensions.ResolvedDependency dep) {
		ExtensionManifest manifest = dep.manifest;
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		String requirer = dep.requiredBy != null ? dep.requiredBy : "The requested extension";
		JLabel headerLabel = new JLabel(
				"<html><b>" + escapeHtml(requirer) + "</b> requires the following extension:</html>");
		headerLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(headerLabel);
		content.add(Box.createVerticalStrut(10));

		JLabel nameLabel = new JLabel(
				"<html><b style='font-size:110%'>" + escapeHtml(manifest.getName()) + "</b></html>");
		nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(nameLabel);

		String desc = manifest.getDescription();
		if (desc != null && !desc.isEmpty()) {
			content.add(Box.createVerticalStrut(4));
			JLabel descLabel = new JLabel("<html>" + escapeHtml(desc) + "</html>");
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
			JButton pageLink = new JButton("<html><a href=''>" + escapeHtml(pageUrl) + "</a></html>");
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

	/**
	 * HTML-escapes the four characters that must be escaped inside HTML attribute values and text content: {@code &},
	 * {@code <}, {@code >}, and {@code "}.
	 *
	 * <p>
	 * Self-contained implementation — cannot access {@code ExtensionCard.escapeHtml} which is package-private in
	 * {@code ui.settings}.
	 */
	private static String escapeHtml(String text) {
		if (text == null || text.isEmpty()) return "";
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

}
