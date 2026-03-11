package net.classiclauncher.launcher.api;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.version.Version;

/**
 * Null-object singleton implementation of {@link GameApi}.
 *
 * <p>
 * Used as the default API when a {@link Game} has no {@code apiFactory} configured. All methods return safe, empty
 * values — no network calls are made and no exceptions are thrown.
 */
public final class NullGameApi implements GameApi {

	/**
	 * The singleton instance.
	 */
	public static final NullGameApi INSTANCE = new NullGameApi();

	private NullGameApi() {
	}

	@Override
	public String getBaseUrl() {
		return "";
	}

	@Override
	public List<Version> getAvailableVersions() {
		return Collections.emptyList();
	}

	@Override
	public Optional<Version> getVersion(String id) {
		return Optional.empty();
	}

}
