package net.classiclauncher.launcher;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import dev.utano.librarymanager.CustomURLClassLoader;
import dev.utano.librarymanager.LibraryManager;
import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.account.Accounts;
import net.classiclauncher.launcher.alpha.LauncherAlpha;
import net.classiclauncher.launcher.extension.Extensions;
import net.classiclauncher.launcher.platform.Platform;
import net.classiclauncher.launcher.settings.LauncherStyle;
import net.classiclauncher.launcher.settings.Settings;
import net.classiclauncher.launcher.ui.ExtensionUriConfirmDialog;
import net.classiclauncher.launcher.update.ReleaseSource;
import net.classiclauncher.launcher.update.UpdateChecker;
import net.classiclauncher.launcher.update.source.github.GitHubReleaseSource;
import net.classiclauncher.launcher.uri.ExtensionInstallRequest;
import net.classiclauncher.launcher.uri.SingleInstanceManager;
import net.classiclauncher.launcher.uri.UriSchemeHandler;
import net.classiclauncher.launcher.v1_1.LauncherV1_1;
import top.wavelength.betterreflection.BetterReflectionClass;
import top.wavelength.betterreflection.BetterReflectionUtils;

public class Main {

	private static final Logger LOG = LogManager.getLogger(Main.class);

	/**
	 * The logical name of this launcher installation. Determines data-dir, IPC port, etc.
	 */
	private static final String LAUNCHER_NAME = "launcher";

	// Held as a field so handleUri() can reach the Extensions instance without a static reference.
	private static Extensions extensionsRef;

	public static void main(String[] args) {
		// ── 0. Detect URI from args BEFORE any disk I/O ───────────────────────
		String initialUri = (args.length > 0 && UriSchemeHandler.isClassicLauncherUri(args[0])) ? args[0] : null;

		// ── 1. Initialize context ──────────────────────────────────────────────
		// Replace LAUNCHER_NAME ("launcher") with your game/launcher name before releasing a fork.
		// This becomes the data directory name on all platforms:
		// Windows : %APPDATA%\<name>
		// macOS : ~/Library/Application Support/<name>
		// Linux : ~/.config/<name>
		LauncherContext.initialize(LAUNCHER_NAME);

		// ── 2. Single-instance check ───────────────────────────────────────────
		// Attempt to bind the IPC port. If another instance is already running, forward the URI
		// (if any) and exit. The URI will be shown in the already-running instance's window.
		SingleInstanceManager sim = new SingleInstanceManager(LAUNCHER_NAME);
		boolean isPrimary;
		try {
			isPrimary = sim.tryClaimInstance(initialUri);
		} catch (IOException e) {
			// Unexpected bind failure — treat this instance as primary and continue.
			LOG.error("SingleInstanceManager bind failed; continuing as primary instance", e);
			isPrimary = true;
		}
		if (!isPrimary) {
			// Another instance is running and has been notified (if a URI was given). Exit.
			System.exit(0);
			return;
		}
		Runtime.getRuntime().addShutdownHook(new Thread(sim::close, "sim-shutdown"));

		// ── 3. Pending URI state (shared between IPC listener and UI startup) ──
		// pendingUri holds a URI that arrived before the UI is ready to display a dialog.
		// uiReady is set to true once the launcher window is shown.
		AtomicReference<String> pendingUri = new AtomicReference<>(initialUri);
		AtomicReference<Boolean> uiReady = new AtomicReference<>(Boolean.FALSE);

		// ── 4. macOS: register Apple Events URI handler ────────────────────────
		// Must be done before any AWT/Swing objects are created. The handler fires in the already-
		// running instance when a classiclauncher:// URI is clicked while the app is open.
		if (Platform.current() == Platform.MACOS) {
			registerMacOsOpenUriHandler(pendingUri, uiReady);
		}

		// ── 5. IPC listener: URIs forwarded from secondary instances ───────────
		// On Windows and Linux the OS launches a new process with the URI as args[0].
		// SingleInstanceManager forwards it here over the loopback socket.
		sim.setUriListener(uri -> {
			if (uiReady.get()) {
				SwingUtilities.invokeLater(() -> handleUri(uri));
			} else {
				pendingUri.compareAndSet(null, uri);
			}
		});

		// ── 6. Create shared classloader ──────────────────────────────────────
		// All runtime dependencies (loaded from libs.yml) and extensions are added
		// to this classloader so they share the same type space.
		CustomURLClassLoader classLoader = new CustomURLClassLoader(new URL[0],
				Thread.currentThread().getContextClassLoader());
		Thread.currentThread().setContextClassLoader(classLoader);

		// ── 7. Bootstrap launcher libraries from libs.yml (JAR mode only) ─────
		// When running from a shaded JAR, heavy dependencies (LWJGL, etc.) are NOT
		// bundled in the JAR. They are listed in libs.yml next to the JAR and
		// downloaded/loaded at startup via LibraryManager.
		File libsYml = resolveLibsYml();
		if (BetterReflectionUtils.isRunningFromJar(new BetterReflectionClass<>(Main.class)) && libsYml.exists()) {
			try {
				YmlConfig config = new YmlConfig(libsYml);
				config.load();
				LibraryManager lm = new LibraryManager(LauncherContext.getInstance().getDataDir().getAbsolutePath());
				lm.loadFromYml(config);
				lm.downloadLibraries();
				lm.loadLibraries(classLoader);
			} catch (Exception e) {
				JOptionPane.showMessageDialog(null, "Failed to load launcher libraries:\n" + e.getMessage(),
						"Startup Error", JOptionPane.ERROR_MESSAGE);
				System.exit(1);
			}
		}

		// ── 8. Initialize settings ────────────────────────────────────────────
		Settings settings = Settings.getInstance();
		extensionsRef = settings.getExtensions();

		// ── 9. Load extensions ────────────────────────────────────────────────
		// Extensions may call Accounts.onReady(...) during their onLoad to register
		// additional providers. These callbacks are queued until signalReady below.
		settings.getExtensions().loadAll(classLoader, settings);

		// ── 10. Signal Accounts ready ─────────────────────────────────────────
		// Drains any queued Accounts.onReady(...) callbacks registered by extensions.
		Accounts.signalReady(settings.getAccounts());

		// ── 10b. Reload accounts with extension deserializers ─────────────────
		// Settings.getAccounts().load() runs before extensions are loaded, so accounts
		// are deserialized with built-in stubs. Now that extensions have registered their
		// providers (and thus updated the deserializer map), re-read from disk so that
		// accounts use the extension implementations (e.g. MicrosoftAccount with refresh token).
		settings.getAccounts().load();

		// ── 11. Launch UI + drain pending URI ─────────────────────────────────
		LauncherStyle style = settings.getLauncher().getStyle();
		List<String> extIssues = settings.getExtensions().getLoadIssues();
		SwingUtilities.invokeLater(() -> {
			// Propagate the custom classloader to the EDT so ResourceLoader can find
			// extension resources (icons, game assets, etc.) via the context classloader.
			Thread.currentThread().setContextClassLoader(classLoader);

			switch (style) {
				case V1_1 :
					new LauncherV1_1().show();
					break;
				case ALPHA :
				default :
					new LauncherAlpha().show();
					break;
			}
			if (!extIssues.isEmpty()) {
				StringBuilder sb = new StringBuilder(
						"<html>The following extension issues were detected at startup:<br><br>");
				for (String issue : extIssues) {
					sb.append("&nbsp;&nbsp;• ")
							.append(issue.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
							.append("<br>");
				}
				sb.append("</html>");
				JOptionPane.showMessageDialog(null, new javax.swing.JLabel(sb.toString()), "Extension Issues",
						JOptionPane.WARNING_MESSAGE);
			}

			// Mark UI as ready and drain any URI that arrived during startup
			uiReady.set(Boolean.TRUE);
			String pending = pendingUri.getAndSet(null);
			if (pending != null) {
				handleUri(pending);
			}
		});

		// ── 12. Check for updates (daemon thread, non-blocking) ───────────────
		// Launched after the UI invokeLater block is submitted; the update check runs
		// in the background and dispatches the UpdateDialog to the EDT only if a newer
		// version is found. The window supplier is evaluated lazily on the EDT when the
		// dialog is about to appear, so it always returns the active frame.
		settings.setReleaseSource(GitHubReleaseSource.fromLauncherConfig());
		ReleaseSource releaseSource = settings.getReleaseSource();
		if (releaseSource != null && settings.getLauncher().isUpdateCheckEnabled()) {
			UpdateChecker.checkAsync(releaseSource, LauncherVersion.VERSION, settings.getLauncher(),
					Main::findActiveWindow);
		}
	}

	// ── URI handling ──────────────────────────────────────────────────────────

	/**
	 * Parses a {@code classiclauncher://} URI and shows the confirm dialog. Must be called on the EDT.
	 *
	 * @param rawUri
	 *            the raw URI string
	 */
	private static void handleUri(String rawUri) {
		ExtensionInstallRequest request;
		try {
			request = UriSchemeHandler.parse(rawUri);
		} catch (IllegalArgumentException e) {
			LOG.warn("Malformed classiclauncher:// URI: {}", rawUri, e);
			JOptionPane.showMessageDialog(null, "Invalid launcher URI:\n" + e.getMessage(), "URI Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (extensionsRef == null) {
			LOG.warn("handleUri called before extensions were initialized; URI dropped: {}", rawUri);
			JOptionPane.showMessageDialog(null,
					"The launcher is not fully initialized yet. Please try again in a moment.", "Not Ready",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		Window activeWindow = findActiveWindow();
		new ExtensionUriConfirmDialog(activeWindow, request, extensionsRef).showAndWait();
	}

	/**
	 * Returns the currently focused/active Swing window, or {@code null} if none is focused.
	 */
	/**
	 * Resolves the {@code libs.yml} file next to the launcher JAR.
	 *
	 * <p>
	 * Uses the code-source location of this class so the path is correct when running from a jpackage installer (where
	 * the JAR sits in the app's {@code lib/} subdirectory). Falls back to {@code user.dir} when the code-source
	 * location cannot be determined (e.g. when running from an IDE with exploded classes).
	 */
	private static File resolveLibsYml() {
		try {
			java.security.CodeSource cs = Main.class.getProtectionDomain().getCodeSource();
			if (cs != null && cs.getLocation() != null) {
				File jarOrDir = new File(cs.getLocation().toURI());
				File base = jarOrDir.isFile() ? jarOrDir.getParentFile() : jarOrDir;
				return new File(base, "libs.yml");
			}
		} catch (Exception ignored) {
		}
		return new File(System.getProperty("user.dir", "."), "libs.yml");
	}

	private static Window findActiveWindow() {
		for (Window w : Window.getWindows()) {
			if (w.isActive()) return w;
		}
		return null;
	}

	// ── macOS Apple Events URI handler ────────────────────────────────────────

	/**
	 * Registers a {@code java.awt.desktop.OpenURIHandler} via reflection so the code compiles against the Java 8 API
	 * target while still leveraging the Java 9+ Desktop API at runtime.
	 *
	 * <p>
	 * Skipped silently on Java 8 and on non-macOS platforms that do not support the
	 * {@link java.awt.Desktop.Action#APP_OPEN_URI} action.
	 *
	 * @param pendingUri
	 *            holds a URI that arrived before the launcher window is ready
	 * @param uiReady
	 *            {@code true} once the launcher window is fully shown
	 */
	private static void registerMacOsOpenUriHandler(final AtomicReference<String> pendingUri,
			final AtomicReference<Boolean> uiReady) {

		if (BetterReflectionUtils.getJavaVersion() < 9) {
			LOG.debug("Skipping macOS OpenURIHandler registration: Java < 9");
			return;
		}

		try {
			// Load classes that exist only in Java 9+
			Class<?> desktopClass = Class.forName("java.awt.Desktop");
			Class<?> openUriHandlerClass = Class.forName("java.awt.desktop.OpenURIHandler");
			Class<?> openUriEventClass = Class.forName("java.awt.desktop.OpenURIEvent");

			Object desktop = desktopClass.getMethod("getDesktop").invoke(null);

			// Create a Proxy implementing OpenURIHandler via reflection
			Object proxy = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
					new Class<?>[]{openUriHandlerClass}, new InvocationHandler() {

						@Override
						public Object invoke(Object proxyObj, Method method, Object[] proxyArgs) throws Throwable {
							if ("openURI".equals(method.getName()) && proxyArgs != null && proxyArgs.length == 1) {
								Object event = proxyArgs[0];
								// event.getURI() → java.net.URI
								Object uri = openUriEventClass.getMethod("getURI").invoke(event);
								if (uri != null) {
									String uriStr = uri.toString();
									if (uiReady.get()) {
										SwingUtilities.invokeLater(() -> handleUri(uriStr));
									} else {
										pendingUri.set(uriStr);
									}
								}
							}
							return null;
						}

					});

			desktopClass.getMethod("setOpenURIHandler", openUriHandlerClass).invoke(desktop, proxy);

			LOG.debug("macOS OpenURIHandler registered successfully");

		} catch (Exception e) {
			// Not fatal — URI handling via args[0] still works for cold launches.
			LOG.debug("macOS OpenURIHandler registration failed (non-fatal): {}", e.getMessage());
		}
	}

}
