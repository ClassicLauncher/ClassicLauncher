# Launch System

The launch system is responsible for preparing game files and constructing the process command that
starts a game. It is designed around two complementary abstractions: `LaunchStrategy` and
`LaunchProgress`, coordinated by `GameLauncher`.

---

## Key types

| Type             | Package           | Role                                                   |
|------------------|-------------------|--------------------------------------------------------|
| `LaunchStrategy` | `launcher.launch` | Prepare files + build the process command              |
| `LaunchProgress` | `launcher.launch` | Progress and log callbacks during launch               |
| `LaunchContext`  | `launcher.launch` | Immutable snapshot of everything needed for one launch |
| `GameLauncher`   | `launcher`        | Orchestrates the background launch thread              |

---

## LaunchContext

`LaunchContext` is an immutable value object created just before `GameLauncher.launchAsync` is
called. It carries every piece of data a strategy needs so that strategies themselves remain
stateless.

| Field           | Type               | Description                                                             |
|-----------------|--------------------|-------------------------------------------------------------------------|
| `account`       | `Account`          | The account that initiated the launch                                   |
| `game`          | `Game`             | The game being launched                                                 |
| `profile`       | `Profile`          | The active profile (JVM args, resolution, game directory override, …)   |
| `jre`           | `JavaInstallation` | The resolved JRE, or `null` when not applicable (EXE/SHELL games)       |
| `gameDataDir`   | `File`             | `<launcherDataDir>/games/<gameId>/` — canonical data root for this game |
| `gameDirectory` | `File`             | `profile.getGameDirectory()` if set, otherwise `gameDataDir`            |

`gameDataDir` always points to the launcher-managed data root. `gameDirectory` is where the game
process should use as its working directory (i.e. where the saves, screenshots, and config live).

---

## LaunchStrategy interface

```java
public interface LaunchStrategy {
    void prepare(LaunchContext ctx, LaunchProgress progress) throws Exception;
    List<String> buildCommand(LaunchContext ctx) throws Exception;
}
```

### `prepare`

Called on a background thread. Responsible for all blocking I/O before the game starts:

- Downloading and verifying game files (client JAR, libraries, assets, …)
- Extracting native libraries
- Ensuring a valid access token is available (blocking on async auth if necessary)
- Any other prerequisite work

`prepare` receives a `LaunchProgress` so it can report per-file download progress, write to the log
tab, and update the progress bar. If `prepare` throws, the launch is aborted and
`LaunchProgress.onLaunchComplete(false)` is called.

### `buildCommand`

Called immediately after `prepare` returns successfully, on the same background thread.
Returns the ordered list of strings that will be passed to `ProcessBuilder`. The first element is
the executable (e.g. `java` absolute path or a native binary), followed by all arguments.

If the strategy does not support launching (e.g. `NullLaunchStrategy`), `buildCommand` must throw
`UnsupportedOperationException`.

---

## Built-in strategies

### `NullLaunchStrategy`

Singleton (`NullLaunchStrategy.INSTANCE`). No-op `prepare`. `buildCommand` always throws
`UnsupportedOperationException`. Used as the ultimate fallback when no strategy can be resolved for
a given `ExecutableType`.

### `JarLaunchStrategy`

Singleton (`JarLaunchStrategy.INSTANCE`). Reads `libs.yml` and uses LibraryManager to resolve
the classpath. Intended for JAR-based games that do not need a custom prepare pipeline. Game extensions can replace this with a custom launch strategy via the `launchStrategyFactory`
on the `Game` builder.

### `ExeLaunchStrategy`

Singleton (`ExeLaunchStrategy.INSTANCE`). No-op `prepare`. `buildCommand` returns a command list
that invokes the game's executable directly from `ctx.getGameDirectory()`. Used for EXE and SHELL
`ExecutableType` games.

---

## Strategy selection

`Game.createLaunchStrategy()` is the default entry point for strategy resolution. The auto-default
table is:

| `ExecutableType` | Default strategy              |
|------------------|-------------------------------|
| `JAR`            | `JarLaunchStrategy.INSTANCE`  |
| `EXE`            | `ExeLaunchStrategy.INSTANCE`  |
| `SHELL`          | `ExeLaunchStrategy.INSTANCE`  |
| anything else    | `NullLaunchStrategy.INSTANCE` |

To override the default, pass a `Supplier<LaunchStrategy>` to `Game.Builder.launchStrategyFactory`:

```java
Game game = Game.builder("my-game", "My Game", ExecutableType.JAR)
        .launchStrategyFactory(MyLaunchStrategy::new)
        .build();
```

`AccountProvider.getLaunchStrategy(Game)` is called by `GameLauncher` first. Its default
implementation delegates to `game.createLaunchStrategy()`. Override it only when a specific
provider needs a different pipeline for the same game — the pattern mirrors `getApiForGame(Game)`.

```java

@Override
public LaunchStrategy getLaunchStrategy(Game game) {
	if ("my_game".equals(game.getGameId())) {
		return new MyCustomLaunchStrategy();
	}
	return game.createLaunchStrategy(); // default for all other games
}
```

---

## LaunchProgress interface

`LaunchProgress` is the callback interface through which a `LaunchStrategy` communicates progress
back to the UI. The launcher wires a concrete implementation that updates the log tab and progress
bar in `LauncherV1_1`.

| Method                                                            | Description                                                                                                   |
|-------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `void log(String message)`                                        | Appends a line to the launcher log tab; also written via Log4j2                                               |
| `void setTotalFiles(int total)`                                   | Shows the progress bar and sets its maximum value                                                             |
| `void fileCompleted()`                                            | Increments the progress bar by one step                                                                       |
| `void fileProgress(String fileName, long bytes, long totalBytes)` | Per-file byte-level progress; used to update a secondary label or tooltip                                     |
| `void onLaunchComplete(boolean success)`                          | Called when the game process exits (or fails to start); re-enables the Play button and hides the progress bar |

All methods may be called from a background thread. The concrete implementation in the launcher
dispatches UI updates via `SwingUtilities.invokeLater`.

---

## GameLauncher

`GameLauncher` is the single entry point for launching a game from the UI layer.

```java
GameLauncher launcher = new GameLauncher(frame);
launcher.launchAsync(ctx, progress);
```

### Constructor

`GameLauncher(JFrame frame)` — the frame is used for `LauncherVisibility` handling (hide or dispose
before the game starts, restore after it exits).

### `launchAsync(LaunchContext ctx, LaunchProgress progress)`

Spawns a daemon background thread that executes the following sequence:

1. **Visibility pre-launch** — based on `ctx.getProfile().getLauncherVisibility()`:
    - `CLOSE_LAUNCHER`: disposes the frame.
    - `HIDE_LAUNCHER`: hides the frame (makes it invisible).
    - `KEEP_OPEN`: no change.
2. **Strategy resolution** — calls `ctx.getAccount().getProvider().getLaunchStrategy(ctx.getGame())`,
   which by default delegates to `ctx.getGame().createLaunchStrategy()`.
3. **`strategy.prepare(ctx, progress)`** — blocking file I/O, token refresh, etc.
4. **`strategy.buildCommand(ctx)`** — constructs the process command list.
5. **`ProcessBuilder`** — redirects stderr into stdout (`redirectErrorStream(true)`), starts the
   process, and streams each output line to `progress.log(line)`.
6. **Wait for exit** — blocks until the process exits, then calls
   `progress.onLaunchComplete(exitCode == 0)`.
7. **Visibility post-launch** — if `HIDE_LAUNCHER` was used, the frame is made visible again
   after the process exits.

If any step throws an exception, `progress.log(exception message)` is called, followed by
`progress.onLaunchComplete(false)`.

---

## Implementing a custom launch strategy

Game extensions provide their own `LaunchStrategy` by setting `launchStrategyFactory` on the
`Game` builder. The strategy's `prepare` method handles all blocking I/O (downloading game files,
verifying checksums, ensuring valid tokens), while `buildCommand` assembles the final process
command.

```java
Game game = Game.builder("my-game", "My Game", ExecutableType.JAR)
        .launchStrategyFactory(MyLaunchStrategy::new)
        .build();
```

See the game extension's own documentation for details on its specific prepare pipeline and
command construction.

---

## See also

- [`game-abstraction.md`](game-abstraction.md) — `Game`, `ExecutableType`, `launchStrategyFactory`
- [`account-providers.md`](account-providers.md) — `AccountProvider.getLaunchStrategy`
