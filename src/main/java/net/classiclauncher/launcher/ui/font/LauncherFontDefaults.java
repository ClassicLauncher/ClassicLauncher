package net.classiclauncher.launcher.ui.font;

import net.classiclauncher.launcher.platform.Platform;
import net.classiclauncher.launcher.settings.LauncherStyle;

/**
 * Central registry of default UI fonts for each {@link LauncherStyle}.
 *
 * <p>
 * Each style maps to a {@link PlatformFonts} instance that encodes the preferred font hierarchy per operating system.
 * Call {@link #forStyle(LauncherStyle)} immediately after the Look-and-Feel is installed, before any component is
 * created.
 *
 * <pre>{@code
 * applySystemLookAndFeel();
 * LauncherFontDefaults.forStyle(LauncherStyle.V1_1).applyToSwing();
 * }</pre>
 */
public final class LauncherFontDefaults {

	private LauncherFontDefaults() {
	}

	/**
	 * Returns the {@link PlatformFonts} for the given launcher style.
	 *
	 * <p>
	 * Font selection rationale:
	 * <ul>
	 * <li><b>ALPHA</b> (2010 era) — Tahoma was the Windows XP system font; Segoe UI is the fallback for Vista+. macOS
	 * used Lucida Grande throughout.</li>
	 * <li><b>V1_1</b> (2013 era) — Segoe UI was standard on Windows 7/8; Lucida Grande remained the macOS default until
	 * Yosemite.</li>
	 * </ul>
	 * Linux always uses the system default — never overridden.
	 *
	 * @param style
	 *            the active launcher style; must not be {@code null}
	 * @return a ready-to-apply {@link PlatformFonts} instance
	 * @throws IllegalArgumentException
	 *             for unknown future styles not yet handled
	 */
	public static PlatformFonts forStyle(LauncherStyle style) {
		if (style == null) throw new IllegalArgumentException("style must not be null");
		switch (style) {
			case ALPHA :
				return PlatformFonts.builder().font(Platform.WINDOWS, PlatformFont.of("Tahoma", "Segoe UI"))
						.font(Platform.MACOS, PlatformFont.of("Lucida Grande"))
						.font(Platform.LINUX, PlatformFont.SYSTEM_DEFAULT)
						.font(Platform.UNKNOWN, PlatformFont.SYSTEM_DEFAULT).build();
			case V1_1 :
				return PlatformFonts.builder().font(Platform.WINDOWS, PlatformFont.of("Segoe UI"))
						.font(Platform.MACOS, PlatformFont.of("Lucida Grande"))
						.font(Platform.LINUX, PlatformFont.SYSTEM_DEFAULT)
						.font(Platform.UNKNOWN, PlatformFont.SYSTEM_DEFAULT).build();
			default :
				throw new IllegalArgumentException("No font defaults defined for style: " + style);
		}
	}

}
