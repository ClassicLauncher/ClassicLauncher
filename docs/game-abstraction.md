# Game Abstraction

The `Game` class tells the launcher what kind of binary your game uses, which version filters
appear in the Profile Editor, how the Java settings section behaves, and which `GameApi` to use
to fetch available versions.

---

## Building a Game

```java
Game game = Game.builder("my-game", "My Game", ExecutableType.JAR)
		.versionFilter("snapshot", "Show snapshots")
		.versionFilter("old_beta", "Show old Beta versions")
		.versionFilter("old_alpha", "Show old Alpha versions")
		.apiFactory(MyVersionApi::new)    // optional — see "Version API" below
		.build();
```

### `ExecutableType`

| Value   | Effect                                                                            |
|---------|-----------------------------------------------------------------------------------|
| `JAR`   | Java Settings section visible in Profile Editor; launcher invokes `java -jar ...` |
| `EXE`   | Java Settings section hidden; launcher invokes the binary directly                |
| `SHELL` | Java Settings section hidden; launcher invokes via shell                          |

### Version filters

Each `versionFilter(typeId, label)` call adds one `VersionFilterOption`. The Profile Editor
renders a checkbox for each option. The option's `typeId` is compared to `Version.getType().getId()`
to decide whether to show or hide a version in the picker list.

The first three filters map to Profile's fixed boolean fields:

| Index | Profile field         |
|-------|-----------------------|
| 0     | `enableSnapshots`     |
| 1     | `enableBetaVersions`  |
| 2     | `enableAlphaVersions` |

Games with fewer than three filters hide the unused checkboxes. Games that need more than three
filters should store the extras elsewhere (the Profile model currently supports only three).

---

## Version API (apiFactory / createApi)

Each `Game` can own a default `GameApi` via the `apiFactory` builder option. The factory is a
`Supplier<GameApi>` called lazily by `Game.createApi()` each time a fresh API instance is needed
(e.g. when the Profile Editor opens).

```java
Game game = Game.builder("my-game", "My Game", ExecutableType.JAR)
		.versionFilter("release", "Show release builds")
		.apiFactory(MyVersionApi::new)   // MyVersionApi extends HttpGameApi
		.build();
```

When no `apiFactory` is set, `Game.createApi()` returns `NullGameApi.INSTANCE` — an empty,
no-network singleton that is safe to call but returns no versions.

`AccountProvider.getApiForGame(Game)` delegates to `game.createApi()` by default, so setting
the factory on the `Game` is sufficient for most cases. Only override `getApiForGame` when a
specific provider needs a *different* API for the same game.

See [`api-integration.md`](api-integration.md) for how to implement `HttpGameApi`.

---

## Launch strategy (launchStrategyFactory / createLaunchStrategy)

Each `Game` can declare a default `LaunchStrategy` via the `launchStrategyFactory` builder option.
The factory is a `Supplier<LaunchStrategy>` invoked lazily by `Game.createLaunchStrategy()` each
time a launch is initiated (once per `GameLauncher.launchAsync` call).

```java
Game game = Game.builder("my-game", "My Game", ExecutableType.JAR)
        .apiFactory(MyVersionApi::new)
        .launchStrategyFactory(MyLaunchStrategy::new)
        .build();
```

When no factory is set, `createLaunchStrategy()` returns a sensible default based on
`ExecutableType`:

| `ExecutableType` | Default strategy              |
|------------------|-------------------------------|
| `JAR`            | `JarLaunchStrategy.INSTANCE`  |
| `EXE`            | `ExeLaunchStrategy.INSTANCE`  |
| `SHELL`          | `ExeLaunchStrategy.INSTANCE`  |
| anything else    | `NullLaunchStrategy.INSTANCE` |

Most games do not need to set `launchStrategyFactory` explicitly — the auto-selected default is
sufficient. A game extension typically sets its own strategy:

```java
Game game = Game.builder("my-game", "My Game", ExecutableType.JAR)
        .apiFactory(MyVersionApi::new)
        .launchStrategyFactory(MyLaunchStrategy::new)
        .build();
```

`AccountProvider.getLaunchStrategy(Game)` is called by `GameLauncher` before
`Game.createLaunchStrategy()`. Its default implementation delegates directly to
`game.createLaunchStrategy()`, so setting the factory on the `Game` is sufficient for most cases.
Only override `getLaunchStrategy` in the provider when a specific provider needs a *different*
pipeline for the same game (see [`account-providers.md`](account-providers.md)).

See [`launch-system.md`](launch-system.md) for the full `LaunchStrategy` contract,
`LaunchProgress`, `LaunchContext`, and `GameLauncher` orchestration.

---

## Background renderer (backgroundRendererFactory / createBackgroundRenderer)

Each `Game` can declare a per-style `BackgroundRenderer` via the `backgroundRendererFactory` builder
option. The factory is a `Function<LauncherStyle, BackgroundRenderer>` called by
`Game.createBackgroundRenderer(style)` each time a background is needed (e.g. when the login screen
loads or the user switches provider/game).

```java
Game game = Game.builder("my-game", "My Game", ExecutableType.JAR)
        .backgroundRendererFactory(style -> {
            switch (style) {
                case ALPHA:
                    return new MyPlainRenderer("/assets/bg.png");
                case V1_1:
                    return new MyDarkRenderer("/assets/bg.png", 0.3f);
                default:
                    return null;
            }
        })
        .build();
```

`BackgroundRenderer` is a `@FunctionalInterface` with a single method:

```java
void paint(Graphics2D g, int width, int height);
```

When no factory is set (or the factory returns `null`), the launcher's `BackgroundPanel` fills with
a solid `#1E1E1E` gray fallback automatically — no special handling is required.

`AccountProvider.getBackgroundRenderer(Game, LauncherStyle)` delegates to
`game.createBackgroundRenderer(style)` by default, so setting the factory on the `Game` is
sufficient for most cases. Only override `getBackgroundRenderer` in the provider when a specific
provider needs a *different* background for the same game (see
[`account-providers.md`](account-providers.md)).

---

## Selection callback (onSelected)

Use `onSelected` to run code whenever this game becomes the active game — for example, to
pre-fetch versions or refresh UI state.

```java
Game game = Game.builder("my-game", "My Game", ExecutableType.JAR)
		.apiFactory(MyVersionApi::new)
		.onSelected(style -> {
			// Called on the EDT each time this game is selected
			myVersionCache.refresh();
		})
		.build();
```

The callback receives the currently active `LauncherStyle` so it can tailor its behaviour to the
visible UI (e.g. show a style-specific splash while fetching data).

The callback is called by `Game.onSelected(LauncherStyle)`, which the launcher invokes on the EDT.
If no callback is registered the call is a no-op.

---

## Attaching a Game to a provider

Implement `AccountProvider.getGames()` to associate one or more games with the provider.
`getPrimaryGame()` returns the first element (or `null` for game-agnostic providers):

```java
private static final Game MY_GAME = Game.builder("my-game", "My Game", ExecutableType.JAR)
		.versionFilter("snapshot", "Show snapshots")
		.apiFactory(MyVersionApi::new)
		.build();

@Override
public List<Game> getGames() {
	return Collections.singletonList(MY_GAME);
}
```

The launcher calls `Game.resolve()` to obtain the active game:

1. If the `LauncherContext.getDefaultGame()` is supported by the selected account's provider, return
   it. This respects the user's explicit game selection in the login screen.
2. Otherwise, the account's `provider.getPrimaryGame()` if non-null.
3. Falls back to `LauncherContext.getInstance().getDefaultGame()` if set.
4. Returns `null` if none are configured (Profile Editor uses three built-in filters, Java section
   shown).

Two `Game` objects are considered equal when they share the same `gameId` (via `equals`/`hashCode`),
so `List.contains()` checks work correctly even when providers recreate their game lists.

When a game becomes active the launcher calls both `game.onSelected(style)` (the callback
registered on the `Game` itself — see "Selection callback" above) and
`provider.onGameSelected(game, style)` on the owning provider. Use the `Game` callback for
game-level reactions; use the provider override for provider-specific reactions.
See [`account-providers.md`](account-providers.md) for the provider-side hook.

---

## Launcher-wide default and persistence

The user's game and provider selection is persisted in `settings.yml` as `default-game` (game ID)
and `default-provider` (provider type ID). Call `setDefaultGame` / `setDefaultProviderTypeId` on
`LauncherContext` — both auto-persist to `LauncherSettings`:

```java
LauncherContext ctx = LauncherContext.getInstance();
ctx.setDefaultGame(game);                          // persists game ID
ctx.setDefaultProviderTypeId(provider.getTypeId()); // persists provider type ID
```

On startup, after extensions are loaded and providers are registered, `Main` calls
`LauncherContext.getInstance().restoreDefaultGame()` to resolve the persisted IDs back to live
`Game` / provider objects. The restore scans the saved provider first, then falls back to any
provider that offers the saved game ID.

---

## Game assets (logo & icon)

`GameSelectorWidget` automatically loads a logo and an icon for each game without any path
configuration on the `Game` object. Assets are resolved at runtime by `ResourceLoader` based
on the game's `gameId` and the active `LauncherStyle`.

### Directory convention

```
src/main/resources/
  assets/
    games/
      {gameId}/
        style/
          alpha/
            logo.svg   ← wide logo shown in the widget body  (SVG preferred, .png fallback)
            logo.png
            icon.svg   ← small square icon shown in the popover grid
            icon.png
          v1_1/
            logo.svg
            logo.png
            icon.svg
            icon.png
```

`{gameId}` is the stable identifier — the first argument passed to `Game.builder(...)`.
`LauncherStyle` names are lowercased (`ALPHA` → `alpha`, `V1_1` → `v1_1`).

Only one format per asset is required. SVG is always tried first; `.png` is the fallback.
If neither exists the widget falls back to rendering the game's `displayName` as bold text.

### Fallback chain for icons in the popover and account combo box

1. Auto-detected game icon (`/assets/games/{gameId}/style/{style}/icon.{svg|png}`)
2. `AccountProvider.getIconResourcePath()` (the provider's own classpath icon)
3. No icon — card shows the provider name only

### Single-provider behaviour

`GameSelectorWidget` always opens a popover when clicked — even with a single provider the
popover is shown because it contains a settings gear (⚙) button that gives access to the
full settings dialog. The cursor is always a hand cursor.

When there is only one provider with one game, the provider and game selection steps are
skipped automatically, but the popover still appears with the gear button accessible.

### Utility API

```java
LauncherStyle style = Settings.getInstance().getLauncher().getStyle();

// Resolve paths (returns null if no file exists for that style)
String logoPath = ResourceLoader.resolveGameLogoPath(game.getGameId(), style);
String iconPath = ResourceLoader.resolveGameIconPath(game.getGameId(), style);

// Load and render in one call (SVG or PNG, scaled)
ImageIcon icon = ResourceLoader.loadIcon(iconPath, 64, 64);
```

---

## EXE / SHELL games

For non-JAR games the Java Settings section is hidden from the Profile Editor and the Java
executable field is not shown. `GameLauncher` still passes the resolved executable path as the
process command, so ensure your `GameApi` and library manager return the correct binary path.
