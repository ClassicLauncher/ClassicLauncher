package net.classiclauncher.launcher.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.*;

/**
 * A small, borderless button that displays a single Unicode glyph (e.g. ⚙, ⋮, ✕).
 *
 * <p>
 * Supports two visual modes:
 * <ul>
 * <li><b>Transparent</b> — no background; the glyph floats over whatever is underneath. Created with
 * {@link #IconButton(String, String)} or by passing {@code null} colours.</li>
 * <li><b>Filled</b> — opaque background that changes on hover. Created with
 * {@link #IconButton(String, String, Color, Color)}.</li>
 * </ul>
 *
 * <p>
 * Both modes suppress focus painting, border painting, and content-area painting, set the cursor to
 * {@link Cursor#HAND_CURSOR}, and use a configurable font size (default 13f plain). Call
 * {@link #withFontStyle(int, float)} to customise.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * // Transparent gear button
 * IconButton gear = new IconButton("\u2699", "Settings");
 * gear.withSize(24, 16);
 * gear.addActionListener(e -> openSettings());
 *
 * // Filled kebab button with hover
 * IconButton kebab = new IconButton("\u22EE", "Options", new Color(0xE4E4E4), new Color(0xCCCCCC));
 * kebab.withFontStyle(Font.BOLD, 14f).withSize(24, 24);
 * kebab.addActionListener(e -> showMenu(kebab));
 * }</pre>
 */
public class IconButton extends JButton {

	private final Color defaultBg;
	private final Color hoverBg;

	/**
	 * Creates a transparent icon button (no background).
	 *
	 * @param glyph
	 *            the Unicode character to display (e.g. {@code "\u2699"})
	 * @param tooltip
	 *            tooltip text shown on hover
	 */
	public IconButton(String glyph, String tooltip) {
		this(glyph, tooltip, null, null);
	}

	/**
	 * Creates an icon button with an opaque background that changes on hover.
	 *
	 * @param glyph
	 *            the Unicode character to display
	 * @param tooltip
	 *            tooltip text shown on hover
	 * @param defaultBg
	 *            background colour at rest ({@code null} for transparent)
	 * @param hoverBg
	 *            background colour on hover ({@code null} for transparent)
	 */
	public IconButton(String glyph, String tooltip, Color defaultBg, Color hoverBg) {
		super(glyph);
		this.defaultBg = defaultBg;
		this.hoverBg = hoverBg;

		setToolTipText(tooltip);
		setFont(getFont().deriveFont(Font.PLAIN, 13f));
		setMargin(new Insets(0, 2, 0, 2));
		setFocusPainted(false);
		setBorderPainted(false);
		setContentAreaFilled(false);
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		boolean filled = defaultBg != null;
		setOpaque(filled);
		if (filled) {
			setBackground(defaultBg);
		}

		addMouseListener(new MouseAdapter() {

			@Override
			public void mouseEntered(MouseEvent e) {
				if (hoverBg != null) {
					setBackground(hoverBg);
				}
			}

			@Override
			public void mouseExited(MouseEvent e) {
				if (IconButton.this.defaultBg != null) {
					setBackground(IconButton.this.defaultBg);
				}
			}

		});
	}

	/**
	 * Sets the preferred and maximum size of this button.
	 *
	 * @return this button, for chaining
	 */
	public IconButton withSize(int width, int height) {
		Dimension d = new Dimension(width, height);
		setPreferredSize(d);
		setMaximumSize(d);
		return this;
	}

	/**
	 * Overrides the font style and size.
	 *
	 * @param style
	 *            one of {@link Font#PLAIN}, {@link Font#BOLD}, {@link Font#ITALIC}
	 * @param size
	 *            point size
	 * @return this button, for chaining
	 */
	public IconButton withFontStyle(int style, float size) {
		setFont(getFont().deriveFont(style, size));
		return this;
	}

}
