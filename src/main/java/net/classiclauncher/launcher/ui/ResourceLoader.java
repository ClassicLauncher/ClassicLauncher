package net.classiclauncher.launcher.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.*;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.SVGLoader;

import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.settings.LauncherStyle;

/**
 * Shared utility for loading SVG and raster image assets from the classpath.
 *
 * <p>
 * SVG files are rendered via JSVG at the requested pixel size. All other formats are decoded via {@link ImageIO} and
 * scaled with {@link Image#SCALE_SMOOTH}. Returns {@code null} (never throws) when the resource is absent or
 * unreadable.
 *
 * <h3>Automatic game-asset resolution</h3> Game logos and icons follow a fixed two-tier convention — no path needs to
 * be configured:
 * <ol>
 * <li><b>Provider override</b> (checked first):
 * {@code /assets/providers/{typeId}/games/{gameId}/style/{styleName}/{asset}.{svg|png}}</li>
 * <li><b>Game default</b> (fallback): {@code /assets/games/{gameId}/style/{styleName}/{asset}.{svg|png}}</li>
 * </ol>
 * {@code typeId} is the provider's type identifier lowercased (e.g. {@code microsoft}). {@code styleName} is the
 * {@link LauncherStyle} constant name lowercased (e.g. {@code v1_1}).
 *
 * <p>
 * Both tiers search SVG before PNG. Resources are located via the Launcher's classloader first, then the calling
 * thread's context classloader — this makes extension-bundled assets transparently discoverable without any change to
 * call sites.
 *
 * <p>
 * Use {@link #resolveGameLogoPath} / {@link #resolveGameIconPath} to obtain the first available path for a
 * game+provider+style combination, then pass it to {@link #loadIcon}.
 */
public final class ResourceLoader {

	/**
	 * Preferred extension order when probing for a game asset. SVG wins over PNG.
	 */
	private static final String[] ASSET_EXTENSIONS = {".svg", ".png"};

	private ResourceLoader() {
	}

	/**
	 * Loads an icon (SVG or raster) from a classpath resource path, scaled to {@code w × h}.
	 *
	 * @param resourcePath
	 *            classpath path (e.g. {@code "/icons/offline.svg"}) or {@code null}
	 * @param width
	 *            desired width in pixels
	 * @param height
	 *            desired height in pixels
	 * @return a scaled {@link ImageIcon}, or {@code null} if loading fails or path is {@code null}
	 */
	public static ImageIcon loadIcon(String resourcePath, int width, int height) {
		if (resourcePath == null) return null;
		InputStream stream = openStream(resourcePath);
		if (stream == null) return null;
		try (InputStream autoClose = stream) {
			if (resourcePath.toLowerCase().endsWith(".svg")) {
				BufferedImage img = renderSvg(autoClose, width, height);
				return img != null ? new ImageIcon(img) : null;
			} else {
				BufferedImage raw = ImageIO.read(autoClose);
				if (raw == null) return null;
				Image scaled = raw.getScaledInstance(width, height, Image.SCALE_SMOOTH);
				return new ImageIcon(scaled);
			}
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Convenience overload for square icons.
	 *
	 * @param resourcePath
	 *            classpath path or {@code null}
	 * @param size
	 *            icon size in pixels (width = height)
	 */
	public static ImageIcon loadIcon(String resourcePath, int size) {
		return loadIcon(resourcePath, size, size);
	}

	/**
	 * Loads an icon (SVG or raster) from a {@link File} on disk, scaled to {@code size × size}. Used to load cached
	 * extension icons that have been extracted from extension JARs.
	 *
	 * @param file
	 *            the icon file on disk
	 * @param size
	 *            icon size in pixels (width = height)
	 * @return a scaled {@link ImageIcon}, or {@code null} if loading fails or file is {@code null}/absent
	 */
	public static ImageIcon loadIconFromFile(File file, int size) {
		if (file == null || !file.exists()) return null;
		try (InputStream stream = new FileInputStream(file)) {
			if (file.getName().toLowerCase().endsWith(".svg")) {
				BufferedImage img = renderSvg(stream, size, size);
				return img != null ? new ImageIcon(img) : null;
			} else {
				BufferedImage raw = ImageIO.read(stream);
				if (raw == null) return null;
				Image scaled = raw.getScaledInstance(size, size, Image.SCALE_SMOOTH);
				return new ImageIcon(scaled);
			}
		} catch (Exception e) {
			return null;
		}
	}

	// ── Game-asset path resolution (with provider override) ───────────────────

	/**
	 * Returns the classpath path for the wide logo asset of the given game and style, checking the provider override
	 * tier before the game default.
	 *
	 * <p>
	 * Resolution order (SVG preferred over PNG at each tier):
	 * <ol>
	 * <li>{@code /assets/providers/{typeId}/games/{gameId}/style/{styleName}/logo.{svg|png}}</li>
	 * <li>{@code /assets/games/{gameId}/style/{styleName}/logo.{svg|png}}</li>
	 * </ol>
	 *
	 * @param gameId
	 *            stable game identifier (from {@link Game#getGameId()})
	 * @param providerTypeId
	 *            type ID of the active provider (from {@link AccountProvider#getTypeId()}), or {@code null} to skip the
	 *            provider tier
	 * @param style
	 *            the active launcher style
	 * @return the first matching classpath path, or {@code null} if none exists
	 */
	public static String resolveGameLogoPath(String gameId, String providerTypeId, LauncherStyle style) {
		return resolveGameAssetPathWithProvider(gameId, providerTypeId, style, "logo");
	}

	/**
	 * Returns the classpath path for the small square icon of the given game and style, checking the provider override
	 * tier before the game default.
	 *
	 * <p>
	 * Resolution order (SVG preferred over PNG at each tier):
	 * <ol>
	 * <li>{@code /assets/providers/{typeId}/games/{gameId}/style/{styleName}/icon.{svg|png}}</li>
	 * <li>{@code /assets/games/{gameId}/style/{styleName}/icon.{svg|png}}</li>
	 * </ol>
	 *
	 * @param gameId
	 *            stable game identifier
	 * @param providerTypeId
	 *            type ID of the active provider, or {@code null} to skip the provider tier
	 * @param style
	 *            the active launcher style
	 * @return the first matching classpath path, or {@code null} if none exists
	 */
	public static String resolveGameIconPath(String gameId, String providerTypeId, LauncherStyle style) {
		return resolveGameAssetPathWithProvider(gameId, providerTypeId, style, "icon");
	}

	/**
	 * Convenience overload — resolves the game logo without a provider override tier.
	 *
	 * @param gameId
	 *            stable game identifier
	 * @param style
	 *            active launcher style
	 * @return first matching classpath path, or {@code null}
	 */
	public static String resolveGameLogoPath(String gameId, LauncherStyle style) {
		return resolveGameAssetPath(gameId, style, "logo");
	}

	/**
	 * Convenience overload — resolves the game icon without a provider override tier.
	 *
	 * @param gameId
	 *            stable game identifier
	 * @param style
	 *            active launcher style
	 * @return first matching classpath path, or {@code null}
	 */
	public static String resolveGameIconPath(String gameId, LauncherStyle style) {
		return resolveGameAssetPath(gameId, style, "icon");
	}

	/**
	 * Probes for a game+provider asset using the two-tier resolution order.
	 *
	 * @param gameId
	 *            stable game identifier
	 * @param providerTypeId
	 *            provider type ID (lowercased before use), or {@code null}
	 * @param style
	 *            active launcher style
	 * @param assetName
	 *            base file name without extension ({@code "logo"} or {@code "icon"})
	 * @return matching classpath path, or {@code null} if neither tier has the asset
	 */
	private static String resolveGameAssetPathWithProvider(String gameId, String providerTypeId, LauncherStyle style,
			String assetName) {
		if (gameId == null || style == null || assetName == null) return null;
		// Tier 1: provider override
		if (providerTypeId != null && !providerTypeId.isEmpty()) {
			String providerBase = "/assets/providers/" + providerTypeId.toLowerCase() + "/games/" + gameId + "/style/"
					+ style.name().toLowerCase() + "/" + assetName;
			String path = resolveFirstExisting(providerBase, ASSET_EXTENSIONS);
			if (path != null) return path;
		}
		// Tier 2: game default
		return resolveGameAssetPath(gameId, style, assetName);
	}

	/**
	 * Probes the classpath for {@code /assets/games/{gameId}/style/{styleName}/{assetName}.svg} then {@code .png},
	 * returning the first path whose resource exists.
	 *
	 * @param gameId
	 *            stable game identifier
	 * @param style
	 *            active launcher style
	 * @param assetName
	 *            base file name without extension ({@code "logo"}, {@code "icon"})
	 * @return matching classpath path, or {@code null} if neither extension is present
	 */
	public static String resolveGameAssetPath(String gameId, LauncherStyle style, String assetName) {
		if (gameId == null || style == null || assetName == null) return null;
		String base = "/assets/games/" + gameId + "/style/" + style.name().toLowerCase() + "/" + assetName;
		return resolveFirstExisting(base, ASSET_EXTENSIONS);
	}

	/**
	 * Probes each extension in order and returns the first {@code base+ext} path that exists on any visible
	 * classloader, or {@code null} if none are found.
	 */
	private static String resolveFirstExisting(String base, String[] extensions) {
		for (String ext : extensions) {
			String path = base + ext;
			if (resourceExists(path)) return path;
		}
		return null;
	}

	/**
	 * Returns {@code true} if the given classpath resource exists on either the Launcher classloader or the calling
	 * thread's context classloader.
	 */
	private static boolean resourceExists(String path) {
		if (ResourceLoader.class.getResource(path) != null) return true;
		ClassLoader ctx = Thread.currentThread().getContextClassLoader();
		if (ctx == null) return false;
		String stripped = path.startsWith("/") ? path.substring(1) : path;
		return ctx.getResource(stripped) != null;
	}

	// ── Classloader-aware resource access ────────────────────────────────────

	/**
	 * Opens a classpath resource, trying the Launcher classloader first and then the calling thread's context
	 * classloader as a fallback.
	 *
	 * <p>
	 * The context-classloader fallback is what makes extension-bundled resources visible: the extension loader sets
	 * itself as the thread context classloader before invoking extension code, so resources inside extension JARs are
	 * resolved transparently without any change to call sites.
	 *
	 * <p>
	 * This method is intentionally {@code public} so that components that paint resources directly (e.g.
	 * {@link GameSelectorWidget}) can use the same dual-classloader lookup.
	 *
	 * @param resourcePath
	 *            absolute classpath path (e.g. {@code "/icons/aether-online.svg"})
	 * @return an open {@link InputStream}, or {@code null} if the resource is not found
	 */
	public static InputStream openStream(String resourcePath) {
		InputStream stream = ResourceLoader.class.getResourceAsStream(resourcePath);
		if (stream != null) {
			System.out.println("[ResourceLoader] Loaded from Launcher classloader: " + resourcePath);
			return stream;
		}
		ClassLoader ctx = Thread.currentThread().getContextClassLoader();
		if (ctx == null) {
			System.out.println("[ResourceLoader] Not found and context classloader is null: " + resourcePath);
			return null;
		}
		// ClassLoader.getResourceAsStream never wants a leading slash
		String stripped = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
		stream = ctx.getResourceAsStream(stripped);
		if (stream != null) {
			System.out.println("[ResourceLoader] Loaded from context classloader: " + resourcePath);
		} else {
			System.out.println("[ResourceLoader] Not found in any classloader: " + resourcePath + " (ctx="
					+ ctx.getClass().getName() + ")");
		}
		return stream;
	}

	// ── SVG rendering ─────────────────────────────────────────────────────────

	/**
	 * Renders an SVG {@link InputStream} into a {@link BufferedImage} at the given size.
	 *
	 * @param stream
	 *            input stream for the SVG document (not closed by this method)
	 * @param width
	 *            target width in pixels
	 * @param height
	 *            target height in pixels
	 * @return rendered image, or {@code null} if loading fails
	 */
	public static BufferedImage renderSvg(InputStream stream, int width, int height) {
		if (stream == null) return null;
		try {
			SVGLoader loader = new SVGLoader();
			SVGDocument doc = loader.load(stream);
			if (doc == null) return null;
			BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = img.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			doc.render((javax.swing.JComponent) null, g2, new ViewBox(0, 0, width, height));
			g2.dispose();
			return img;
		} catch (Exception e) {
			return null;
		}
	}

}
