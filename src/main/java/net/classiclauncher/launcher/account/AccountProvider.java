package net.classiclauncher.launcher.account;

import java.util.List;
import java.util.function.Consumer;

import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.api.GameApi;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.launch.LaunchStrategy;
import net.classiclauncher.launcher.settings.LauncherStyle;
import net.classiclauncher.launcher.ui.BackgroundRenderer;

/**
 * Describes an account type and provides the factory methods needed to create and restore accounts.
 *
 * <p>
 * Register a provider at startup via {@link Accounts#registerProvider(AccountProvider)}. Built-in providers are
 * registered automatically; downstream projects add their own in {@code Main}.
 *
 * <p>
 * Implementation contract:
 * <ul>
 * <li>Providers with {@link AuthMethod#FORM} must implement {@link #createFromForm(String, char[])}.</li>
 * <li>Providers with {@link AuthMethod#BROWSER} must implement {@link #startBrowserAuth(Consumer, Consumer)}.</li>
 * <li>{@link #fromConfig(String, YmlConfig)} must be implemented by all providers for account deserialization.</li>
 * </ul>
 */
public abstract class AccountProvider {

	/**
	 * A stable, uppercase identifier for this account type, stored in the account's YAML. Must match the value returned
	 * by {@link Account#getType()} for accounts this provider creates.
	 */
	public abstract String getTypeId();

	/**
	 * Human-readable name shown in the UI (e.g. "Offline", "Microsoft").
	 */
	public abstract String getDisplayName();

	/**
	 * Classpath resource path for this provider's icon (SVG or PNG), or {@code null} if none. Example:
	 * {@code "/icons/offline.svg"}
	 */
	public abstract String getIconResourcePath();

	/**
	 * {@code true} if the login form should show a password field.
	 */
	public abstract boolean requiresPassword();

	/**
	 * The authentication method this provider uses.
	 */
	public abstract AuthMethod getAuthMethod();

	/**
	 * For {@link AuthMethod#BROWSER} providers: the local redirect URI that the callback HTTP server will listen on
	 * (e.g. {@code "http://localhost:8765/callback"}). Returns {@code null} for non-browser providers.
	 */
	public abstract String getCallbackUri();

	/**
	 * Returns the list of {@link Game} instances this provider supports.
	 *
	 * <p>
	 * When the list is non-empty, {@link #getPrimaryGame()} returns the first element and that game takes precedence
	 * over {@link LauncherContext#getDefaultGame()} for accounts of this type.
	 *
	 * <p>
	 * Return an empty list for game-agnostic providers (e.g. offline accounts).
	 *
	 * @return an unmodifiable, never-null list of supported games
	 */
	public abstract List<Game> getGames();

	/**
	 * Convenience accessor: returns the first game from {@link #getGames()}, or {@code null} if the list is empty
	 * (game-agnostic provider).
	 *
	 * <p>
	 * This method is {@code final} and cannot be overridden.
	 *
	 * @return the primary game, or {@code null}
	 */
	public final Game getPrimaryGame() {
		List<Game> games = getGames();
		return (games != null && !games.isEmpty()) ? games.get(0) : null;
	}

	/**
	 * Returns the {@link GameApi} to use for the given game with this provider's accounts.
	 *
	 * <p>
	 * Default: delegates to {@link Game#createApi()} — the game's built-in API. Override to supply a custom or
	 * decorating API (e.g. an archival version source) for a specific game while reusing the default for others.
	 *
	 * @param game
	 *            the resolved game (never null)
	 * @return the {@link GameApi} to use; never null
	 */
	public GameApi getApiForGame(Game game) {
		return game.createApi();
	}

	/**
	 * Returns the {@link LaunchStrategy} to use when launching the given game with accounts belonging to this provider.
	 *
	 * <p>
	 * Default: delegates to {@link Game#createLaunchStrategy()} — the game's built-in strategy. Override to supply a
	 * custom strategy (e.g. Minecraft-specific download pipeline) for a specific game while reusing the default for
	 * others.
	 *
	 * @param game
	 *            the resolved game (never null)
	 * @return the {@link LaunchStrategy} to use; never null
	 */
	public LaunchStrategy getLaunchStrategy(Game game) {
		return game.createLaunchStrategy();
	}

	/**
	 * Returns the {@link BackgroundRenderer} to use when displaying the given game with the given launcher style for
	 * accounts belonging to this provider.
	 *
	 * <p>
	 * Default: delegates to {@link Game#createBackgroundRenderer(LauncherStyle)} — the game's built-in renderer
	 * factory. Override to supply a custom background for a specific game/style combination while reusing the default
	 * for others.
	 *
	 * @param game
	 *            the resolved game (may be {@code null} if no game is selected)
	 * @param style
	 *            the active launcher style
	 * @return a {@link BackgroundRenderer}, or {@code null} for the solid fallback
	 */
	public BackgroundRenderer getBackgroundRenderer(Game game, LauncherStyle style) {
		return game != null ? game.createBackgroundRenderer(style) : null;
	}

	/**
	 * Called when this provider's game becomes the active selection in the launcher UI.
	 *
	 * <p>
	 * This fires in three scenarios:
	 * <ul>
	 * <li>The user picks this provider (and game) in the {@code GameSelectorWidget} popover.</li>
	 * <li>The launcher opens and an account belonging to this provider is already selected.</li>
	 * <li>The user logs in with this provider and the main view is shown.</li>
	 * </ul>
	 *
	 * <p>
	 * Extensions can override this to perform side effects such as dynamically changing the update-notes URL or
	 * configuring game-specific state. The method is called on the EDT.
	 *
	 * <p>
	 * If two different providers support the same game, only the provider that owns the active account receives this
	 * callback — the other provider's hook is not called.
	 *
	 * <p>
	 * The default implementation is a no-op.
	 *
	 * @param game
	 *            the game that was selected (may be {@code null} for game-agnostic providers)
	 * @param style
	 *            the active launcher style
	 */
	public void onGameSelected(Game game, LauncherStyle style) {
		// no-op — extensions override as needed
	}

	/**
	 * The URL of the update / patch-notes page for accounts belonging to this provider. Returning {@code null}
	 * instructs the launcher to fall back to the URL configured in {@code settings.yml} ({@code update-notes.url}).
	 *
	 * <p>
	 * This lets forks expose different news pages per account type (e.g. a Microsoft account could show Mojang news
	 * while a custom provider shows your own server's news).
	 */
	public abstract String getUpdateNotesUrl();

	/**
	 * Creates a new account from form credentials. Implementations must clear the {@code password} array after use to
	 * avoid credentials lingering in heap memory.
	 *
	 * <p>
	 * Only called when {@link #getAuthMethod()} is {@link AuthMethod#FORM}.
	 *
	 * @param username
	 *            the username entered by the user
	 * @param password
	 *            the password characters (cleared by this method after use)
	 * @return the newly created account
	 * @throws UnsupportedOperationException
	 *             if this provider does not support form auth
	 */
	public abstract Account createFromForm(String username, char[] password);

	/**
	 * Initiates a browser-based authentication flow. Implementations must call either {@code onComplete} or
	 * {@code onError} exactly once, from any thread.
	 *
	 * <p>
	 * Only called when {@link #getAuthMethod()} is {@link AuthMethod#BROWSER}.
	 *
	 * @param onComplete
	 *            called with the authenticated account on success
	 * @param onError
	 *            called with a human-readable error message on failure
	 * @throws UnsupportedOperationException
	 *             if this provider does not support browser auth
	 */
	public abstract void startBrowserAuth(Consumer<Account> onComplete, Consumer<String> onError);

	/**
	 * Deserializes an account from its persisted YAML config.
	 *
	 * @param id
	 *            the account UUID (file name without extension)
	 * @param config
	 *            the loaded YAML config for this account
	 * @return the deserialized account
	 */
	public abstract Account fromConfig(String id, YmlConfig config);

	/**
	 * Asynchronously refreshes the account's identity (username, UUID) and tokens in the background. Called at launcher
	 * startup and when the user switches to an existing account, so that the displayed username is always up to date.
	 *
	 * <p>
	 * The default implementation immediately calls {@code onUpdated} with the account unchanged — no network call is
	 * made. Override this method in providers that support token-based refresh (e.g. Microsoft / OAuth providers).
	 *
	 * <p>
	 * Implementations must call exactly one of {@code onUpdated} or {@code onError}, from any thread. Callers wrap UI
	 * updates in {@link javax.swing.SwingUtilities#invokeLater}.
	 *
	 * @param account
	 *            the account to refresh
	 * @param onUpdated
	 *            called with the (possibly updated) account on success
	 * @param onError
	 *            called with a human-readable message on failure; the caller treats this as non-fatal and continues
	 *            with the cached account data
	 */
	public void refreshIdentityAsync(Account account, Consumer<Account> onUpdated, Consumer<String> onError) {
		onUpdated.accept(account);
	}

}
