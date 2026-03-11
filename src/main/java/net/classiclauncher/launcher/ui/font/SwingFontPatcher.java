package net.classiclauncher.launcher.ui.font;

import java.awt.*;

import javax.swing.*;

/**
 * Patches all Swing UIManager font defaults with a given font family, preserving each key's original style and point
 * size so that the L&amp;F's bold/italic hierarchy (table headers, titled borders, etc.) is maintained.
 */
public final class SwingFontPatcher {

	private SwingFontPatcher() {
	}

	/**
	 * Replaces every {@link Font} entry in {@link UIManager#getDefaults()} with the same style/size but the given
	 * {@code fontFamily}.
	 *
	 * @param fontFamily
	 *            the font family name to apply; {@code null} is a no-op
	 */
	public static void patch(String fontFamily) {
		if (fontFamily == null) {
			return;
		}
		UIManager.getDefaults().entrySet().stream().filter(e -> e.getValue() instanceof Font).forEach(e -> {
			Font original = (Font) e.getValue();
			UIManager.put(e.getKey(), new Font(fontFamily, original.getStyle(), original.getSize()));
		});
	}

}
