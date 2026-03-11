# Fork Customization Guide

This document walks you through turning ClassicLauncher base into a launcher for your own game.

---

## Step 1 — Name your launcher

Open `Main.java` and change the initialize call:

```java
// before
LauncherContext.initialize("launcher");

// after
LauncherContext.initialize("my-awesome-game");
```

This string becomes:

- The human-readable launcher name shown in the window title
- The name of the data directory created on the user's machine

---

## Step 2 — Choose a UI style

The UI style and update-notes URL can be changed at runtime via **Settings → Launcher** in the
V1_1 launcher (`LauncherSettingsPanel`). A restart is required for a style change to take effect.

To set the initial defaults before first launch, edit `LauncherSettings.java` or pre-populate
`<dataDir>/settings.yml`:

```yaml
ui:
  style: V1_1           # V1_1 (2013 tabbed) or ALPHA (2010 login window)

update-notes:
  url: "https://your-game.com/patch-notes"
```

### GameSelectorWidget — two-step provider + game picker

The `GameSelectorWidget` displayed above the login form opens a two-step selection popover:

1. **Provider step** — all registered providers shown as cards with their icons.
   Skipped automatically when only one provider is registered.
2. **Game step** — all games supported by the chosen provider, rendered with the
   per-provider game icon (or the game's default icon as fallback).
   Skipped automatically when the provider supports ≤ 1 game.

The selection callback receives both the chosen `AccountProvider` and `Game` (may be `null`
for game-agnostic providers):

```java
gameLogo = new GameSelectorWidget(170, 40, (provider, game) -> {
    selectedProvider = provider;
    updateProviderUI();
});
```

---

## Step 3 — Supply a background

Backgrounds are provided by extensions via the `BackgroundRenderer` system. The launcher core
contains **no background images** — when no extension provides a renderer, a solid `#1E1E1E`
gray fallback is used automatically.

### How it works

Each `Game` can declare a per-style background renderer via `backgroundRendererFactory`:

```java
Game.builder("my-game", "My Game", ExecutableType.JAR)
    .backgroundRendererFactory(style -> {
        switch (style) {
            case ALPHA:
                return new MyBackgroundRenderer("/assets/bg.png", 4, 0f, 1.0f);
            case V1_1:
                return new MyBackgroundRenderer("/assets/bg.png", 3, 0.25f, 1.3f);
            default:
                return null;  // gray fallback
        }
    })
    .build();
```

`BackgroundRenderer` is a `@FunctionalInterface` with a single method:

```java
void paint(Graphics2D g, int width, int height);
```

The renderer loads images from the extension's own classloader (via `getClass().getResourceAsStream()`),
so place your background texture inside the extension JAR's resources — not in the launcher core.

### Resolution chain

When the launcher needs a background:

1. `provider.getBackgroundRenderer(game, style)` — override per-provider if needed
2. Default delegates to `game.createBackgroundRenderer(style)` — the factory above
3. `null` → solid `#1E1E1E` gray fill (handled by `BackgroundPanel`)

### Dynamic updates

`LoginScreen` updates the background dynamically when the user switches provider or game via
the `GameSelectorWidget`, or picks a different existing account in switch mode. The main view
(`LauncherV1_1`) does not have a background — it uses a plain panel.

See [`game-abstraction.md`](game-abstraction.md) for the full background renderer API and
[`account-providers.md`](account-providers.md) for per-provider overrides.

---

## Step 4 — Register account providers

In `Main.java`, after `Settings settings = Settings.getInstance()`, register any custom providers:

```java
// built-in offline provider is already registered automatically
settings.getAccounts().registerProvider(new MyCustomAccountProvider());
```

When building extensions (independently distributed JARs), use `Accounts.onReady` instead so the
registration is safely deferred until the bootstrap finishes signalling:

```java
// inside LauncherExtension.onLoad(settings):
Accounts.onReady(accounts -> accounts.registerProvider(new MyCustomAccountProvider()));
```

See [`account-providers.md`](account-providers.md) for how to implement `AccountProvider`.

---

## Step 5 — Bundling launcher dependencies with libs.yml

If your fork depends on libraries that should not be shaded into the launcher JAR, list them in a
`libs.yml` file placed next to the JAR at runtime. The launcher will download and load them
automatically (only when running from a JAR — not in the IDE).

```yaml
repositories:
  0:
    url: https://repo.maven.apache.org/maven2/

dependencies:
  0:
    groupId: org.example
    artifactId: my-library
    version: 1.0.0
```

The downloaded JARs are cached in `<working-directory>/libs/maven/` and reused on subsequent
launches.

---

## Step 6 — Distributing functionality as extensions

Rather than bundling all account providers and game support directly in the launcher JAR, you can
ship them as independently installable extensions. This keeps the base launcher small and lets
users opt-in to additional games or auth methods.

Users install extensions from **Settings → Extensions** by pasting a manifest URL, or by clicking
**Local…** to install from a manifest YAML file and JAR on disk (useful during development).

**Extension icon convention** — place your icon at the root of the extension JAR's resources:

```
src/main/resources/icon.svg   ← preferred (vector)
src/main/resources/icon.png   ← raster fallback
```

The launcher extracts it at install time and caches it to disk so it is always visible in the
panel, even when the extension is disabled.

See [`extension-system.md`](extension-system.md) for the full manifest format, dependency
chain handling, and implementation guide.

---

## Step 7 — Configure the Game abstraction

The `Game` object tells the launcher what kind of binary your game uses, which version filter
checkboxes appear in the Profile Editor, how to fetch available versions, and what background
to display.

Build one and attach it to your account provider via `getGames()`:

```java
private static final Game MY_GAME = Game.builder("my-game", "My Game", ExecutableType.JAR)
		.versionFilter("snapshot", "Show snapshots")
		.versionFilter("old_beta", "Show old Beta versions")
		.apiFactory(MyVersionApi::new)                     // see Step 8
		.launchStrategyFactory(MyLaunchStrategy::new)      // optional — see game-abstraction.md
		.backgroundRendererFactory(style -> {              // see Step 3
			switch (style) {
				case V1_1:
					return new MyDarkRenderer();
				default:
					return null;
			}
		})
		.build();

@Override
public List<Game> getGames() {
	return Collections.singletonList(MY_GAME);
}
```

If every provider should share the same game, set a launcher-wide default instead:

```java
LauncherContext.getInstance().setDefaultGame(MY_GAME);
```

See [`game-abstraction.md`](game-abstraction.md) for the full `Game` API.

---

## Step 8 — Implement the version API

Extend `HttpGameApi` and override `getAvailableVersions()` and `getVersion(String)` to fetch
version metadata from your game server. Wire it to the game via `apiFactory`:

```java
public class MyVersionApi extends HttpGameApi {
    public MyVersionApi() { super("https://api.my-game.com"); }

    @Override
    public List<Version> getAvailableVersions() {
        try {
            String json = fetchText("/versions");
            return parseVersions(json);
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<Version> getVersion(String id) { ... }
}
```

Then on the `Game` builder:

```java
Game.builder("my-game", "My Game", ExecutableType.JAR)
    .apiFactory(MyVersionApi::new)
    .build();
```

If a specific account provider needs a *different* API for the same game (e.g. an archival
server with more versions), override `AccountProvider.getApiForGame(Game)` in that provider.

See [`api-integration.md`](api-integration.md) for full details and security notes.

---

## Step 9 — JRE management (optional)

The Settings tab in the V1_1 launcher includes a **Java Runtime Environments** sub-section. On
first launch the launcher auto-detects installed JVMs via `JavaDetector`. Users can also add a
JRE manually with a file picker and set a default. Profiles can select which JRE to use.

No extra configuration is required — `JavaManager` is created automatically by `Settings` and
persists JRE records under `<dataDir>/jre/<uuid>.yml`.

See [`jre-management.md`](jre-management.md) for advanced usage.

---

## Step 10 — Set the JAR manifest entry point

In `pom.xml`, add a `mainClass` to the shade plugin's manifest configuration:

```xml

<configuration>
    <archive>
        <manifest>
            <mainClass>net.classiclauncher.Mainnet.classiclauncher.Main</mainClass>
        </manifest>
    </archive>
    ...
</configuration>
```

---

## Step 11 — Build and distribute

```bash
mvn package
```

Ship `target/Launcher-1.0-SNAPSHOT.jar`. Users run it with:

```bash
java -jar Launcher-1.0-SNAPSHOT.jar
```
