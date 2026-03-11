package net.classiclauncher.launcher.v1_1.tabs;

import java.awt.*;
import java.net.URL;

import javax.swing.*;
import javax.swing.text.html.HTMLEditorKit;

import net.classiclauncher.launcher.settings.LauncherSettings;

/**
 * Tab that renders update notes from a remote URL as HTML.
 */
public class UpdateNotesTab extends JPanel {

	private final JEditorPane editorPane;

	private UpdateNotesTab(String notesUrl) {
		super(new BorderLayout());

		editorPane = new JEditorPane();
		editorPane.setEditorKit(new HTMLEditorKit());
		editorPane.setEditable(false);
		editorPane.setBackground(new Color(0x1E1E1E));

		JScrollPane scrollPane = new JScrollPane(editorPane);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		add(scrollPane, BorderLayout.CENTER);

		if (notesUrl == null || notesUrl.isEmpty()) {
			editorPane.setContentType("text/html");
			editorPane.setText("<html><body style='background:#1E1E1E;color:#C8C8C8;font-family:sans-serif;"
					+ "padding:16px'><p>No update notes URL configured.</p></body></html>");
		} else {
			loadUrl(notesUrl);
		}
	}

	private void loadUrl(String notesUrl) {
		new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground() throws Exception {
				URL url = new URL(notesUrl);
				SwingUtilities.invokeLater(() -> {
					try {
						editorPane.setPage(url);
					} catch (Exception ex) {
						editorPane.setContentType("text/html");
						editorPane.setText("<html><body style='background:#1E1E1E;color:#C8C8C8;"
								+ "font-family:sans-serif;padding:16px'>" + "<p>Failed to load update notes: "
								+ ex.getMessage() + "</p>" + "</body></html>");
					}
				});
				return null;
			}

		}.execute();
	}

	/**
	 * Creates a tab that loads the given URL. Pass {@code null} or an empty string to show the "not configured"
	 * placeholder.
	 */
	public static UpdateNotesTab create(String url) {
		return new UpdateNotesTab(url);
	}

	/**
	 * Creates a tab using the URL stored in {@code settings.yml}.
	 */
	public static UpdateNotesTab create(LauncherSettings settings) {
		return new UpdateNotesTab(settings.getUpdateNotesUrl());
	}

}
