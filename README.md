# ClassicLauncher

A generic, reusable game launcher base inspired by the old Minecraft Launcher aesthetics.
Fork it, wire up your own game's API and account system, and ship a launcher in minutes.

---

## Features

- **Two built-in UI styles** — `ALPHA` (2010 login-window look, incomplete) and `V1_1` (2013 tabbed launcher look)
- **Account provider system** — plug in any authentication method (form, OAuth browser flow)
- **Profile management** — per-profile version, resolution, JVM args, and launcher visibility settings
- **Platform-aware data directory** — `%APPDATA%\<name>` · `~/Library/Application Support/<name>` · `~/.config/<name>`
- **SVG icon support** for account providers via [JSVG](https://github.com/weissbürger/jsvg)

---

## Quick Start

### Prerequisites

| Tool  | Version                                          |
|-------|--------------------------------------------------|
| Java  | 8+ (built with Java 25, targets Java 8 bytecode) |
| Maven | 3.6+                                             |

### Build

```bash
mvn package
```

The shaded JAR is produced at `target/ClassicLauncher-1.0-SNAPSHOT.jar`.

### Run

```bash
java -jar target/ClassicLauncher-1.0-SNAPSHOT.jar
```

---

## Forking

See [`docs/fork-guide.md`](docs/fork-guide.md) for a step-by-step walkthrough.

The minimum changes required:

1. `Main.java` — change `LauncherContext.initialize("launcher")` to your game name
2. `settings.yml` — set `ui.style: V1_1` and `update-notes.url: <your patch notes URL>`
3. `src/main/resources/alpha/assets/background.png` — supply a 16×16 dark tile texture
4. Implement or register an `AccountProvider` (see [`docs/account-providers.md`](docs/account-providers.md))
5. Implement `Api.getAvailableVersions()` (see [`docs/api-integration.md`](docs/api-integration.md))

---

## Project Structure

```
src/main/java/.../launcher/
  Main.java                   ← entry point
  LauncherContext.java        ← platform data directory resolution
  account/
    Account.java              ← abstract account base
    AccountProvider.java      ← abstract provider (auth metadata + factories)
    AccountType.java          ← built-in type ID constants
    Accounts.java             ← load/save/register providers
    AuthMethod.java           ← FORM | BROWSER
    BrowserAuthHelper.java    ← Desktop.browse() + local callback HTTP server
    OfflineAccount.java
    OfflineAccountProvider.java
  api/
    Api.java                  ← stub version API client (override in fork)
  profile/
    Profile.java              ← Lombok @Builder
    Profiles.java
    LauncherVisibility.java
  settings/
    Settings.java             ← singleton; owns Accounts, Profiles, LauncherSettings, AccountSettings
    LauncherSettings.java
    AccountSettings.java
    LauncherStyle.java
  version/
    Version.java
    VersionType.java
  alpha/
    LauncherAlpha.java        ← 2010-era login window UI
    BackgroundPanel.java
    LoginPanel.java
  v1_1/
    LauncherV1_1.java         ← 2013-era tabbed launcher UI
    DarkBackgroundPanel.java
    BottomBar.java
    LoginScreen.java
    ProfileEditorDialog.java
    tabs/
      UpdateNotesTab.java
      LauncherLogTab.java
      ProfileEditorTab.java
```

## Planned Features

* Separate accounts based on selected provider/game
* Separate profiles based on selected provider/game
* Default profile settings based on provider/game
* Refactor LauncherAlpha, LauncherV1_1 and LauncherStyle
* Add context menu in Profile Editor with the following options:
    * Add Profile
    * Copy Profile
    * Delete Profile
    * Open Game Folder
* Add logout option per-account within LoginScreen
* Complete ALPHA style

---

## Code formatting

All code must be formatted before commit. This project uses
[Spotless](https://github.com/diffplug/spotless) with the Eclipse JDT formatter
(see `eclipse-formatter.xml`). IntelliJ IDEA picks up the committed `.idea/codeStyles/`
automatically.

```bash
# Check formatting
mvn spotless:check

# Apply formatting
mvn spotless:apply
```

It is recommended to install the pre-commit hook so formatting is applied automatically
on every `git commit`:

```bash
pip install pre-commit   # or: brew install pre-commit
pre-commit install
```

See [`Formatting.md`](Formatting.md) for the full style guide.

---

## License

This project is provided as a base for downstream forks. Customize and redistribute freely.
