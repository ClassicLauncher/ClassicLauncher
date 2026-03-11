package net.classiclauncher.launcher.ui;

import java.awt.*;

import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.game.Game;

/**
 * Paints a background onto a panel surface.
 *
 * <p>
 * Extensions provide implementations (e.g. tiled images, gradients) via {@link Game#createBackgroundRenderer} or
 * {@link AccountProvider#getBackgroundRenderer}. When no renderer is set, the launcher falls back to a solid
 * {@code #1E1E1E} fill.
 */
@FunctionalInterface
public interface BackgroundRenderer {

	/**
	 * Paints the background onto the given graphics context.
	 *
	 * <p>
	 * The caller creates a defensive copy of the graphics context before invoking this method, so implementations may
	 * freely modify rendering hints, transforms, or composites without affecting sibling paint operations.
	 *
	 * @param g
	 *            the graphics context to paint on (caller-owned copy; disposed by caller)
	 * @param width
	 *            the panel width in pixels
	 * @param height
	 *            the panel height in pixels
	 */
	void paint(Graphics2D g, int width, int height);

}
