package net.classiclauncher.launcher.ui;

import java.awt.*;

/**
 * SVG star icon loaded from {@code /ui/star.svg}.
 *
 * <p>
 * Used in the Java settings table to mark the default JRE installation, and anywhere else a star indicator is needed.
 *
 * @see SvgIcon
 */
public class StarIcon extends SvgIcon {

	private static final String RESOURCE = "/ui/star.svg";

	/**
	 * Creates a square star icon at the given size using the SVG's native fill.
	 *
	 * @param size
	 *            icon width and height in pixels
	 */
	public StarIcon(int size) {
		super(RESOURCE, size, size);
	}

	/**
	 * Creates a square star icon at the given size with a custom colour.
	 *
	 * @param size
	 *            icon width and height in pixels
	 * @param color
	 *            foreground colour
	 */
	public StarIcon(int size, Color color) {
		super(RESOURCE, size, size, color);
	}

}
