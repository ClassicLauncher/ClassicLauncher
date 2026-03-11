package net.classiclauncher.launcher.extension;

import net.classiclauncher.launcher.account.Accounts;
import net.classiclauncher.launcher.settings.Settings;

/**
 * Entry point for launcher extensions loaded from independently distributed JARs.
 *
 * <p>
 * Implement this interface in your extension JAR and ensure the class has a public no-arg constructor. The launcher
 * will discover it via classpath scanning and call {@link #onLoad(Settings)} once after the core settings have been
 * initialized.
 *
 * <h3>Registering custom account providers</h3> Because Accounts is not yet signalled as ready when {@code onLoad} is
 * called, extensions must use {@link Accounts#onReady} to defer provider registration:
 *
 * <pre>{@code
 *
 * public void onLoad(Settings settings) {
 * 	Accounts.onReady(accounts -> accounts.registerProvider(new MyProvider()));
 * }
 * }</pre>
 * <p>
 * The callback fires immediately if Accounts is already ready (e.g. in tests), or is deferred until
 * {@code Accounts.signalReady} is called during bootstrap.
 */
public interface LauncherExtension {

	/**
	 * Called once after the launcher core has initialized but before the UI is shown.
	 *
	 * @param settings
	 *            the fully-loaded launcher settings instance
	 */
	void onLoad(Settings settings);

}
