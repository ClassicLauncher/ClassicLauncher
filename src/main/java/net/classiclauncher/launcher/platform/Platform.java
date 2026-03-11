package net.classiclauncher.launcher.platform;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Represents the host operating system, with its associated set of {@link PlatformCapability capabilities}.
 *
 * <p>
 * Obtain the current platform via {@link #current()}:
 *
 * <pre>{@code
 * Platform os = Platform.current();
 * if (os.hasCapability(PlatformCapability.NATIVE_EXE)) { ... }
 * }</pre>
 */
public enum Platform {

	WINDOWS(EnumSet.of(PlatformCapability.NATIVE_EXE, PlatformCapability.WINDOWS_REGISTRY)),

	MACOS(EnumSet.of(PlatformCapability.APP_BUNDLE, PlatformCapability.SHELL_SCRIPT,
			PlatformCapability.POSIX_PERMISSIONS)),

	LINUX(EnumSet.of(PlatformCapability.SHELL_SCRIPT, PlatformCapability.POSIX_PERMISSIONS,
			PlatformCapability.SYSTEM_PACKAGE_MANAGER)),

	/**
	 * Unrecognised OS — no capabilities assumed.
	 */
	UNKNOWN(EnumSet.noneOf(PlatformCapability.class));

	// -------------------------------------------------------------------------

	private static final Platform CURRENT = detect();

	private final Set<PlatformCapability> capabilities;

	Platform(Set<PlatformCapability> capabilities) {
		this.capabilities = Collections.unmodifiableSet(capabilities);
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/**
	 * Returns the platform for the current JVM host, detected once at class-load time.
	 */
	public static Platform current() {
		return CURRENT;
	}

	/**
	 * Returns {@code true} if this platform supports the given capability.
	 */
	public boolean hasCapability(PlatformCapability capability) {
		return capabilities.contains(capability);
	}

	/**
	 * Returns the full, unmodifiable set of capabilities for this platform.
	 */
	public Set<PlatformCapability> getCapabilities() {
		return capabilities;
	}

	// -------------------------------------------------------------------------
	// Detection
	// -------------------------------------------------------------------------

	private static Platform detect() {
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("win")) return WINDOWS;
		if (os.contains("mac")) return MACOS;
		if (os.contains("linux") || os.contains("nix") || os.contains("nux")) return LINUX;
		return UNKNOWN;
	}

}
