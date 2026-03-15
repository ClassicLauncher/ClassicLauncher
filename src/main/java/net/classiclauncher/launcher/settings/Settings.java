package net.classiclauncher.launcher.settings;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;
import net.classiclauncher.launcher.account.Accounts;
import net.classiclauncher.launcher.extension.Extensions;
import net.classiclauncher.launcher.jre.JavaManager;
import net.classiclauncher.launcher.profile.Profiles;
import net.classiclauncher.launcher.update.ReleaseSource;

public class Settings {

	/**
	 * Well-known source ID for the launcher's own update checks. Set by {@code Main.java} during startup; used by
	 * {@link net.classiclauncher.launcher.update.UpdateChecker} and
	 * {@link net.classiclauncher.launcher.ui.update.UpdateSettingsPanel}.
	 */
	public static final String LAUNCHER_SOURCE_ID = "launcher";

	private static Settings instance;

	private final LauncherSettings launcherSettings;
	private final AccountSettings accountSettings;
	@Getter
	private final Accounts accounts;
	@Getter
	private final Profiles profiles;
	@Getter
	private final JavaManager javaManager;
	@Getter
	private final Extensions extensions;

	private final ConcurrentHashMap<String, ReleaseSource> releaseSources = new ConcurrentHashMap<>();

	private Settings() {
		launcherSettings = new LauncherSettings();
		accountSettings = new AccountSettings();
		accounts = new Accounts();
		accounts.load();
		profiles = new Profiles();
		profiles.load();
		javaManager = new JavaManager();
		javaManager.load();
		extensions = new Extensions();
		extensions.load();
	}

	public static Settings getInstance() {
		if (instance == null) instance = new Settings();
		return instance;
	}

	public LauncherSettings getLauncher() {
		return launcherSettings;
	}

	public AccountSettings getAccount() {
		return accountSettings;
	}

	// ── Release sources ──────────────────────────────────────────────────────

	/**
	 * Returns the launcher's default release source (the one registered under {@link #LAUNCHER_SOURCE_ID}), or
	 * {@code null} if none has been set.
	 *
	 * <p>
	 * This is the source used by the startup update check and the "Check Now" button in settings.
	 *
	 * @return the launcher's release source, or {@code null}
	 */
	public ReleaseSource getReleaseSource() {
		return releaseSources.get(LAUNCHER_SOURCE_ID);
	}

	/**
	 * Sets (or replaces) the launcher's default release source. Pass {@code null} to remove it and disable the
	 * launcher's own update checks.
	 *
	 * <p>
	 * This is a convenience method equivalent to:
	 *
	 * <pre>{@code
	 * settings.addReleaseSource(Settings.LAUNCHER_SOURCE_ID, source);
	 * }</pre>
	 *
	 * @param releaseSource
	 *            the release source, or {@code null} to remove
	 */
	public void setReleaseSource(ReleaseSource releaseSource) {
		if (releaseSource == null) {
			releaseSources.remove(LAUNCHER_SOURCE_ID);
		} else {
			releaseSources.put(LAUNCHER_SOURCE_ID, releaseSource);
		}
	}

	/**
	 * Registers a named release source. Multiple sources can coexist — each identified by a unique {@code id}.
	 * Extensions can use this to register their own update feeds independently of the launcher's:
	 *
	 * <pre>{@code
	 *
	 * public void onLoad(Settings settings) {
	 * 	settings.addReleaseSource("my-extension", new GitHubReleaseSource("my-org/my-extension"));
	 * }
	 * }</pre>
	 *
	 * <p>
	 * If a source with the same {@code id} already exists, it is replaced.
	 *
	 * @param id
	 *            unique identifier for this source (e.g. extension name, {@code "launcher"})
	 * @param source
	 *            the release source; must not be {@code null}
	 * @throws IllegalArgumentException
	 *             if {@code id} or {@code source} is {@code null}
	 */
	public void addReleaseSource(String id, ReleaseSource source) {
		if (id == null) throw new IllegalArgumentException("Release source ID must not be null");
		if (source == null) throw new IllegalArgumentException("Release source must not be null");
		releaseSources.put(id, source);
	}

	/**
	 * Removes a named release source.
	 *
	 * @param id
	 *            the source identifier to remove
	 * @return the removed source, or {@code null} if no source was registered under that ID
	 */
	public ReleaseSource removeReleaseSource(String id) {
		if (id == null) return null;
		return releaseSources.remove(id);
	}

	/**
	 * Returns the release source registered under the given ID, or {@code null} if none exists.
	 *
	 * @param id
	 *            the source identifier
	 * @return the source, or {@code null}
	 */
	public ReleaseSource getReleaseSource(String id) {
		if (id == null) return null;
		return releaseSources.get(id);
	}

	/**
	 * Returns an unmodifiable view of all registered release sources. The keys are source IDs and the values are the
	 * corresponding {@link ReleaseSource} implementations.
	 *
	 * @return unmodifiable map of all registered sources
	 */
	public Map<String, ReleaseSource> getReleaseSources() {
		return Collections.unmodifiableMap(releaseSources);
	}

}
