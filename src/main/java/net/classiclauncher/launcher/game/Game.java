package net.classiclauncher.launcher.game;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.account.Account;
import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.api.GameApi;
import net.classiclauncher.launcher.api.NullGameApi;
import net.classiclauncher.launcher.launch.ExeLaunchStrategy;
import net.classiclauncher.launcher.launch.JarLaunchStrategy;
import net.classiclauncher.launcher.launch.LaunchStrategy;
import net.classiclauncher.launcher.launch.NullLaunchStrategy;
import net.classiclauncher.launcher.settings.LauncherStyle;
import net.classiclauncher.launcher.settings.Settings;
import net.classiclauncher.launcher.ui.BackgroundRenderer;

/**
 * Immutable descriptor of a launchable game.
 *
 * <p>
 * A {@code Game} tells the launcher:
 * <ul>
 * <li>How to start the game binary ({@link ExecutableType}).</li>
 * <li>Which sections to show/hide in the Profile Editor.</li>
 * <li>What version-filter checkboxes to present (e.g. snapshots, beta, alpha).</li>
 * </ul>
 *
 * <p>
 * Construction via the fluent {@link Builder}:
 *
 * <pre>{@code
 *
 * Game myGame = Game.builder("my-game", "My Game", ExecutableType.JAR)
 * 		.versionFilter("snapshot", "Enable snapshot versions").resolutionSupported(false).build();
 * }</pre>
 *
 * <p>
 * Set the default game for a launcher instance via {@link LauncherContext#setDefaultGame(Game)}. Individual
 * {@link AccountProvider}s can override this by returning a non-null value from
 * {@link AccountProvider#getPrimaryGame()}.
 *
 * <p>
 * Use {@link #resolve()} anywhere in the UI to obtain the game that applies to the currently selected account.
 */
public final class Game {

	private final String gameId;
	private final String displayName;
	private final ExecutableType executableType;

	// Profile Editor visibility flags
	private final boolean versionSelectionEnabled;
	private final boolean gameDirSupported;
	private final boolean resolutionSupported;
	private final boolean autoCrashReportSupported;

	// Optional version-filter checkboxes; empty = use built-in Minecraft-style defaults
	private final List<VersionFilterOption> versionFilters;

	/**
	 * Factory for creating the game's default {@link GameApi}. Never null.
	 */
	private final Supplier<GameApi> apiFactory;

	/**
	 * Factory for creating the game's default {@link LaunchStrategy}. Never null.
	 */
	private final Supplier<LaunchStrategy> launchStrategyFactory;

	/**
	 * Factory for creating a style-specific {@link BackgroundRenderer}. May be null.
	 */
	private final Function<LauncherStyle, BackgroundRenderer> backgroundRendererFactory;

	/**
	 * Callback invoked when this game becomes the active game. May be null.
	 */
	private final Consumer<LauncherStyle> onSelectedCallback;

	private Game(Builder b) {
		this.gameId = Objects.requireNonNull(b.gameId, "gameId");
		this.displayName = Objects.requireNonNull(b.displayName, "displayName");
		this.executableType = Objects.requireNonNull(b.executableType, "executableType");
		this.versionSelectionEnabled = b.versionSelectionEnabled;
		this.gameDirSupported = b.gameDirSupported;
		this.resolutionSupported = b.resolutionSupported;
		this.autoCrashReportSupported = b.autoCrashReportSupported;
		this.versionFilters = Collections.unmodifiableList(new ArrayList<>(b.versionFilters));
		this.apiFactory = b.apiFactory != null ? b.apiFactory : () -> NullGameApi.INSTANCE;

		if (b.launchStrategyFactory != null) {
			this.launchStrategyFactory = b.launchStrategyFactory;
		} else {
			switch (b.executableType) {
				case JAR :
					this.launchStrategyFactory = () -> JarLaunchStrategy.INSTANCE;
					break;
				case EXE :
				case SHELL :
					this.launchStrategyFactory = () -> ExeLaunchStrategy.INSTANCE;
					break;
				default :
					this.launchStrategyFactory = () -> NullLaunchStrategy.INSTANCE;
					break;
			}
		}
		this.backgroundRendererFactory = b.backgroundRendererFactory;
		this.onSelectedCallback = b.onSelectedCallback;
	}

	// ── Getters ───────────────────────────────────────────────────────────────

	public String getGameId() {
		return gameId;
	}

	public String getDisplayName() {
		return displayName;
	}

	public ExecutableType getExecutableType() {
		return executableType;
	}

	public boolean isVersionSelectionEnabled() {
		return versionSelectionEnabled;
	}

	public boolean isGameDirSupported() {
		return gameDirSupported;
	}

	public boolean isResolutionSupported() {
		return resolutionSupported;
	}

	public boolean isAutoCrashReportSupported() {
		return autoCrashReportSupported;
	}

	/**
	 * Version-filter options shown as checkboxes in the Profile Editor. If empty, the Profile Editor falls back to
	 * built-in Minecraft-style defaults (snapshot / old_beta / old_alpha).
	 */
	public List<VersionFilterOption> getVersionFilters() {
		return versionFilters;
	}

	/**
	 * Creates a new {@link GameApi} via the configured factory. Never returns {@code null}; returns
	 * {@link NullGameApi#INSTANCE} when no factory is set.
	 */
	public GameApi createApi() {
		return apiFactory.get();
	}

	/**
	 * Creates a new {@link LaunchStrategy} via the configured factory. Never returns {@code null}; returns the
	 * appropriate built-in strategy based on {@link ExecutableType} when no custom factory is set.
	 */
	public LaunchStrategy createLaunchStrategy() {
		return launchStrategyFactory.get();
	}

	/**
	 * Creates a {@link BackgroundRenderer} for the given launcher style via the configured factory. Returns
	 * {@code null} when no factory is set (the launcher falls back to a solid {@code #1E1E1E} fill).
	 *
	 * @param style
	 *            the active launcher style
	 * @return a renderer for the requested style, or {@code null}
	 */
	public BackgroundRenderer createBackgroundRenderer(LauncherStyle style) {
		return backgroundRendererFactory != null ? backgroundRendererFactory.apply(style) : null;
	}

	// ── Context resolution ────────────────────────────────────────────────────

	/**
	 * Resolves the {@code Game} for the currently selected account:
	 * <ol>
	 * <li>If the {@link LauncherContext#getDefaultGame() default game} is supported by the account's provider, return
	 * it. This respects the user's explicit game selection in the login screen.</li>
	 * <li>Otherwise, the account's {@link AccountProvider#getPrimaryGame()} if non-null.</li>
	 * <li>{@link LauncherContext#getDefaultGame()} if set.</li>
	 * <li>{@code null} if no game is configured (all Profile Editor sections are shown).</li>
	 * </ol>
	 */
	public static Game resolve() {
		try {
			Settings settings = Settings.getInstance();
			String accountId = settings.getAccount().getSelectedAccountId();
			if (accountId != null) {
				Optional<Account> acc = settings.getAccounts().getById(accountId);
				if (acc.isPresent()) {
					Optional<AccountProvider> prov = settings.getAccounts().getProvider(acc.get().getType());
					if (prov.isPresent()) {
						// Prefer the default game if the provider supports it
						Game defaultGame = LauncherContext.getInstance().getDefaultGame();
						if (defaultGame != null && prov.get().getGames().contains(defaultGame)) {
							return defaultGame;
						}
						if (prov.get().getPrimaryGame() != null) {
							return prov.get().getPrimaryGame();
						}
					}
				}
			}
		} catch (Exception ignored) {
			// Settings may not be initialised in test contexts
		}
		return LauncherContext.getInstance().getDefaultGame();
	}

	// ── Builder ───────────────────────────────────────────────────────────────

	/**
	 * Creates a {@link Builder} with the three required fields.
	 *
	 * @param gameId
	 *            stable identifier (used internally, stored in config)
	 * @param displayName
	 *            human-readable name shown in the UI
	 * @param executableType
	 *            how the game binary is launched
	 */
	public static Builder builder(String gameId, String displayName, ExecutableType executableType) {
		return new Builder(gameId, displayName, executableType);
	}

	public static final class Builder {

		private final String gameId;
		private final String displayName;
		private final ExecutableType executableType;

		private boolean versionSelectionEnabled = true;
		private boolean gameDirSupported = true;
		private boolean resolutionSupported = true;
		private boolean autoCrashReportSupported = true;
		private final List<VersionFilterOption> versionFilters = new ArrayList<>();
		private Supplier<GameApi> apiFactory = null;
		private Supplier<LaunchStrategy> launchStrategyFactory = null;
		private Function<LauncherStyle, BackgroundRenderer> backgroundRendererFactory = null;
		private Consumer<LauncherStyle> onSelectedCallback = null;

		private Builder(String gameId, String displayName, ExecutableType executableType) {
			this.gameId = gameId;
			this.displayName = displayName;
			this.executableType = executableType;
		}

		/**
		 * Whether the version selector section is shown in the Profile Editor. Default: {@code true}.
		 */
		public Builder versionSelectionEnabled(boolean v) {
			versionSelectionEnabled = v;
			return this;
		}

		/**
		 * Whether the Game Directory field is shown in the Profile Editor. Default: {@code true}.
		 */
		public Builder gameDirSupported(boolean v) {
			gameDirSupported = v;
			return this;
		}

		/**
		 * Whether the Resolution fields are shown in the Profile Editor. Default: {@code true}.
		 */
		public Builder resolutionSupported(boolean v) {
			resolutionSupported = v;
			return this;
		}

		/**
		 * Whether the Auto Crash Report checkbox is shown in the Profile Editor. Default: {@code true}.
		 */
		public Builder autoCrashReportSupported(boolean v) {
			autoCrashReportSupported = v;
			return this;
		}

		/**
		 * Adds a version-filter checkbox entry. Call multiple times to add multiple filters (shown in order).
		 *
		 * @param typeId
		 *            the {@code VersionType} ID to filter (e.g. {@code "snapshot"})
		 * @param label
		 *            checkbox label in the Profile Editor
		 */
		public Builder versionFilter(String typeId, String label) {
			versionFilters.add(VersionFilterOption.builder().typeId(typeId).label(label).build());
			return this;
		}

		/**
		 * Sets a factory that supplies the default {@link GameApi} for this game. When not set,
		 * {@link NullGameApi#INSTANCE} is used as the default.
		 *
		 * @param factory
		 *            a non-null supplier; the returned {@link GameApi} must never be null
		 */
		public Builder apiFactory(Supplier<GameApi> factory) {
			this.apiFactory = factory;
			return this;
		}

		/**
		 * Sets a factory that supplies the default {@link LaunchStrategy} for this game. When not set, the strategy is
		 * inferred from {@link ExecutableType}: JAR → {@link JarLaunchStrategy}, EXE/SHELL → {@link ExeLaunchStrategy},
		 * otherwise → {@link NullLaunchStrategy}.
		 *
		 * @param factory
		 *            a non-null supplier; the returned {@link LaunchStrategy} must never be null
		 */
		public Builder launchStrategyFactory(Supplier<LaunchStrategy> factory) {
			this.launchStrategyFactory = factory;
			return this;
		}

		/**
		 * Sets a factory that creates a {@link BackgroundRenderer} for a given {@link LauncherStyle}. The factory may
		 * return {@code null} for styles it does not support — the launcher falls back to a solid {@code #1E1E1E} fill.
		 *
		 * @param factory
		 *            a function from style to renderer (or {@code null})
		 */
		public Builder backgroundRendererFactory(Function<LauncherStyle, BackgroundRenderer> factory) {
			this.backgroundRendererFactory = factory;
			return this;
		}

		/**
		 * Sets a callback invoked when this game becomes the active game. The callback receives the currently active
		 * {@link LauncherStyle}.
		 *
		 * @param callback
		 *            a non-null consumer; called on the EDT via {@link Game#onSelected(LauncherStyle)}
		 */
		public Builder onSelected(Consumer<LauncherStyle> callback) {
			this.onSelectedCallback = callback;
			return this;
		}

		public Game build() {
			return new Game(this);
		}

	}

	/**
	 * Called by the launcher when this game becomes the active game (e.g. when the user switches provider or the UI
	 * initialises). Invokes the callback registered via {@link Builder#onSelected(Consumer)}, if any.
	 *
	 * @param style
	 *            the currently active launcher style
	 */
	public void onSelected(LauncherStyle style) {
		if (onSelectedCallback != null) onSelectedCallback.accept(style);
	}

}
