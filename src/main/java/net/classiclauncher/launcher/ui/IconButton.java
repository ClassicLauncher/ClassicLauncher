package net.classiclauncher.launcher.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.*;

/**
 * A small, borderless button that displays either a Swing {@link Icon} (typically an {@link SvgIcon}) or a single
 * Unicode glyph as a fallback.
 *
 * <p>
 * Supports two visual modes:
 * <ul>
 * <li><b>Transparent</b> — no background; the icon floats over whatever is underneath. Created with
 * {@link #IconButton(Icon, String)} or by passing {@code null} colours.</li>
 * <li><b>Filled</b> — opaque background that changes on hover. Created with
 * {@link #IconButton(Icon, String, Color, Color)}.</li>
 * </ul>
 *
 * <p>
 * Both modes suppress focus painting, border painting, and content-area painting, set the cursor to
 * {@link Cursor#HAND_CURSOR}, and centre the icon within the button bounds.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * // SVG gear button
 * IconButton gear = new IconButton(new GearIcon(14), "Settings").withSize(24, 16);
 * gear.addActionListener(e -> openSettings());
 *
 * // SVG dots button with hover background
 * IconButton kebab = new IconButton(new DotsIcon(14), "Options", new Color(0xE4E4E4), new Color(0xCCCCCC));
 * kebab.withSize(24, 24);
 * kebab.addActionListener(e -> showMenu(kebab));
 * }</pre>
 */
public class IconButton extends JButton {

	private final Color defaultBg;
	private final Color hoverBg;

	/**
	 * Creates a transparent icon button using a Swing {@link Icon}.
	 *
	 * @param icon
	 *            the icon to display (e.g. {@link GearIcon}, {@link DotsIcon})
	 * @param tooltip
	 *            tooltip text shown on hover
	 */
	public IconButton(Icon icon, String tooltip) {
		this(icon, tooltip, null, null);
	}

	/**
	 * Creates an icon button with a Swing {@link Icon} and an opaque background that changes on hover.
	 *
	 * @param icon
	 *            the icon to display
	 * @param tooltip
	 *            tooltip text shown on hover
	 * @param defaultBg
	 *            background colour at rest ({@code null} for transparent)
	 * @param hoverBg
	 *            background colour on hover ({@code null} for transparent)
	 */
	public IconButton(Icon icon, String tooltip, Color defaultBg, Color hoverBg) {
		super();
		this.defaultBg = defaultBg;
		this.hoverBg = hoverBg;
		setIcon(icon);
		init(tooltip);
	}

	/**
	 * Creates a transparent icon button using a Unicode glyph.
	 *
	 * @param glyph
	 *            the Unicode character to display (e.g. {@code "\u2699"})
	 * @param tooltip
	 *            tooltip text shown on hover
	 * @deprecated use {@link #IconButton(Icon, String)} with an {@link SvgIcon} subclass instead
	 */
	@Deprecated
	public IconButton(String glyph, String tooltip) {
		this(glyph, tooltip, null, null);
	}

	/**
	 * Creates an icon button with a Unicode glyph and an opaque background that changes on hover.
	 *
	 * @param glyph
	 *            the Unicode character to display
	 * @param tooltip
	 *            tooltip text shown on hover
	 * @param defaultBg
	 *            background colour at rest ({@code null} for transparent)
	 * @param hoverBg
	 *            background colour on hover ({@code null} for transparent)
	 * @deprecated use {@link #IconButton(Icon, String, Color, Color)} with an {@link SvgIcon} subclass instead
	 */
	@Deprecated
	public IconButton(String glyph, String tooltip, Color defaultBg, Color hoverBg) {
		super(glyph);
		this.defaultBg = defaultBg;
		this.hoverBg = hoverBg;
		setFont(getFont().deriveFont(Font.PLAIN, 13f));
		init(tooltip);
	}

	private void init(String tooltip) {
		setToolTipText(tooltip);
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
	 * Overrides the font style and size. Only meaningful for glyph-based buttons.
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
