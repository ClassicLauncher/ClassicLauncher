# Implementing Custom Account Providers

An `AccountProvider` bundles everything the launcher needs to know about an account type:
metadata shown in the UI, the authentication flow, game association, API selection, and the
deserialization factory.

---

## Minimal implementation

```java
public class MyAccountProvider extends AccountProvider {

	@Override
	public String getTypeId() {
		return "MY_TYPE";
	}

	@Override
	public String getDisplayName() {
		return "My Auth";
	}

	@Override
	public String getIconResourcePath() {
		return "/icons/my-auth.svg";
	} // or null

	@Override
	public boolean requiresPassword() {
		return true;
	}

	@Override
	public AuthMethod getAuthMethod() {
		return AuthMethod.FORM;
	}

	@Override
	public String getCallbackUri() {
		return null;
	}

	@Override
	public List<Game> getGames() {
		return Collections.singletonList(MY_GAME);
	}

	@Override
	public Account createFromForm(String username, char[] password) {
		// validate credentials, call your auth API, return the account
		String token = MyAuthApi.authenticate(username, new String(password));
		Arrays.fill(password, '\0'); // always clear the password array
		return new MyAccount(Account.generateId(), username, token);
	}

	@Override
	public void startBrowserAuth(Consumer<Account> onComplete, Consumer<String> onError) {
		throw new UnsupportedOperationException("FORM provider does not support browser auth");
	}

	@Override
	public Account fromConfig(String id, YmlConfig config) {
		return MyAccount.fromConfig(id, config);
	}
}
```

Register it before the UI starts:

```java
Settings.getInstance().

getAccounts().

registerProvider(new MyAccountProvider());
```

**From an extension**, use `Accounts.onReady` to defer registration until the bootstrap
signals that `Accounts` is fully initialized:

```java
// inside LauncherExtension.onLoad(settings):
Accounts.onReady(accounts ->accounts.

registerProvider(new MyAccountProvider()));
```

This works whether `onReady` is called before or after `Accounts.signalReady` — if Accounts is
already ready the callback fires immediately; otherwise it is queued and executed when
`signalReady` is called during bootstrap (step 5 of `Main`).

---

## Browser / OAuth provider

For OAuth flows set `getAuthMethod()` to `AuthMethod.BROWSER` and use `BrowserAuthHelper`:

```java

@Override
public AuthMethod getAuthMethod() {
	return AuthMethod.BROWSER;
}

@Override
public String getCallbackUri() {
	return "http://localhost:8765/callback";
}

@Override
public void startBrowserAuth(Consumer<Account> onComplete, Consumer<String> onError) {
	String authUrl = "https://auth.my-game.com/oauth?redirect_uri=" + getCallbackUri();

	BrowserAuthHelper.openUrl(authUrl);

	BrowserAuthHelper.startCallbackServer(8765, (path, params) -> {
		String code = params.get("code");
		if (code == null) {
			onError.accept("Missing authorization code in callback.");
			return;
		}
		try {
			String token = MyAuthApi.exchangeCode(code);
			String username = MyAuthApi.fetchUsername(token);
			onComplete.accept(new MyAccount(Account.generateId(), username, token));
		} catch (Exception e) {
			onError.accept("Token exchange failed: " + e.getMessage());
		}
	});
}
```

The callback server shuts itself down after the first request. The `onComplete`/`onError`
callbacks may be called from a non-Swing thread — the launcher wraps them in
`SwingUtilities.invokeLater` automatically.

---

## Account class

Your account class must extend `Account` and implement `getType()` and `saveData(YmlConfig)`:

```java
public class MyAccount extends Account {

	private final String token;

	public MyAccount(String id, String username, String token) {
		super(id, username);
		this.token = token;
	}

	@Override
	public String getType() {
		return "MY_TYPE";
	}

	@Override
	protected void saveData(YmlConfig config) {
		config.set("type", getType());
		config.set("display-name", getDisplayName());
		config.set("token", token); // store securely in production
		config.save();
	}

	public static MyAccount fromConfig(String id, YmlConfig config) {
		return new MyAccount(id,
				config.getString("display-name", "Player"),
				config.getString("token", ""));
	}
}
```

---

## Defining a type ID constant in your extension

Do not add custom type ID strings to the Launcher core's `AccountType` class. Instead, define
your own constant in your extension and register it with the `AccountType` registry:

```java
// In your extension module:
public final class MyAccountType {
	public static final String MY_TYPE = "MY_TYPE";

	private MyAccountType() {
	}
}
```

Then in `LauncherExtension.onLoad()`, register it so introspection tools can discover the
associated classes:

```java
AccountType.register(MyAccountType.MY_TYPE, MyAccount.CLASS, MyAccountProvider.CLASS);
```

The `CLASS` constants are `BetterReflectionClass` instances defined at the bottom of each class:

```java
// At the bottom of MyAccount.java:
public static final BetterReflectionClass<MyAccount> CLASS =
		new BetterReflectionClass<>(MyAccount.class);

// At the bottom of MyAccountProvider.java:
public static final BetterReflectionClass<MyAccountProvider> CLASS =
		new BetterReflectionClass<>(MyAccountProvider.class);
```

The `AccountType` registry (`AccountType.getAll()`, `AccountType.getEntry(typeId)`,
`AccountType.getAccountClass(typeId)`, `AccountType.getProviderClass(typeId)`) is for
introspection and tooling. Deserialization is driven separately via
`Accounts.registerProvider()` — both calls are needed.

---

## Linking games via getGames()

Override `getGames()` to associate one or more `Game` objects with this provider. The first
game returned by `getPrimaryGame()` is the one the launcher uses for profile editing and binary
launching whenever an account of this type is active.

```java
private static final Game MY_GAME = Game.builder("my-game", "My Game", ExecutableType.JAR)
		.versionFilter("snapshot", "Show snapshots")
		.versionFilter("old_beta", "Show old Beta versions")
		.apiFactory(MyVersionApi::new)
		.build();

@Override
public List<Game> getGames() {
	return Collections.singletonList(MY_GAME);
}
```

Game-agnostic providers (e.g. offline accounts that work with any game) should return an
empty list:

```java

@Override
public List<Game> getGames() {
	return Collections.emptyList();
}
```

See [`game-abstraction.md`](game-abstraction.md) for the full `Game` API, including `apiFactory`.

---

## Customising the version API (getApiForGame)

By default `getApiForGame(Game)` delegates to `game.createApi()` — the factory set on the `Game`
builder. You only need to override it when a specific provider needs a *different* API for the
same game (e.g. an archival server that exposes additional old versions):

```java
@Override
public GameApi getApiForGame(Game game) {
	if ("my_game".equals(game.getGameId())) {
		return new ArchivalVersionApi();   // custom API for this provider only
	}
	return game.createApi();               // default for all other games
}
```

`ProfileEditorDialog` calls `provider.getApiForGame(game)` so the selected account's provider
drives which API populates the version picker.

See [`api-integration.md`](api-integration.md) for how to implement `HttpGameApi`.

---

## Customising the launch strategy (getLaunchStrategy)

`AccountProvider.getLaunchStrategy(Game)` mirrors the `getApiForGame` pattern. The default
implementation delegates to `game.createLaunchStrategy()` — no override is needed for most
providers.

Override it only when a specific provider requires a *different* launch pipeline than the game's
built-in default. A typical case is an extension that adds offline account support with a custom
token injection step:

```java

@Override
public LaunchStrategy getLaunchStrategy(Game game) {
	if ("my_game".equals(game.getGameId())) {
		return new MyCustomLaunchStrategy();
	}
	return game.createLaunchStrategy(); // default for all other games
}
```

`GameLauncher` calls `provider.getLaunchStrategy(game)` (where `provider` is the active account's
provider) before falling through to `game.createLaunchStrategy()`. The two hooks compose cleanly:
set the factory on `Game` for the game-level default; override `getLaunchStrategy` in the provider
only for provider-specific deviations.

See [`launch-system.md`](launch-system.md) for the full `LaunchStrategy` contract, the built-in
implementations, and how to implement custom prepare and `buildCommand` pipelines.

---

## Customising the background renderer (getBackgroundRenderer)

`AccountProvider.getBackgroundRenderer(Game, LauncherStyle)` mirrors the `getApiForGame` and
`getLaunchStrategy` patterns. The default implementation delegates to
`game.createBackgroundRenderer(style)` — no override is needed for most providers.

Override it only when a specific provider requires a *different* background than the game's
built-in default:

```java

@Override
public BackgroundRenderer getBackgroundRenderer(Game game, LauncherStyle style) {
	if ("my-game".equals(game.getGameId()) && style == LauncherStyle.V1_1) {
		return new MyCustomDarkRenderer();  // provider-specific background
	}
	return game.createBackgroundRenderer(style);  // default for other games
}
```

The launcher calls `provider.getBackgroundRenderer(game, style)` from `LoginScreen` and
`LauncherAlpha` whenever the active provider or game changes. The background swaps dynamically
via `BackgroundPanel.setRenderer()`. The `LauncherV1_1` main view (tabbed layout) does not
have a background — it uses a plain panel.

When the method returns `null`, the launcher's `BackgroundPanel` fills with a solid `#1E1E1E`
gray fallback — this is the default when no extension is loaded.

See [`game-abstraction.md`](game-abstraction.md) for setting the game-level default via
`backgroundRendererFactory`.

---

## Game selection hook (onGameSelected)

`AccountProvider.onGameSelected(Game, LauncherStyle)` is called on the EDT whenever this
provider's game becomes the active selection in the launcher UI. It fires in three cases:

1. The user picks this provider (and game) in the `GameSelectorWidget` popover.
2. The launcher opens and an account belonging to this provider is already selected.
3. The user logs in with this provider and the main view is shown.

The default implementation is a no-op. Override it to perform side effects such as dynamically
changing the update-notes URL, loading game-specific configuration, or adjusting launcher state:

```java

@Override
public void onGameSelected(Game game, LauncherStyle style) {
	if ("my-game".equals(game.getGameId())) {
		Settings.getInstance().getLauncher().setUpdateNotesUrl("https://my-game.com/news");
	}
}
```

If two different providers support the same game, only the provider that owns the active
account receives the callback — the other provider's hook is not called.

---

## Background identity refresh (refreshIdentityAsync)

The launcher calls `refreshIdentityAsync` at startup and on account switch to silently update the
account's username, UUID, and tokens in the background. The default implementation is a no-op
(calls `onUpdated` immediately). Override it in token-based providers:

```java

@Override
public void refreshIdentityAsync(Account account,
								 Consumer<Account> onUpdated,
								 Consumer<String> onError) {
	MyAccount myAccount = (MyAccount) account;
	// Run on a background thread — this method may be called from the EDT
	new Thread(() -> {
		try {
			String freshName = MyAuthApi.fetchCurrentUsername(myAccount.getToken());
			myAccount.applyUsername(freshName);
			Settings.getInstance().getAccounts().save(myAccount);
			onUpdated.accept(myAccount);
		} catch (Exception e) {
			onError.accept(e.getMessage()); // non-fatal — caller logs and continues
		}
	}).start();
}
```

The `onError` callback is treated as non-fatal by `LauncherV1_1` — it logs to stderr and
continues showing the cached username. Never show a blocking dialog from `onError` here.

---

## Update notes URL

Override `getUpdateNotesUrl()` to supply a provider-specific update notes page. If this returns
`null` (the default), the launcher falls back to `settings.yml → update-notes.url`.

```java

@Override
public String getUpdateNotesUrl() {
	return "https://my-game.com/patch-notes";
}
```

---

## AccountComboBox

`AccountComboBox` is a drop-in `JComboBox<Account>` replacement that renders each account with
its game icon, display name, game name, and provider name — in both the collapsed trigger and the
open dropdown.

```java
AccountComboBox combo = new AccountComboBox(accounts.getProviders());
for(
Account acc :accounts.

getAll()){
		combo.

addItem(acc);
}
```

Each item is rendered as:

```
[16×16 icon]  AccountName — GameName (ProviderName)
```

Icon resolution follows the same fallback chain as the `GameSelectorWidget` popover:

1. Auto-detected game icon (`/assets/games/{gameId}/style/{style}/icon.{svg|png}`)
2. `AccountProvider.getIconResourcePath()`
3. No icon

The game is resolved from `provider.getPrimaryGame()`. If no provider has a game, the
launcher-wide default set via `LauncherContext.getInstance().setDefaultGame(...)` is used instead.
Any missing segment (game or provider) is silently omitted from the label.

---

## LoginScreen account-aware defaults

When opened in **switch mode** (via BottomBar's "Switch User"), `LoginScreen` automatically:

1. **Resolves the initial provider** from the currently selected account (via
   `accountSettings.getSelectedAccountId()` → `accounts.getById()` → `accounts.getProvider()`).
   This sets the `GameSelectorWidget` and background to match the active account's game, rather
   than defaulting to the first registered provider.

2. **Pre-selects the current account** in the existing-accounts combo box, so the user sees their
   active account highlighted immediately.

3. **Updates the background dynamically** when the user picks a different existing account in the
   combo box. The combo's `ItemListener` resolves the selected account's provider, updates the
   `GameSelectorWidget`, and calls `updateBackgroundRenderer()` — swapping the background to
   match the newly selected account's game.

If the selected account's provider is not found (e.g. the extension was uninstalled), the
fallback is the first registered provider, same as for a fresh login.

---

## IconButton

`IconButton` is a small, borderless `JButton` that displays a single Unicode glyph (e.g. ⚙, ⋮, ✕).
It is used throughout the launcher UI for compact action buttons.

Two visual modes:

| Mode        | Description                                | Constructor                                          |
|-------------|--------------------------------------------|------------------------------------------------------|
| Transparent | No background; glyph floats over content   | `new IconButton(glyph, tooltip)`                     |
| Filled      | Opaque background with hover colour change | `new IconButton(glyph, tooltip, defaultBg, hoverBg)` |

Both modes suppress focus/border/content-area painting and set a hand cursor.

```java
// Transparent gear button (used in GameSelectorWidget popover)
IconButton gear = new IconButton("\u2699", "Settings").withSize(24, 16);
gear.

addActionListener(e ->

openSettings());

// Transparent kebab button (used in ExtensionCard)
IconButton kebab = new IconButton("\u22EE", "Options")
		.withFontStyle(Font.BOLD, 14f)
		.withSize(24, 24);
kebab.

addActionListener(e ->

showMenu(kebab));

// Filled mode — opaque background with hover colour change
IconButton action = new IconButton("\u2713", "Confirm",
		new Color(0x34A853), new Color(0x2D8F47))
		.withSize(28, 28);
action.

addActionListener(e ->

confirm());
```

Fluent setters:

| Method                       | Description                                           |
|------------------------------|-------------------------------------------------------|
| `withSize(w, h)`             | Sets preferred and maximum size                       |
| `withFontStyle(style, size)` | Overrides font style (`PLAIN`, `BOLD`) and point size |
