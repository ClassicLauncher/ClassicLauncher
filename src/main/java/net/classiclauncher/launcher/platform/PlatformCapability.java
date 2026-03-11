package net.classiclauncher.launcher.platform;

/**
 * Discrete capability that a platform may or may not support.
 *
 * <p>
 * Use {@link Platform#hasCapability(PlatformCapability)} to query at runtime.
 *
 * <pre>{@code
 * if (Platform.current().hasCapability(PlatformCapability.NATIVE_EXE)) {
 * 	runWindowsInstaller(path);
 * }
 * }</pre>
 */
public enum PlatformCapability {

	/**
	 * Platform can execute native Windows binaries ({@code .exe}, {@code .msi}). True only on {@link Platform#WINDOWS}.
	 */
	NATIVE_EXE,

	/**
	 * Platform can open macOS application bundles ({@code .app}) and disk images ({@code .dmg}) via the {@code open}
	 * command. True only on {@link Platform#MACOS}.
	 */
	APP_BUNDLE,

	/**
	 * Platform supports Unix shell scripts ({@code .sh}) and has a POSIX-compatible shell available at {@code /bin/sh}.
	 * True on {@link Platform#MACOS} and {@link Platform#LINUX}.
	 */
	SHELL_SCRIPT,

	/**
	 * Platform supports POSIX file permissions ({@code chmod}, {@code chown}). True on {@link Platform#MACOS} and
	 * {@link Platform#LINUX}.
	 */
	POSIX_PERMISSIONS,

	/**
	 * Platform provides access to the Windows Registry ({@code reg}, {@code regedit}, {@code Preferences} backed by
	 * HKCU on Windows). True only on {@link Platform#WINDOWS}.
	 */
	WINDOWS_REGISTRY,

	/**
	 * Platform has a well-known system package manager available (e.g. {@code apt}, {@code dnf}, {@code pacman} on
	 * Linux; {@code brew} on macOS is common but not guaranteed — check at runtime). True on {@link Platform#LINUX}.
	 */
	SYSTEM_PACKAGE_MANAGER,
}
