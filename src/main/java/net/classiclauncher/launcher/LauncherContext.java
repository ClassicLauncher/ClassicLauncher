package net.classiclauncher.launcher;

import java.io.File;
import java.util.List;

import lombok.Getter;
import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.settings.LauncherSettings;
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
	 * The default game for this launcher instance, or {@code null} if none has been set. Set via
	 * {@link #setDefaultGame(Game)} which also persists the choice to {@link LauncherSettings}.
	 */
	private Game defaultGame;

	/**
	 * The type ID of the provider associated with the default game selection, or {@code null}. Persisted alongside the
	 * game ID so the correct provider is restored on restart when multiple providers offer the same game.
	 */
	private String defaultProviderTypeId;

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
		if (instance == null) instance = new LauncherContext("classiclauncher");
		return instance;
	}

	// ── Default game / provider ──────────────────────────────────────────────

	/**
	 * Sets the default game for this launcher instance and persists the game ID to
	 * {@link LauncherSettings#setDefaultGameId(String)}. If {@link Settings} is not yet initialized the persistence
	 * step is silently skipped.
	 *
	 * @param game
	 *            the default game, or {@code null} to clear
	 */
	public void setDefaultGame(Game game) {
		System.out.println("[LauncherContext.setDefaultGame] Setting to: "
				+ (game != null ? game.getDisplayName() + " (" + game.getGameId() + ")" : "null"));
		new Exception("[LauncherContext.setDefaultGame] call stack").printStackTrace(System.out);
		this.defaultGame = game;
		try {
			Settings.getInstance().getLauncher().setDefaultGameId(game != null ? game.getGameId() : null);
		} catch (Exception ignored) {
			// Settings not yet initialized during early startup
		}
	}

	public void setDefaultGameIfNone(Game game) {
		if (game == null) setDefaultGame(game);
	}

	/**
	 * Sets the provider type ID associated with the default game selection and persists it to
	 * {@link LauncherSettings#setDefaultProviderTypeId(String)}.
	 *
	 * @param providerTypeId
	 *            the provider type ID, or {@code null} to clear
	 */
	public void setDefaultProviderTypeId(String providerTypeId) {
		this.defaultProviderTypeId = providerTypeId;
		try {
			Settings.getInstance().getLauncher().setDefaultProviderTypeId(providerTypeId);
		} catch (Exception ignored) {
		}
	}

	/**
	 * Restores the default game and provider from persisted settings by scanning all registered providers for a
	 * matching game ID.
	 *
	 * <p>
	 * Resolution order:
	 * <ol>
	 * <li>If a provider type ID was saved, look for the game in that specific provider first.</li>
	 * <li>If not found (provider removed, extension unloaded, etc.), scan all providers for a game with the saved
	 * ID.</li>
	 * </ol>
	 *
	 * <p>
	 * Call this after extensions are loaded and accounts are signalled ready, so all providers and their games are
	 * registered.
	 */
	public void restoreDefaultGame() {
		try {
			LauncherSettings launcher = Settings.getInstance().getLauncher();
			String gameId = launcher.getDefaultGameId();
			String providerTypeId = launcher.getDefaultProviderTypeId();
			System.out.println(
					"[LauncherContext.restoreDefaultGame] gameId=" + gameId + " providerTypeId=" + providerTypeId);
			if (gameId == null) {
				System.out.println("[LauncherContext.restoreDefaultGame] No saved gameId, skipping restore");
				return;
			}

			List<AccountProvider> providers = Settings.getInstance().getAccounts().getProviders();
			System.out.println("[LauncherContext.restoreDefaultGame] Scanning " + providers.size() + " provider(s)");
			for (AccountProvider p : providers) {
				System.out.println("[LauncherContext.restoreDefaultGame]   provider=" + p.getTypeId() + " ("
						+ p.getDisplayName() + ") games=" + p.getGames().size());
				for (Game g : p.getGames()) {
					System.out.println("[LauncherContext.restoreDefaultGame]     game=" + g.getGameId() + " ("
							+ g.getDisplayName() + ")");
				}
			}

			// Tier 1: look in the saved provider
			if (providerTypeId != null) {
				for (AccountProvider provider : providers) {
					if (provider.getTypeId().equals(providerTypeId)) {
						for (Game game : provider.getGames()) {
							if (game.getGameId().equals(gameId)) {
								this.defaultGame = game;
								this.defaultProviderTypeId = providerTypeId;
								System.out.println("[LauncherContext.restoreDefaultGame] Restored from saved provider: "
										+ game.getDisplayName());
								return;
							}
						}
						System.out.println("[LauncherContext.restoreDefaultGame] Saved provider " + providerTypeId
								+ " found but game " + gameId + " not in its game list");
						break;
					}
				}
			}

			// Tier 2: scan all providers
			for (AccountProvider provider : providers) {
				for (Game game : provider.getGames()) {
					if (game.getGameId().equals(gameId)) {
						this.defaultGame = game;
						this.defaultProviderTypeId = provider.getTypeId();
						System.out.println("[LauncherContext.restoreDefaultGame] Restored from fallback scan: "
								+ game.getDisplayName() + " (provider=" + provider.getTypeId() + ")");
						return;
					}
				}
			}
			System.out.println("[LauncherContext.restoreDefaultGame] Game " + gameId + " not found in any provider");
		} catch (Exception e) {
			System.out.println("[LauncherContext.restoreDefaultGame] Exception: " + e.getMessage());
			e.printStackTrace();
		}
	}

	// ── Platform data directory ──────────────────────────────────────────────

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
