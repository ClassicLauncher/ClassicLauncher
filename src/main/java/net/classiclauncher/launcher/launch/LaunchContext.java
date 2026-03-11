package net.classiclauncher.launcher.launch;

import java.io.File;
import java.util.Objects;

import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.account.Account;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.jre.JavaInstallation;
import net.classiclauncher.launcher.profile.Profile;

/**
 * Immutable context object passed to {@link LaunchStrategy} methods.
 *
 * <p>
 * Built by BottomBar.handlePlay() and passed through the full launch pipeline.
 */
public final class LaunchContext {

	private final Account account;
	private final Game game;
	private final Profile profile;

	/**
	 * The Java installation to use, or {@code null} to use {@code "java"} from PATH.
	 */
	private final JavaInstallation jre;

	/**
	 * Root data directory for this game: {@code <launcherDataDir>/games/<gameId>/}. Houses the {@code versions/},
	 * {@code libraries/}, and {@code assets/} subdirectories.
	 */
	private final File gameDataDir;

	/**
	 * The working directory passed to the game process. Equals {@link Profile#getGameDirectory()} if set, otherwise
	 * equals {@link #gameDataDir}.
	 */
	private final File gameDirectory;

	public LaunchContext(Account account, Game game, Profile profile, JavaInstallation jre) {
		this.account = Objects.requireNonNull(account, "account must not be null");
		this.game = Objects.requireNonNull(game, "game must not be null");
		this.profile = Objects.requireNonNull(profile, "profile must not be null");
		this.jre = jre; // nullable

		this.gameDataDir = LauncherContext.getInstance().resolve("games", game.getGameId());

		String profileGameDir = profile.getGameDirectory();
		this.gameDirectory = (profileGameDir != null && !profileGameDir.isEmpty())
				? new File(profileGameDir)
				: this.gameDataDir;
	}

	public Account getAccount() {
		return account;
	}

	public Game getGame() {
		return game;
	}

	public Profile getProfile() {
		return profile;
	}

	public JavaInstallation getJre() {
		return jre;
	}

	public File getGameDataDir() {
		return gameDataDir;
	}

	public File getGameDirectory() {
		return gameDirectory;
	}

}
