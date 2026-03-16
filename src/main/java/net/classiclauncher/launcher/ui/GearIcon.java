package net.classiclauncher.launcher.ui;

import java.awt.*;

/**
 * SVG gear/settings icon loaded from {@code /ui/gear.svg}.
 *
 * <p>
 * Used for settings buttons in the game selector widget and anywhere else a gear indicator is needed.
 *
 * @see SvgIcon
 */
public class GearIcon extends SvgIcon {

	private static final String RESOURCE = "/ui/gear.svg";

	/**
	 * Creates a square gear icon at the given size using the SVG's native fill.
	 *
	 * @param size
	 *            icon width and height in pixels
	 */
	public GearIcon(int size) {
		super(RESOURCE, size, size);
	}

	/**
	 * Creates a square gear icon at the given size with a custom colour.
	 *
	 * @param size
	 *            icon width and height in pixels
	 * @param color
	 *            foreground colour
	 */
	public GearIcon(int size, Color color) {
		super(RESOURCE, size, size, color);
	}

}
