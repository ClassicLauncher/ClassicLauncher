# JRE Management

The launcher ships a built-in Java Runtime Environment manager that auto-detects installed JVMs,
lets users add custom installations, and wires selected JREs into individual profiles.

---

## Architecture

| Class              | Responsibility                                                                                              |
|--------------------|-------------------------------------------------------------------------------------------------------------|
| `JavaInstallation` | Immutable value object: id, display name, version string, executable path, auto-detected flag, default flag |
| `JavaDetector`     | Platform-specific scan returning a deduplicated list of `JavaInstallation`s                                 |
| `JavaManager`      | CRUD + persistence; owned by `Settings`; one `JavaManager` per launcher instance                            |

---

## JavaManager API

```java
JavaManager jre = Settings.getInstance().getJavaManager();

// Read
List<JavaInstallation> all = jre.getAll();
Optional<JavaInstallation> def = jre.getDefault();

// Mutate
jre.

add(JavaInstallation.manual("/usr/bin/java", "17.0.9"));
		jre.

remove(id);               // deletes <dataDir>/jre/<id>.yml
jre.

setDefault(id);           // marks one as default, clears flag from others

// Auto-detect (runs synchronously — call from background thread)
int added = jre.autoDetect(); // returns count of newly found installations
```

All mutations persist immediately to `<dataDir>/jre/<uuid>.yml`.

---

## Auto-detection sources

| Platform    | Sources                                                                                      |
|-------------|----------------------------------------------------------------------------------------------|
| **macOS**   | `/usr/libexec/java_home -V` output                                                           |
| **Linux**   | `/usr/lib/jvm/` directory scan + `which java` symlink resolution                             |
| **Windows** | `JAVA_HOME`, `%ProgramFiles%\Java\`, Eclipse Adoptium, Microsoft, BellSoft, Azul vendor dirs |
| **All**     | `System.getProperty("java.home")` (the JVM running the launcher)                             |

Duplicate installations (same canonical executable path) are silently skipped.

---

## Resolving the JRE for launch

A `Profile` stores `javaExecutable` as either:

- `null` — use `JavaManager.getDefault()`, fall back to `java` on `PATH`
- An absolute path — use that binary directly

`GameLauncher.launchAsync()` reads the profile's `javaExecutable` when building the process
command. The Profile Editor's JRE combo box writes the selected installation's executable path
(or `null` for "System Default") into the profile when saved.

---

## Adding a custom JRE at runtime

```java
String path = "/opt/my-jdk/bin/java";
String version = JavaDetector.queryVersion(path); // runs java -version
JavaInstallation inst = JavaInstallation.manual(path, version);
Settings.

getInstance().

getJavaManager().

add(inst);
```

`add()` deduplicates by canonical path, so calling it twice with the same binary is safe.

---

## UI — Settings tab

The V1_1 launcher exposes the JRE manager under **Settings → Java**. All four built-in sections
are registered in this order:

```java
SettingsPanel settingsPanel = new SettingsPanel();
settingsPanel.addSection("Launcher",   new LauncherSettingsPanel(settings.getLauncher()));
settingsPanel.addSection("Java",       new JavaSettingsPanel(settings.getJavaManager()));
settingsPanel.addSection("Extensions", new ExtensionSettingsPanel(settings.getExtensions()));
settingsPanel.addSection("Updates",
        new UpdateSettingsPanel(settings.getLauncher(), settings.getReleaseSource()));
```

`SettingsPanel` can be embedded as a tab (`JTabbedPane`) or wrapped in a `JDialog` — it has no
frame dependency. To navigate to a specific section programmatically:

```java
settingsPanel.selectSection("Java");
```
