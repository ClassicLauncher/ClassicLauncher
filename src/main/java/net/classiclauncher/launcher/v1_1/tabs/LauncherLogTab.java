package net.classiclauncher.launcher.v1_1.tabs;

import java.awt.*;

import javax.swing.*;

/**
 * Tab that displays launcher output log in a scrollable, non-editable text area.
 */
public class LauncherLogTab extends JPanel {

	private final JTextArea textArea;

	public LauncherLogTab() {
		super(new BorderLayout());

		textArea = new JTextArea();
		textArea.setEditable(false);
		textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		textArea.setBackground(new Color(0x1E1E1E));
		textArea.setForeground(new Color(0xC8C8C8));
		textArea.setCaretColor(new Color(0xC8C8C8));

		JScrollPane scrollPane = new JScrollPane(textArea);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Appends a line of text and auto-scrolls to the bottom. Safe to call from any thread.
	 */
	public void appendLine(String line) {
		SwingUtilities.invokeLater(() -> {
			textArea.append(line + "\n");
			textArea.setCaretPosition(textArea.getDocument().getLength());
		});
	}

	/**
	 * Clears all log content. Safe to call from any thread.
	 */
	public void clear() {
		SwingUtilities.invokeLater(() -> textArea.setText(""));
	}

}
