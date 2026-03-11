package net.classiclauncher.launcher.ui.font;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ordered list of preferred font families for a single platform.
 *
 * <p>
 * The first family that is physically installed on the system is used. Use {@link #SYSTEM_DEFAULT} to explicitly
 * request no override.
 *
 * <p>
 * Create instances via the static factories:
 *
 * <pre>{@code
 * PlatformFont.of("Segoe UI", "Tahoma")   // try Segoe UI, fall back to Tahoma
 * PlatformFont.SYSTEM_DEFAULT              // leave the L&F font untouched
 * }</pre>
 */
public final class PlatformFont {

	/**
	 * Sentinel: apply no override — let the L&amp;F choose the font.
	 */
	public static final PlatformFont SYSTEM_DEFAULT = new PlatformFont(Collections.emptyList());

	private final List<String> families;

	private PlatformFont(List<String> families) {
		this.families = families;
	}

	/**
	 * Creates a {@code PlatformFont} with the given preferred families, tried in order.
	 *
	 * @param families
	 *            one or more font family names in descending preference
	 */
	public static PlatformFont of(String... families) {
		if (families == null || families.length == 0) {
			throw new IllegalArgumentException("At least one font family must be provided");
		}
		return new PlatformFont(Arrays.asList(families));
	}

	/**
	 * Resolves to the first family from the preference list that is installed on this machine, or {@code null} if none
	 * match (or this is {@link #SYSTEM_DEFAULT}).
	 */
	public String resolve() {
		if (families.isEmpty()) {
			return null;
		}
		Set<String> installed = Arrays
				.stream(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
				.collect(Collectors.toSet());
		return families.stream().filter(installed::contains).findFirst().orElse(null);
	}

}
