package net.classiclauncher.launcher.ui;

import java.awt.*;

/**
 * SVG dots icon loaded from {@code /ui/dots.svg}.
 *
 * <p>
 * Used for kebab/options menu buttons. The icon shows three dots rendered from the SVG asset.
 *
 * @see SvgIcon
 */
public class DotsIcon extends SvgIcon {

	private static final String RESOURCE = "/ui/dots.svg";

	/**
	 * Creates a square dots icon at the given size using the SVG's native fill.
	 *
	 * @param size
	 *            icon width and height in pixels
	 */
	public DotsIcon(int size) {
		super(RESOURCE, size, size);
	}

	/**
	 * Creates a square dots icon at the given size with a custom colour.
	 *
	 * @param size
	 *            icon width and height in pixels
	 * @param color
	 *            foreground colour
	 */
	public DotsIcon(int size, Color color) {
		super(RESOURCE, size, size, color);
	}

}
