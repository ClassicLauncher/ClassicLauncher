package net.classiclauncher.launcher.ui.update;

import java.awt.*;
import java.net.URI;
import java.util.List;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import net.classiclauncher.launcher.LauncherVersion;
import net.classiclauncher.launcher.platform.Platform;
import net.classiclauncher.launcher.settings.LauncherSettings;
import net.classiclauncher.launcher.update.ArtifactSelector;
import net.classiclauncher.launcher.update.AssetInfo;
import net.classiclauncher.launcher.update.DistributionMode;
import net.classiclauncher.launcher.update.ReleaseInfo;
import net.classiclauncher.launcher.update.UpdateInstaller;
import net.classiclauncher.launcher.update.UpdatePlan;

/**
 * Modal dialog shown when one or more newer launcher versions are available.
 *
 * <p>
 * Layout (700×500):
 * <ul>
 * <li><b>North</b> — header showing "current → latest" version.</li>
 * <li><b>Center</b> — read-only, non-selectable, scrollable changelog area rendered from CommonMark Markdown. Version
 * headings are hyperlinks to the GitHub release tag page.</li>
 * <li><b>South</b> — "Skip This Version" and "Disable Update Checker" on the left; "Later" and
 * {@link SplitInstallButton} on the right.</li>
 * </ul>
 */
public class UpdateDialog extends JDialog {

	private static final Parser MARKDOWN_PARSER = Parser.builder().build();
	private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder().build();

	private final UpdatePlan plan;
	private final LauncherSettings settings;

	public UpdateDialog(Window owner, UpdatePlan plan, LauncherSettings settings) {
		super(owner, "Update Available", Dialog.ModalityType.APPLICATION_MODAL);
		this.plan = plan;
		this.settings = settings;

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(700, 500);
		setMinimumSize(new Dimension(600, 400));
		setLocationRelativeTo(owner);

		setContentPane(buildContent());
	}

	private JPanel buildContent() {
		JPanel content = new JPanel(new BorderLayout(0, 0));

		// ── North: version header ──────────────────────────────────────────────
		JPanel header = new JPanel(new BorderLayout());
		header.setBackground(new Color(0xF0F0F0));
		header.setOpaque(true);

		String title = "Update Available — " + plan.getCurrentVersion() + " \u2192 " + plan.latestVersion();
		JLabel titleLabel = new JLabel(title);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
		header.add(titleLabel, BorderLayout.WEST);

		int count = plan.getReleases().size();
		String subtitle = count == 1 ? "1 new version" : count + " new versions";
		JLabel subtitleLabel = new JLabel(subtitle);
		subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 11f));
		subtitleLabel.setForeground(new Color(0x555555));
		header.add(subtitleLabel, BorderLayout.EAST);

		Color sep = UIManager.getColor("Separator.foreground");
		if (sep == null) sep = Color.LIGHT_GRAY;
		header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, sep),
				BorderFactory.createEmptyBorder(12, 16, 8, 16)));

		content.add(header, BorderLayout.NORTH);

		// ── Center: changelogs ─────────────────────────────────────────────────
		// Anonymous subclass disables text selection while preserving hyperlink clicks and scrolling.
		JEditorPane changelog = new JEditorPane("text/html", buildChangelogHtml(plan.getReleases())) {

			@Override
			public void select(int selectionStart, int selectionEnd) {
				// no-op: suppress text selection
			}

		};
		changelog.setEditable(false);
		changelog.setFocusable(false);
		changelog.setBackground(Color.WHITE);
		changelog.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
		changelog.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
		changelog.setCaretPosition(0);
		changelog.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));

		// Open clicked hyperlinks in the system browser
		changelog.addHyperlinkListener(new HyperlinkListener() {

			@Override
			public void hyperlinkUpdate(HyperlinkEvent e) {
				if (e.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
				if (!Desktop.isDesktopSupported()) return;
				try {
					String href = e.getURL() != null ? e.getURL().toExternalForm() : e.getDescription();
					Desktop.getDesktop().browse(new URI(href));
				} catch (Exception ex) {
					// silently ignore — best-effort navigation
				}
			}

		});

		JScrollPane scroll = new JScrollPane(changelog);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		content.add(scroll, BorderLayout.CENTER);

		// ── South: action buttons ──────────────────────────────────────────────
		content.add(buildActionRow(), BorderLayout.SOUTH);

		return content;
	}

	private JPanel buildActionRow() {
		JPanel row = new JPanel(new BorderLayout(8, 0));
		Color sep = UIManager.getColor("Separator.foreground");
		if (sep == null) sep = Color.LIGHT_GRAY;
		row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, sep),
				BorderFactory.createEmptyBorder(8, 12, 10, 12)));

		// Left side: Skip / Disable buttons
		JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		leftButtons.setOpaque(false);

		JButton skipButton = new JButton("Skip This Version");
		skipButton.addActionListener(e -> {
			String latestTag = plan.getReleases().get(plan.getReleases().size() - 1).getTagName();
			String version = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
			settings.setSkippedVersion(version);
			dispose();
		});
		leftButtons.add(skipButton);

		JButton disableButton = new JButton("Disable Update Checker");
		disableButton.addActionListener(e -> {
			settings.setUpdateCheckEnabled(false);
			dispose();
		});
		leftButtons.add(disableButton);

		row.add(leftButtons, BorderLayout.WEST);

		// Right side: Later button + split install button
		JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		rightButtons.setOpaque(false);

		JButton laterButton = new JButton("Later");
		laterButton.addActionListener(e -> dispose());
		rightButtons.add(laterButton);

		SplitInstallButton installButton = new SplitInstallButton(plan.getReleases(), this::startInstall);
		rightButtons.add(installButton);

		row.add(rightButtons, BorderLayout.EAST);

		return row;
	}

	private void startInstall(ReleaseInfo release) {
		ArtifactSelector selector = new ArtifactSelector();
		java.util.Optional<AssetInfo> assetOpt = selector.select(release, Platform.current(),
				DistributionMode.current());

		if (!assetOpt.isPresent()) {
			JOptionPane.showMessageDialog(this,
					"No suitable download was found for your platform (" + Platform.current().name().toLowerCase()
							+ ", " + DistributionMode.current().name().toLowerCase() + ").\n"
							+ "Please download the update manually from the releases page.",
					"No Asset Found", JOptionPane.WARNING_MESSAGE);
			return;
		}

		AssetInfo asset = assetOpt.get();
		dispose(); // close update dialog before showing download dialog

		UpdateDownloadDialog downloadDialog = new UpdateDownloadDialog(getOwner() != null ? getOwner() : null,
				asset.getName());

		new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground() throws Exception {
				UpdateInstaller installer = new UpdateInstaller();
				installer.install(asset, Platform.current(), DistributionMode.current(), downloadDialog.getListener(),
						downloadDialog);
				return null;
			}

			@Override
			protected void done() {
				try {
					get();
					// If we're still here after install(), the JVM did not exit
					// (e.g. Linux DEB manual install path). Mark success so the dialog
					// is informative rather than showing "Downloading…" forever.
					SwingUtilities.invokeLater(new Runnable() {

						@Override
						public void run() {
							downloadDialog.markSuccess();
						}

					});
				} catch (Exception ex) {
					final Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
					SwingUtilities.invokeLater(new Runnable() {

						@Override
						public void run() {
							downloadDialog.markFailed(cause.getMessage());
						}

					});
				}
			}

		}.execute();

		downloadDialog.setVisible(true); // blocks until user clicks OK
	}

	// ── Changelog HTML builder ─────────────────────────────────────────────

	/**
	 * Builds an HTML page from the list of releases. Releases are shown newest first. Version titles are rendered as
	 * hyperlinks to the corresponding GitHub release tag page.
	 */
	private static String buildChangelogHtml(List<ReleaseInfo> releases) {
		StringBuilder html = new StringBuilder();
		html.append("<html><head><style>");
		html.append("body { font-family: sans-serif; font-size: 11px; margin: 0; padding: 4px 8px; }");
		html.append("h1 { font-size: 13px; margin: 12px 0 4px 0; color: #222; font-weight: bold; }");
		html.append("h2 { font-size: 12px; margin: 10px 0 3px 0; color: #333; font-weight: bold; }");
		html.append("h3 { font-size: 11px; margin: 8px 0 2px 0; color: #444; font-weight: bold; }");
		html.append("h4, h5, h6 { font-size: 11px; margin: 6px 0 2px 0; color: #555; }");
		html.append("pre { font-family: monospace; background: #f4f4f4; padding: 6px 8px; margin: 4px 0; }");
		html.append("code { font-family: monospace; background: #f4f4f4; padding: 1px 3px; }");
		html.append("hr { border: none; border-top: 1px solid #ddd; margin: 10px 0; }");
		html.append("ul, ol { margin: 2px 0 4px 18px; padding: 0; }");
		html.append("li { margin: 1px 0; }");
		html.append("p { margin: 3px 0; }");
		html.append("a { color: #0366d6; text-decoration: none; }");
		html.append("a:hover { text-decoration: underline; }");
		html.append(".release-title { font-size: 13px; font-weight: bold; margin: 12px 0 4px 0; color: #111; }");
		html.append("</style></head><body>");

		// Newest first
		for (int i = releases.size() - 1; i >= 0; i--) {
			ReleaseInfo release = releases.get(i);
			String tagName = release.getTagName();
			String title = release.getName().isEmpty() ? tagName : escapeHtml(release.getName());
			String tagUrl = buildTagUrl(tagName);

			html.append("<p class=\"release-title\"><a href=\"").append(escapeHtml(tagUrl)).append("\">").append(title)
					.append("</a></p>");

			String body = release.getBody();
			if (body != null && !body.isEmpty()) {
				html.append(markdownToHtml(body));
			} else {
				html.append("<p><i>No changelog provided.</i></p>");
			}

			if (i > 0) {
				html.append("<hr>");
			}
		}

		html.append("</body></html>");
		return html.toString();
	}

	/**
	 * Constructs the GitHub releases tag URL for the given tag name. Returns {@code "#"} when the GitHub repository is
	 * not configured.
	 */
	private static String buildTagUrl(String tagName) {
		String repo = LauncherVersion.GITHUB_REPO;
		if (repo == null || repo.isEmpty() || repo.startsWith("${")) {
			return "#";
		}
		return "https://github.com/" + repo + "/releases/tag/" + tagName;
	}

	/**
	 * Converts a CommonMark Markdown string to HTML using the commonmark-java library.
	 */
	static String markdownToHtml(String markdown) {
		if (markdown == null || markdown.isEmpty()) return "";
		Node document = MARKDOWN_PARSER.parse(markdown);
		return HTML_RENDERER.render(document);
	}

	private static String escapeHtml(String text) {
		if (text == null) return "";
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

}
