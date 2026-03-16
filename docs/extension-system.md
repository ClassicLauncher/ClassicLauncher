# Extension System

The launcher supports independently distributed Maven artifacts that can add account providers,
games, APIs, and other functionality without modifying the launcher source.

---

## Manifest YAML format

Each extension must publish a manifest YAML file at a stable URL. The format is:

```yaml
name: My Extension
description: Adds custom game support
minLauncherVersion: "1.0.0"
pageUrl: https://example.com/my-extension
maven:
  groupId: com.example
  artifactId: my-ext
  version: 1.2.3
repositories:
  0:
    url: https://repo.example.com/maven2/
dependencies:
  0:
    groupId: com.dep
    artifactId: dep-lib
    version: 1.0.0
    repositories:
      0:
        url: https://repo.dep.com/maven2/
requiredExtensions:
  0:
    url: https://example.com/base-extension/manifest.yml
```

Fields:

| Field                | Required | Description                                                                          |
|----------------------|----------|--------------------------------------------------------------------------------------|
| `name`               | yes      | Human-readable extension name shown in the UI                                        |
| `description`        | no       | Short description shown in the extension card                                        |
| `minLauncherVersion` | no       | Minimum launcher version required (semver, default `"0.0.0"`)                        |
| `pageUrl`            | no       | URL of a page with details, screenshots, or documentation                            |
| `maven.groupId`      | yes      | Maven group ID of the extension artifact                                             |
| `maven.artifactId`   | yes      | Maven artifact ID                                                                    |
| `maven.version`      | yes      | Version to install                                                                   |
| `repositories`       | no       | Indexed list of Maven repository URLs for the extension artifact                     |
| `dependencies`       | no       | Indexed list of Maven JARs downloaded before the extension JAR is loaded (e.g. Gson) |
| `requiredExtensions` | no       | Indexed list of other extension manifest URLs that must be installed first           |

Each `dependencies` entry has: `groupId`, `artifactId`, `version`, and an optional nested
`repositories` list.

Each `requiredExtensions` entry has a single `url` field pointing to another manifest.

---

## Extension icon

Place an icon file at the root of your extension JAR's resources:

```
src/main/resources/icon.svg   ← preferred (vector, scales cleanly)
src/main/resources/icon.png   ← fallback raster
```

The launcher checks for `icon.svg` first, then `icon.png`. At install and update time it
extracts whichever is found into `<dataDir>/extensions/icons/<id>.<ext>`. This cached copy
is used by the UI at all times, including when the extension is disabled — so the icon is
always visible without re-reading the JAR.

If no icon is found, the UI generates a coloured circle with the extension's initials.

---

## Required extensions (dependency chains)

When an extension requires another (e.g. a game extension requiring an auth extension
that provides the `AccountProvider`), declare it:

```yaml
requiredExtensions:
  0:
    url: https://example.com/auth-extension/manifest.yml
```

### What the launcher does at install time

When a user installs an extension that has `requiredExtensions`:

1. The launcher fetches each required manifest.
2. Recursively resolves their requirements (full transitive closure, topological sort).
3. Skips any extension that is already installed (matched by Maven coordinates `groupId:artifactId`).
4. For each new dependency, shows a confirmation dialog with:
    - Which installed extension requires it ("X requires Y")
    - The dependency's name, description, and version
    - A clickable link to the dependency's `pageUrl` (if set)
5. If the user declines any dependency, the entire installation is aborted.
6. On full acceptance, all dependencies are installed in order (deepest first), then the
   root extension is installed last.

### What the launcher does at load time

On every startup, `Extensions.loadAll()` orders enabled extensions topologically by their
`requiredExtensions` graph so that each dependency's JAR is on the classpath before the
extension that needs it is scanned.

Each `requiredExtensions` URL is resolved to an installed extension by reading the cached
manifest file at `<dataDir>/extensions/manifests/<url-hash>.yml` to extract its Maven
coordinates (`groupId:artifactId`), then looking up that coordinate key in the installed
records. If the cached manifest is missing or its coordinates don't match any installed
record, the method falls back to matching the URL against each record's stored
`manifestUrl`. A dependency that cannot be resolved or is disabled is simply skipped in
the ordering.

### Enabling and disabling with cascade

The Extensions settings panel enforces dependency relationships when the user clicks the
status indicator on an extension card:

1. A base "Enable / Disable extension?" confirmation is shown first.
2. If other extensions are affected by the change, a second cascade dialog lists them:
    - **Disabling:** all enabled extensions that transitively depend on the target are listed.
      On accept, all are disabled together.
    - **Enabling:** all disabled extensions that the target transitively requires are listed.
      On accept, all required extensions are enabled first, then the target.
3. After any successful toggle, a **Restart Required** dialog informs the user that the
   launcher must be restarted for the change to take effect.

### Cycle detection

If extension A requires B, and B requires A (or any longer cycle), the launcher detects
the cycle at startup, automatically disables all extensions in the cycle, and shows a
warning dialog listing the affected extensions.

Cycle detection also applies at install time: the resolver returns an error immediately
if the chain leads back to an already-visited coordinate key.

### Diamond dependencies

If A requires B and C, and both B and C require D, D is installed once — the resolver
deduplicates by Maven coordinates (`groupId:artifactId`). At load time the topological
sort also handles diamonds correctly.

---

## Implementing LauncherExtension

Your extension JAR must contain at least one class that implements
`extension.net.classiclauncher.LauncherExtension` with a **public no-arg constructor**.
The launcher discovers it by scanning the JAR at startup.

```java
package com.example.myext;

import account.net.classiclauncher.AccountType;
import account.net.classiclauncher.Accounts;
import extension.net.classiclauncher.LauncherExtension;
import settings.net.classiclauncher.Settings;

public class MyExtension implements LauncherExtension {

	@Override
	public void onLoad(Settings settings) {
		// Register the type in the AccountType registry for introspection tools.
		AccountType.register(MyAccountType.MY_TYPE, MyAccount.CLASS, MyAccountProvider.CLASS);

		// Register the provider via onReady so it is safely deferred until
		// the bootstrap signals that Accounts is fully initialised.
		Accounts.onReady(accounts -> accounts.registerProvider(new MyAccountProvider()));
	}
}
```

### What you can do in onLoad

- Register custom settings pages via `settings.addSettingsPage(myPage)` (see "Custom Settings Pages" below)
- Register account providers via `Accounts.onReady`
- Register account types in the `AccountType` registry for introspection:
  ```java
  AccountType.register(MyAccountType.MY_TYPE, MyAccount.CLASS, MyAccountProvider.CLASS);
  ```
- Set the default game: `LauncherContext.getInstance().setDefaultGame(myGame)`
- Read/write any file under the data directory via `LauncherContext.getInstance().resolve(...)`
- Log to stdout/stderr (launcher log tab captures system output)

**Defining a type ID constant** — never add extension-specific type IDs to the Launcher core's
`AccountType` class. Define your constant in your own extension module instead:

```java
public final class MyAccountType {
    public static final String MY_TYPE = "MY_TYPE";
    private MyAccountType() {}
}
```

Then register it and call `Accounts.onReady` in the same `onLoad`:

```java
@Override
public void onLoad(Settings settings) {
    AccountType.register(MyAccountType.MY_TYPE, MyAccount.CLASS, MyAccountProvider.CLASS);
    Accounts.onReady(accounts -> accounts.registerProvider(new MyAccountProvider()));
}
```

`AccountType.register()` is for introspection (type → class lookup). `Accounts.registerProvider()`
is what actually wires the type into the deserialization and UI systems. Both are needed.

---

## Implementing AccountProvider in an extension

```java
public class MyAccountProvider extends AccountProvider {

	@Override
	public String getTypeId() {
		return MyAccountType.MY_TYPE;
	}

	@Override
	public String getDisplayName() {
		return "My Service";
	}

	@Override
	public String getIconResourcePath() {
		return "/icons/my-service.svg";
	}

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
	public String getUpdateNotesUrl() {
		return "https://my-game.com/news";
	}

	@Override
	public List<Game> getGames() {
		return Collections.singletonList(MY_GAME);
	}

	// getApiForGame(Game) not overridden — uses game.createApi() (MY_GAME's apiFactory)
	// getBackgroundRenderer(Game, LauncherStyle) not overridden — uses game.createBackgroundRenderer(style)
	// onGameSelected(Game, LauncherStyle) not overridden — no-op by default

	@Override
	public Account createFromForm(String username, char[] password) { ...}

	@Override
	public void startBrowserAuth(Consumer<Account> onComplete, Consumer<String> onError) {
		throw new UnsupportedOperationException("FORM provider");
	}

	@Override
	public Account fromConfig(String id, YmlConfig config) {
		return MyAccount.fromConfig(id, config);
	}
}
```

See [`account-providers.md`](account-providers.md) and [`api-integration.md`](api-integration.md)
for full details.

---

## Custom Settings Pages

Extensions can contribute their own pages to the settings panel by extending `SettingsPage` and
registering them in `onLoad`:

```java
@Override
public void onLoad(Settings settings) {
    settings.addSettingsPage(new MyExtensionSettingsPage());
}
```

The page appears in the sidebar alongside the built-in pages, ordered by priority (default 100
for extension-registered pages). Each page gets the standard header/body/footer layout
automatically via `buildPage(PageLayout)`.

```java
public class MyExtensionSettingsPage extends SettingsPage {

    public MyExtensionSettingsPage() {
        super("my-ext-settings", "My Extension", 100);
        JPanel body = new JPanel();
        // ... build your settings UI ...
        buildPage(new PageLayout()
                .title("My Extension Settings")
                .body(body)
                .footerAction(new JButton("Save")));
    }
}
```

### Built-in page priorities

| Page | Priority | ID |
|------|----------|----|
| Launcher | 10 | `launcher` |
| Java | 20 | `java` |
| Extensions | 30 | `extensions` |
| Updates | 40 | `updates` |
| Extension-registered | 100 (suggested) | custom |

---

## Extensions settings panel

The **Settings → Extensions** panel shows all installed extensions as a compact card grid.
Each card contains:

| Element              | Description                                                                                                                                                                                                                                            |
|----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Icon                 | Loaded from the disk cache (`<dataDir>/extensions/icons/<id>.svg/png`). Falls back to a coloured circle with the extension's initials if no icon was bundled.                                                                                          |
| Name + version       | Bold name with the installed version below it.                                                                                                                                                                                                         |
| Used by N extensions | Appears under the version when at least one other installed extension requires this one. Hovering shows a tooltip listing their names.                                                                                                                 |
| Status dot           | Green = enabled, red = disabled. Click to toggle with cascade confirmation and restart prompt.                                                                                                                                                         |
| ⋮ kebab menu         | **Update** — re-fetches the manifest and downloads a newer JAR if available. **Remove** — uninstalls the extension (with cascade warning if dependents exist). **View Details** — opens `pageUrl` in the system browser (disabled when no URL is set). |

Successful enables and disables show a **Restart Required** dialog.

### Local installation

The install dialog includes a **Local…** button for installing extensions directly from disk,
without publishing to a Maven repository. This is useful during development and testing.

When clicked:

1. A file chooser opens for the **manifest YAML** file (`.yml` / `.yaml`).
2. The manifest is parsed locally via `ExtensionManifest.fromFile()` — no network access.
3. A second file chooser opens for the **extension JAR** file.
4. The JAR is copied to the standard `<dataDir>/libs/maven/` path.
5. Runtime dependencies declared in the manifest are downloaded normally from their repositories.
6. The extension record is created, icon extracted, and a restart prompt is shown.

The local manifest uses the same YAML format as a remote one. The `repositories` block for the
extension artifact itself is not needed (the JAR is provided directly), but dependency repositories
are still required for any runtime libraries the extension needs.

### Installation progress dialog

When the user installs an extension (including its dependency chain), a modal
**Installing Extension** dialog is shown with real-time progress:

- A **scrollable log** shows each step as it happens: manifest fetches, file downloads
  (with size), cached-file skips, icon extraction, and record saving.
- A **progress bar** tracks the current file download (determinate when the server
  supplies a `Content-Length` header, indeterminate for unknown-size downloads).
- A **bytes label** shows how much of the current file has been transferred
  (e.g. `2.34 MB / 5.10 MB`).
- The **OK** button is disabled during installation and is enabled when the install
  completes or fails, allowing the user to review the log before dismissing.
- A **Restart Required** dialog is shown after the user clicks OK on a successful install.

---

## Publishing workflow

1. Build your extension JAR and publish it to a Maven repository.
2. Place your icon at `src/main/resources/icon.svg` (or `icon.png`) so it is bundled in the JAR.
3. Write a manifest YAML and host it at a stable HTTPS URL.
4. Users install the extension from the **Settings → Extensions** panel by pasting the manifest URL.
5. If your extension requires others, declare them in `requiredExtensions` — the launcher handles
   prompting and installing the chain automatically.

---

## Version constraints

The `minLauncherVersion` field prevents the extension from loading on incompatible launcher
versions. Use standard semver (e.g. `"1.2.0"`). The launcher compares
`LauncherVersion.VERSION` against this value; if the launcher is older, installation is refused
with a clear error message.

Non-numeric suffixes (e.g. `-SNAPSHOT`) are stripped before comparison, so `1.0-SNAPSHOT` is
treated as `1.0.0`.

---

## Auto-update

When a record has `autoUpdate: true` (the default for newly installed extensions), the launcher
can check for updates by calling `Extensions.checkForUpdates()`. This re-fetches each
extension's manifest and returns a list of records with a newer version available. Call
`Extensions.update(id)` to apply an individual update. Updating automatically re-extracts the
icon from the new JAR.

Users can toggle auto-update per extension from the Extensions settings panel.

---

## Local file layout

Installed extension data is stored under the launcher's data directory:

```
<dataDir>/extensions/<uuid>.yml         — record file (one per extension)
<dataDir>/extensions/manifests/         — cached remote manifests (by URL hash)
<dataDir>/extensions/icons/             — cached extension icons extracted from JARs
<dataDir>/libs/maven/                   — downloaded JARs (LibraryManager layout)
```

Icon files follow the naming convention `<uuid>.svg` or `<uuid>.png`, matching the record's
UUID. They are extracted automatically at install time and deleted at uninstall time.

Cached manifest files follow the naming convention `<Math.abs(url.hashCode())>.yml`. When
an extension is uninstalled, its cached manifest is also deleted (if the record has a
non-empty `manifestUrl`).

To uninstall an extension completely, delete its record file, icon cache file, cached
manifest, and the corresponding entry in `libs/maven/`. The Extensions settings panel
**Remove** option handles this automatically.

---

## Startup issue reporting

If any problems are detected during `Extensions.loadAll()` — circular dependencies, missing
or corrupt JARs, or exceptions thrown by `onLoad` — the affected extensions are automatically
disabled (and saved to disk) and the issues are accumulated in `Extensions.getLoadIssues()`.
After the launcher window opens, all issues are shown together in a single warning dialog so
the user is informed without blocking the startup sequence.
