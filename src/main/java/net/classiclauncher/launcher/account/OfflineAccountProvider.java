package net.classiclauncher.launcher.account;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.game.Game;
import top.wavelength.betterreflection.BetterReflectionClass;

/**
 * Built-in provider for offline (unauthenticated) accounts.
 *
 * <p>
 * Offline accounts require only a username — no password, no network call. This provider is registered automatically by
 * {@link Accounts}.
 */
public class OfflineAccountProvider extends AccountProvider {

	/**
	 * Optional update-notes URL for offline accounts. When {@code null} (the default), the launcher falls back to the
	 * URL in {@code settings.yml}. Set via {@link #withUpdateNotesUrl(String)} before registering the provider.
	 */
	private String updateNotesUrl = null;

	/**
	 * Configures the update-notes URL for offline accounts and returns {@code this} for chaining.
	 *
	 * <pre>{@code
	 * settings.getAccounts()
	 * 		.registerProvider(new OfflineAccountProvider().withUpdateNotesUrl("https://my-game.com/news"));
	 * }</pre>
	 */
	public OfflineAccountProvider withUpdateNotesUrl(String url) {
		this.updateNotesUrl = url;
		return this;
	}

	@Override
	public String getTypeId() {
		return AccountType.OFFLINE;
	}

	@Override
	public String getDisplayName() {
		return "Offline";
	}

	@Override
	public String getIconResourcePath() {
		return null;
	}

	@Override
	public boolean requiresPassword() {
		return false;
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
	public List<Game> getGames() {
		return Collections.emptyList();
	}

	/**
	 * Returns the configured URL, or {@code null} to fall back to {@code settings.yml}.
	 */
	@Override
	public String getUpdateNotesUrl() {
		return updateNotesUrl;
	}

	/**
	 * Creates an offline account from the given username. The {@code password} array is zeroed immediately (offline
	 * accounts don't use it).
	 */
	@Override
	public Account createFromForm(String username, char[] password) {
		if (password != null) {
			Arrays.fill(password, '\0');
		}
		return OfflineAccount.create(username);
	}

	@Override
	public void startBrowserAuth(Consumer<Account> onComplete, Consumer<String> onError) {
		throw new UnsupportedOperationException("Offline accounts do not support browser authentication.");
	}

	@Override
	public Account fromConfig(String id, YmlConfig config) {
		return OfflineAccount.fromConfig(id, config);
	}

	public static BetterReflectionClass<OfflineAccountProvider> CLASS = new BetterReflectionClass<>(
			OfflineAccountProvider.class);

}
