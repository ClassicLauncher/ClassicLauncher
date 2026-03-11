# Native Packaging

ClassicLauncher ships as both a plain fat JAR (runs on any JRE 8+) and as platform-native
installers (macOS .dmg, Windows .msi, Linux .deb/.rpm) that bundle JRE 24 so end users
never need to install Java themselves.

Native installers are produced with **jpackage** (bundled in JDK 14+) via the `native`
Maven profile. Each installer also registers the `classiclauncher://` URI scheme so that
a single browser link can trigger an extension install.

---

## Building locally

### Prerequisites

| Platform  | Requirement                                                                        |
|-----------|------------------------------------------------------------------------------------|
| All       | JDK 17+ on `PATH` / `JAVA_HOME` (jpackage requires 17+ to produce the bundled JRE) |
| macOS     | Xcode Command Line Tools (`xcode-select --install`)                                |
| Windows   | [WiX Toolset 3.x](https://wixtoolset.org/releases/) on `PATH`                      |
| Linux DEB | `fakeroot`, `dpkg` (`sudo apt install fakeroot dpkg`)                              |
| Linux RPM | `rpm-build` (`sudo apt install rpm` / `sudo dnf install rpm-build`)                |

### Commands

```bash
# macOS DMG
mvn package -Pnative -Djpackage.type=dmg -Djpackage.resourceDir=packaging/macos

# Windows MSI  (run from a Windows machine or CI)
mvn package -Pnative -Djpackage.type=msi -Djpackage.resourceDir=packaging/windows

# Linux DEB
mvn package -Pnative -Djpackage.type=deb -Djpackage.resourceDir=packaging/linux

# Linux RPM
mvn package -Pnative -Djpackage.type=rpm -Djpackage.resourceDir=packaging/linux

# macOS app-image (no DMG wrapper — useful for quick local testing)
mvn package -Pnative -Djpackage.type=app-image -Djpackage.resourceDir=packaging/macos
```

Output is written to `target/installer/`.

### Overriding the app version

The `native` profile uses `${jpackage.appVersion}` (default `1.0.0`) for the installer
version string. MSI requires a purely numeric version (`x.y.z`). Override on the command line:

```bash
mvn package -Pnative -Djpackage.type=msi -Djpackage.appVersion=2.0.1 \
    -Djpackage.resourceDir=packaging/windows
```

---

## Packaging resource directories

Each platform has a `packaging/<platform>/` directory that is passed to jpackage via
`--resource-dir`. Files placed here override jpackage's generated defaults.

### `packaging/macos/`

| File                   | Purpose                                                                                                               |
|------------------------|-----------------------------------------------------------------------------------------------------------------------|
| `Info.plist`           | Merged into the `.app` bundle's `Info.plist`; registers `classiclauncher://` and sets `LSMultipleInstancesProhibited` |
| `ClassicLauncher.icns` | App icon — **replace with a real `.icns` file** (create with `iconutil` from a 1024×1024 PNG)                         |

### `packaging/windows/`

| File                    | Purpose                                                                                        |
|-------------------------|------------------------------------------------------------------------------------------------|
| `protocol.wxi`          | WiX fragment included in the MSI; writes `HKCU\Software\Classes\classiclauncher` registry keys |
| `register-protocol.bat` | Manual URI scheme registration for ZIP/portable installs (no admin needed)                     |
| `ClassicLauncher.ico`   | App icon — **replace with a real `.ico` file** (multi-resolution, 256×256 recommended)         |

**Important:** The WiX fragment (`protocol.wxi`) contains a Component GUID
(`A1B2C3D4-E5F6-7890-ABCD-EF1234567890`). Replace this with a freshly generated UUID
**once** for your fork, then never change it, or upgrades will break:

```powershell
# PowerShell
[guid]::NewGuid()
```

```bash
# macOS / Linux
uuidgen
```

### `packaging/linux/`

| File                       | Purpose                                                                |
|----------------------------|------------------------------------------------------------------------|
| `classiclauncher.desktop`  | Desktop entry; declares `MimeType=x-scheme-handler/classiclauncher`    |
| `classiclauncher.postinst` | DEB post-install script; runs `xdg-mime` and `update-desktop-database` |
| `classiclauncher.png`      | App icon — **replace with a real 128×128 PNG**                         |

---

## Icon placeholders

The icon files in `packaging/` are placeholders. jpackage uses a default coffee-cup icon
if the file is missing or invalid. Replace them with real artwork before publishing:

| File to replace                         | Format                  | Tool                                 |
|-----------------------------------------|-------------------------|--------------------------------------|
| `packaging/macos/ClassicLauncher.icns`  | Apple Icon Image        | `iconutil -c icns <iconset.iconset>` |
| `packaging/windows/ClassicLauncher.ico` | Windows ICO (multi-res) | IcoFX, GIMP, or Inkscape             |
| `packaging/linux/classiclauncher.png`   | PNG 128×128             | Any image editor                     |

---

## URI scheme: `classiclauncher://`

The `classiclauncher://` URI scheme lets extension authors publish a one-click install link.

### Supported formats

```
# Remote install (most common)
classiclauncher://install?url=https%3A%2F%2Fexample.com%2Fextension.yml

# Local install (dev/testing)
classiclauncher://install?manifest=%2Fpath%2Fto%2Fmanifest.yml&jar=%2Fpath%2Fto%2Fext.jar
```

### How it is delivered

| Platform | Delivery mechanism                                                                  |
|----------|-------------------------------------------------------------------------------------|
| macOS    | Apple Events → `Desktop.setOpenURIHandler` (registered at startup via reflection)   |
| Windows  | `args[0]` when the OS launches the executable via the registry `shell\open\command` |
| Linux    | `args[0]` via the `.desktop` file `Exec=/path/to/ClassicLauncher %u`                |

### Single-instance IPC

On Windows and Linux the OS creates a **new process** for each URI click. The launcher uses
a loopback socket (`SingleInstanceManager`) to forward the URI to the already-running
instance and immediately exit, so the user sees one confirm dialog in the existing window.

The IPC port is deterministic: `49152 + (abs("launcher".hashCode()) % 16383)` — stable
across restarts and consistent between all instances of the same launcher name.

---

## CI / GitHub Actions

The workflow `.github/workflows/native-build.yml` runs on every push to `main` and on
manual dispatch. It produces four artifact types:

| Job        | Runner           | Artifact                          |
|------------|------------------|-----------------------------------|
| `jar-jdk8` | `ubuntu-latest`  | `jar-jdk8` — fat JAR (JDK 8)      |
| `macos`    | `macos-latest`   | `macos-dmg` — `.dmg` (JDK 24)     |
| `windows`  | `windows-latest` | `windows-msi` — `.msi` (JDK 24)   |
| `linux`    | `ubuntu-latest`  | `linux-deb`, `linux-rpm` (JDK 24) |

Artifacts are available in the GitHub Actions run summary for 90 days.

### Release workflow (suggested)

1. Tag a release: `git tag v1.2.3 && git push --tags`
2. Add a `push: tags: ['v*']` trigger to the workflow (or use `workflow_dispatch`)
3. Use `actions/create-release` + `actions/upload-release-asset` to attach the
   downloaded artifacts to a GitHub Release

---

## Testing URI scheme after installation

```bash
# macOS (after mounting / installing the DMG)
open "classiclauncher://install?url=https://example.com/test-manifest.yml"

# Windows (in Run dialog or browser address bar)
classiclauncher://install?url=https://example.com/test-manifest.yml

# Linux (via xdg-open after DEB install)
xdg-open "classiclauncher://install?url=https://example.com/test-manifest.yml"
```

### Testing single-instance forwarding

With the launcher already running, launch a second instance with a URI argument:

```bash
java -jar target/Launcher-*.jar \
    "classiclauncher://install?url=https://example.com/test-manifest.yml"
```

The second instance should exit immediately and the confirm dialog should appear in the
already-running launcher window.
