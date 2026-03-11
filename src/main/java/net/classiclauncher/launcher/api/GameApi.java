package net.classiclauncher.launcher.api;

import java.util.List;
import java.util.Optional;

import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.version.Version;

/**
 * Contract for fetching game version information from a remote source.
 *
 * <p>
 * Each {@link Game} exposes a default implementation via {@link Game#createApi()}. An {@link AccountProvider} can
 * override or decorate the game's API for its own accounts via {@link AccountProvider#getApiForGame}.
 *
 * <p>
 * Implementations must never return {@code null} from any method.
 */
public interface GameApi {

	/**
	 * Returns the base URL this client targets. Never {@code null}; may be an empty string when no remote source is
	 * configured.
	 */
	String getBaseUrl();

	/**
	 * Returns all game versions available through this API. Never {@code null}; returns an unmodifiable empty list when
	 * no versions are available.
	 */
	List<Version> getAvailableVersions();

	/**
	 * Looks up a single version by its stable string identifier.
	 *
	 * @param id
	 *            the version identifier (e.g. {@code "1.20.4"}, {@code "b1.7.3"}); never {@code null}
	 * @return an {@link Optional} containing the matching {@link Version}, or empty if not found
	 */
	Optional<Version> getVersion(String id);

}
