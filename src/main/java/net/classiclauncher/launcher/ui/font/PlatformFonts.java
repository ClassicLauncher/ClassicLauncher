package net.classiclauncher.launcher.ui.font;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import net.classiclauncher.launcher.platform.Platform;

/**
 * Per-platform font preferences. Build with {@link #builder()}, then call {@link #applyToSwing()} to install into the
 * Swing UIManager.
 *
 * <p>
 * Platforms with no registered preference (or mapped to {@link PlatformFont#SYSTEM_DEFAULT}) are left untouched.
 *
 * <pre>{@code
 * PlatformFonts.builder().font(Platform.WINDOWS, PlatformFont.of("Segoe UI"))
 * 		.font(Platform.MACOS, PlatformFont.of("Lucida Grande")).font(Platform.LINUX, PlatformFont.SYSTEM_DEFAULT)
 * 		.build().applyToSwing();
 * }</pre>
 */
public final class PlatformFonts {

	private final Map<Platform, PlatformFont> preferences;

	private PlatformFonts(Map<Platform, PlatformFont> preferences) {
		this.preferences = Collections.unmodifiableMap(new EnumMap<>(preferences));
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Resolves the font preference for the current OS via {@link Platform#current()} and patches all Swing UIManager
	 * font defaults via {@link SwingFontPatcher}.
	 *
	 * <p>
	 * No-ops if the current platform has no registered preference, or if its preference resolves to no installed font.
	 */
	public void applyToSwing() {
		PlatformFont preference = preferences.get(Platform.current());
		if (preference == null) {
			return;
		}
		SwingFontPatcher.patch(preference.resolve());
	}

	public static Builder builder() {
		return new Builder();
	}

	// -------------------------------------------------------------------------
	// Builder
	// -------------------------------------------------------------------------

	public static final class Builder {

		private final Map<Platform, PlatformFont> preferences = new EnumMap<>(Platform.class);

		private Builder() {
		}

		/**
		 * Registers a font preference for the given platform. Use {@link PlatformFont#SYSTEM_DEFAULT} to explicitly opt
		 * out of overriding.
		 *
		 * @param platform
		 *            the target OS
		 * @param font
		 *            the ordered font preferences for that OS
		 * @return this builder
		 */
		public Builder font(Platform platform, PlatformFont font) {
			if (platform == null) throw new IllegalArgumentException("platform must not be null");
			if (font == null) throw new IllegalArgumentException("font must not be null");
			preferences.put(platform, font);
			return this;
		}

		public PlatformFonts build() {
			return new PlatformFonts(preferences);
		}

	}

}
