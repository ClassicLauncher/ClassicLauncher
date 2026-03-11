package net.classiclauncher.launcher;

import java.io.File;

import lombok.Getter;
import lombok.Setter;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.settings.Settings;

/**
 * Holds the identity and data directory of this launcher instance.
 * <p>
 * Call {@link #initialize(String)} at startup (before {@link Settings#getInstance()}) to set the launcher name. Forks
 * should replace the default name with their own.
 * <p>
 * The data directory is resolved per-platform: Windows : %APPDATA%\<name> macOS : ~/Library/Application Support/<name>
 * Linux : $XDG_CONFIG_HOME/<name> (falls back to ~/.config/<name>)
 */
@Getter
public class LauncherContext {

	private static LauncherContext instance;

	/**
	 * -- GETTER -- The human-readable launcher name (also the data directory name).
	 */
	private final String name;
	/**
	 * -- GETTER -- The root data directory for this launcher on the current platform.
	 */
	private final File dataDir;
	/**
	 * -- GETTER -- Returns the default for this launcher instance, or if none has been set. Individual s may override
	 * this on a per-account-type basis via
	 * <p>
	 * . -- SETTER -- Sets the default game for this launcher instance. Call this in after .
	 *
	 * <pre>
	 * </pre>
	 */
	@Setter
	private Game defaultGame;

	private LauncherContext(String name) {
		this.name = name;
		this.dataDir = resolvePlatformDataDir(name);
	}

	/**
	 * Initializes (or re-initializes) the launcher context with the given name. Must be called before
	 * {@link Settings#getInstance()}.
	 */
	public static void initialize(String launcherName) {
		instance = new LauncherContext(launcherName);
	}

	/**
	 * Returns the current context, initializing with the default name "launcher" if needed.
	 */
	public static LauncherContext getInstance() {
		if (instance == null) instance = new LauncherContext("launcher");
		return instance;
	}

	private static File resolvePlatformDataDir(String name) {
		String os = System.getProperty("os.name", "").toLowerCase();
		if (os.contains("win")) {
			String appData = System.getenv("APPDATA");
			return new File(appData != null ? appData : userHome(), name);
		} else if (os.contains("mac")) {
			return new File(userHome(), "Library/Application Support/" + name);
		} else {
			// Linux / BSD / other: respect XDG Base Directory spec
			String xdg = System.getenv("XDG_CONFIG_HOME");
			if (xdg != null && !xdg.isEmpty()) return new File(xdg, name);
			return new File(userHome(), ".config/" + name);
		}
	}

	private static String userHome() {
		return System.getProperty("user.home");
	}

	/**
	 * Resolves a path relative to the data directory. Example: {@code resolve("accounts")} → {@code <dataDir>/accounts}
	 */
	public File resolve(String... segments) {
		File dir = dataDir;
		for (String segment : segments)
			dir = new File(dir, segment);
		return dir;
	}

}
