# Auto-Update System

The launcher checks for newer versions on startup via a pluggable release source and presents a
multi-version changelog dialog. Users can install the correct artifact for their platform, skip a
specific version, or disable the feature entirely.

---

## Architecture

The update system is split into three packages for modularity:

### Core — `net.classiclauncher.launcher.update`

| Class | Responsibility |
|---|---|
| `ReleaseSource` | `@FunctionalInterface` — abstracts how releases are fetched; implementations can use GitHub, a custom server, or a static list (lambdas work for testing) |
| `UpdateChecker` | Orchestrates the check: calls `ReleaseSource.fetchReleases()`, filters by version, handles skip logic, dispatches to EDT |
| `UpdatePlan` | Result DTO: current version, list of newer releases sorted oldest→newest |
| `ReleaseInfo` | Immutable DTO: tag name, title, markdown body, list of `AssetInfo` |
| `AssetInfo` | Immutable DTO: file name, download URL, size in bytes |

### Install — `net.classiclauncher.launcher.update.install`

| Class | Responsibility |
|---|---|
| `ArtifactSelector` | Maps `Platform` × `DistributionMode` to the correct asset by file extension |
| `UpdateInstaller` | Streams asset to a temp file; per-platform install strategy (see below) |
| `DistributionMode` | `JAR` vs `INSTALLER` enum; `current()` is the single canonical `jpackage.app-path` check |

### GitHub Source — `net.classiclauncher.launcher.update.source.github`

| Class | Responsibility |
|---|---|
| `GitHubReleaseSource` | `ReleaseSource` implementation for GitHub Releases API; `fromLauncherConfig()` factory for launcher's own updates |
| `GitHubReleasesClient` | HTTP-only layer; `GET /repos/{owner}/{repo}/releases`; redirect-following, 10 MiB cap, timeouts |
| `GitHubJsonParser` | Package-private, dependency-free single-pass JSON scanner; filters drafts and pre-releases |

### UI — `net.classiclauncher.launcher.ui.update` and `net.classiclauncher.launcher.ui.settings`

| Class | Package | Responsibility |
|---|---|---|
| `UpdateDialog` | `ui.update` | 700×500 modal dialog; CommonMark changelogs; version titles link to the GitHub release page |
| `SplitInstallButton` | `ui.update` | Main "Install" button + dropdown arrow for per-version selection |
| `UpdateDownloadDialog` | `ui.update` | Progress dialog with label, progress bar, and 60 ms EDT throttle |
| `UpdateSettingsPanel` | `ui.settings` | Settings page (extends `SettingsPage`): enable/disable toggle, skipped-version display, "Check Now" footer button; accepts a `ReleaseSource` |

---

## Pluggable release sources

The `ReleaseSource` interface is a `@FunctionalInterface`, making it trivial to implement:

```java
// Lambda for testing
ReleaseSource testSource = () -> Arrays.asList(
    new ReleaseInfo("v1.0.1", "Fix", "", assets)
);

// GitHub source for the launcher itself
ReleaseSource source = GitHubReleaseSource.fromLauncherConfig();

// GitHub source for an extension
ReleaseSource extSource = new GitHubReleaseSource("my-org/my-extension");
```

Extensions can register their own update feeds without touching the core update logic.

---

## Release source registry

`Settings` maintains a named registry of release sources. Multiple sources can coexist — each
identified by a unique string ID. The launcher's own source uses the well-known ID
`Settings.LAUNCHER_SOURCE_ID` (`"launcher"`).

### API

```java
Settings settings = Settings.getInstance();

// Set the launcher's own source (convenience — same as addReleaseSource("launcher", source))
settings.setReleaseSource(GitHubReleaseSource.fromLauncherConfig());

// Get the launcher's own source
ReleaseSource launcherSource = settings.getReleaseSource();

// Register an additional source (e.g. for an extension's own updates)
settings.addReleaseSource("my-extension", new GitHubReleaseSource("my-org/my-extension"));

// Look up a source by ID
ReleaseSource extSource = settings.getReleaseSource("my-extension");

// Remove a source
settings.removeReleaseSource("my-extension");

// Iterate over all registered sources
Map<String, ReleaseSource> all = settings.getReleaseSources();
```

### From an extension

```java
public void onLoad(Settings settings) {
    // Replace the launcher's default source with a custom one
    settings.setReleaseSource(new MyCustomReleaseSource());

    // Or register an additional source for extension-specific updates
    settings.addReleaseSource("my-extension",
            new GitHubReleaseSource("my-org/my-extension"));
}
```

The registry is thread-safe (`ConcurrentHashMap`). Sources can be added, replaced, or removed at
any time.

---

## Startup flow

```
Main.main()
  └─ settings.setReleaseSource(GitHubReleaseSource.fromLauncherConfig())
  └─ Extensions.loadAll() — extensions may add/replace sources in onLoad()
  └─ source = settings.getReleaseSource()
  └─ UpdateChecker.checkAsync(source, version, settings, windowSupplier)  ← daemon thread
        └─ source.fetchReleases()                                         ← via ReleaseSource
        └─ filter releases newer than currentVersion
        └─ sort ascending (oldest → newest)
        └─ apply skipped-version filter (if any)
        └─ if plan.hasUpdate() → SwingUtilities.invokeLater → UpdateDialog.setVisible(true)
```

A failed update check (network error, parse failure) is logged at `WARN` and silently suppressed
— it never interrupts launcher startup.

---

## DistributionMode

`DistributionMode.current()` is the single canonical location that determines whether the launcher
is running as a plain JAR or as a jpackage installer:

```java
// INSTALLER when jpackage set this property at launch time, JAR otherwise
DistributionMode mode = DistributionMode.current();
```

`JavaManager.detectBundledInstallation()` delegates here rather than re-checking the system
property inline, ensuring there is only one place to change if the detection logic ever needs
updating.

---

## Artifact selection

`ArtifactSelector.select(release, platform, mode)` picks the first asset whose name ends with:

| Platform + mode | Extension searched |
|---|---|
| Any + `JAR` | `.jar` |
| `MACOS` + `INSTALLER` | `.dmg` |
| `WINDOWS` + `INSTALLER` | `.msi` |
| `LINUX` + `INSTALLER` | `.deb` (preferred), then `.rpm` |
| `UNKNOWN` + any | `.jar` |

Returns `Optional.empty()` when no matching asset is found. The dialog shows a warning in that
case and prompts the user to download manually.

---

## Install strategies

`UpdateInstaller.install()` streams the asset to a temp file, then:

| Mode | Strategy |
|---|---|
| **JAR** | Writes a shell script (Unix) or `.bat` (Windows) that sleeps 3 s, overwrites the running JAR, and relaunches it; spawns the script and calls `System.exit(0)` |
| **macOS** | `open <file>.dmg` and `System.exit(0)` — the system handles the rest |
| **Windows** | `msiexec /i <file>.msi` and `System.exit(0)` |
| **Linux DEB/RPM** | Shows a `JOptionPane` with `sudo dpkg -i <path>` / `sudo rpm -i <path>` instructions; does **not** exit — the user runs the command manually |

---

## Settings keys

Stored in `<dataDir>/settings.yml` via `LauncherSettings`:

| Key | Type | Default | Description |
|---|---|---|---|
| `update.check-enabled` | boolean | `true` | Whether to check for updates on startup |
| `update.skipped-version` | string | `""` | Version to silently skip (e.g. `"1.2.3"`); empty = no skip |

### API

```java
LauncherSettings s = settings.getLauncher();

s.isUpdateCheckEnabled();            // true/false
s.setUpdateCheckEnabled(false);      // disables startup check

s.getSkippedVersion();               // returns null when empty
s.setSkippedVersion("1.2.3");        // skip this version on next startup check
s.setSkippedVersion(null);           // clear the skip
```

---

## Manual check

To trigger a check from code (e.g. from a button handler) — always ignores the skipped-version
filter so the user sees all available updates:

```java
ReleaseSource source = Settings.getInstance().getReleaseSource();
try {
    UpdatePlan plan = UpdateChecker.checkManual(source, LauncherVersion.VERSION, settings.getLauncher());
    if (plan != null && plan.hasUpdate()) {
        new UpdateDialog(parentWindow, plan, settings.getLauncher()).setVisible(true);
    }
} catch (IOException e) {
    // handle network error
}
```

`UpdateSettingsPanel` already wires this up behind the "Check Now" button on a `SwingWorker`.

---

## GitHub repository configuration

Set `github.repo` in `pom.xml` — it is filtered into `launcher-version.properties` at build time:

```xml
<properties>
    <github.repo>your-org/your-launcher</github.repo>
</properties>
```

`LauncherVersion.GITHUB_REPO` reads this value at runtime. If the property is left as the
Maven placeholder `${github.repo}` (i.e. the value was never substituted),
`GitHubReleaseSource.fromLauncherConfig()` returns `null`, `settings.setReleaseSource(null)`
removes the launcher source, and the startup check is skipped.

---

## Visual testing

`UpdateDialogPreview` in `src/test/java/.../update/` is a standalone `main()` class that opens
the dialog with three fake releases and realistic markdown changelogs. It is **not** a JUnit test
and does not run in CI.

Run it directly from the IDE (right-click → Run) to verify:

- Changelog rendering (headings, lists, bold, code, inline links)
- Version titles as clickable links (open the system browser)
- Later / Skip This Version / Disable Update Checker / Install buttons
- Split install dropdown showing individual versions
- Dialog closes and the process exits cleanly

---

## Testing

Tests are organized by package:

### `net.classiclauncher.launcher.update`

| Test class | Coverage |
|---|---|
| `UpdateCheckerTest` | Lambda-based `ReleaseSource` tests: two newer versions sorted ascending, already-latest, empty source, network error, null source, skipped-version logic, tag `v`-prefix stripping, mixed older/newer versions, `checkManual` with null source |
| `ArtifactSelectorTest` | All platform × mode combinations, deb-over-rpm preference, empty/null asset list, unknown platform fallback |

### `net.classiclauncher.launcher.update.source.github`

| Test class | Coverage |
|---|---|
| `GitHubJsonParserTest` | Single/multiple releases, draft filter, pre-release filter, escape sequences (newline, quote, backslash, unicode, tab), null body, empty assets, missing asset fields, unknown fields skipped, missing tag_name |
| `GitHubReleaseSourceTest` | HTTP integration via `com.sun.net.httpserver.HttpServer`: fetches and parses releases, filters drafts/prereleases, empty array, network error, HTTP error, `fromLauncherConfig()` factory |

`UpdateCheckerTest` uses lambda `ReleaseSource` implementations — no HTTP server needed. Each test
gets an isolated `LauncherContext` injected via reflection to prevent settings from one test leaking
into the next when `XDG_CONFIG_HOME` is set in the CI environment.

Run all update tests:

```bash
mvn test -Dtest="UpdateCheckerTest,ArtifactSelectorTest,GitHubJsonParserTest,GitHubReleaseSourceTest"
```
