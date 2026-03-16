package net.classiclauncher.launcher.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.swing.*;

/**
 * A Swing {@link Icon} backed by an SVG resource on the classpath.
 *
 * <p>
 * The SVG is lazily rendered at the requested pixel dimensions via {@link ResourceLoader#renderSvg} and cached for
 * subsequent paints. An optional foreground {@link Color} can be applied — the SVG is expected to use {@code #000000}
 * fills, and tinting replaces the black with the requested colour using {@link AlphaComposite#SrcIn}.
 *
 * <p>
 * Subclasses typically fix the resource path and expose a convenient constructor:
 *
 * <pre>{@code
 * public class GearIcon extends SvgIcon {
 *
 * 	public GearIcon(int size) {
 * 		super("/ui/gear.svg", size, size);
 * 	}
 *
 * }
 * }</pre>
 *
 * @see StarIcon
 * @see DotsIcon
 * @see GearIcon
 */
public class SvgIcon implements Icon {

	private final String resourcePath;
	private final int width;
	private final int height;
	private final Color color;
	private BufferedImage cached;

	/**
	 * Creates an SVG icon rendered at the given size, using the SVG's native fill colour (typically black).
	 *
	 * @param resourcePath
	 *            absolute classpath path to the SVG file (e.g. {@code "/ui/gear.svg"})
	 * @param width
	 *            rendered width in pixels
	 * @param height
	 *            rendered height in pixels
	 */
	public SvgIcon(String resourcePath, int width, int height) {
		this(resourcePath, width, height, null);
	}

	/**
	 * Creates an SVG icon rendered at the given size with an optional foreground colour.
	 *
	 * @param resourcePath
	 *            absolute classpath path to the SVG file
	 * @param width
	 *            rendered width in pixels
	 * @param height
	 *            rendered height in pixels
	 * @param color
	 *            foreground colour to tint the icon, or {@code null} to keep the SVG's native fill
	 */
	public SvgIcon(String resourcePath, int width, int height, Color color) {
		this.resourcePath = resourcePath;
		this.width = width;
		this.height = height;
		this.color = color;
	}

	@Override
	public int getIconWidth() {
		return width;
	}

	@Override
	public int getIconHeight() {
		return height;
	}

	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		BufferedImage img = getImage();
		if (img != null) {
			g.drawImage(img, x, y, null);
		}
	}

	/**
	 * Returns the cached rendered image, loading and optionally tinting it on first access.
	 */
	private BufferedImage getImage() {
		if (cached != null) return cached;
		InputStream stream = ResourceLoader.openStream(resourcePath);
		if (stream == null) return null;
		try (InputStream autoClose = stream) {
			BufferedImage raw = ResourceLoader.renderSvg(autoClose, width, height);
			if (raw == null) return null;
			cached = color != null ? tint(raw, color) : raw;
			return cached;
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Replaces all opaque pixels in {@code source} with {@code color}, preserving the alpha channel.
	 */
	private static BufferedImage tint(BufferedImage source, Color color) {
		BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = tinted.createGraphics();
		g2.drawImage(source, 0, 0, null);
		g2.setComposite(AlphaComposite.SrcIn);
		g2.setColor(color);
		g2.fillRect(0, 0, source.getWidth(), source.getHeight());
		g2.dispose();
		return tinted;
	}

}
