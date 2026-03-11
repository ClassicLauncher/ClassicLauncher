package net.classiclauncher.launcher;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Optional;

import javax.swing.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.classiclauncher.launcher.account.Account;
import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.launch.LaunchContext;
import net.classiclauncher.launcher.launch.LaunchProgress;
import net.classiclauncher.launcher.launch.LaunchStrategy;
import net.classiclauncher.launcher.profile.LauncherVisibility;
import net.classiclauncher.launcher.settings.Settings;

/**
 * Thin orchestrator that drives the full game launch pipeline.
 *
 * <ol>
 * <li>Resolves the {@link LaunchStrategy} for the account/game combination.</li>
 * <li>Calls {@link LaunchStrategy#prepare} to download/verify files and obtain tokens.</li>
 * <li>Calls {@link LaunchStrategy#buildCommand} to get the process command.</li>
 * <li>Starts the process, streams its output to {@link LaunchProgress#log}, and handles
 * {@link LauncherVisibility}.</li>
 * </ol>
 *
 * <p>
 * Call {@link #launchAsync} from the EDT — it starts all blocking work on a daemon thread.
 */
public class GameLauncher {

	private static final Logger log = LogManager.getLogger(GameLauncher.class);

	private final JFrame frame;

	/**
	 * @param frame
	 *            the launcher main window, used to implement {@link LauncherVisibility}; may be {@code null} if
	 *            visibility handling is not needed
	 */
	public GameLauncher(JFrame frame) {
		this.frame = frame;
	}

	/**
	 * Starts the full launch pipeline on a daemon thread. Safe to call from the EDT.
	 *
	 * @param ctx
	 *            the launch context (account, game, profile, JRE)
	 * @param progress
	 *            progress and log callbacks
	 */
	public void launchAsync(LaunchContext ctx, LaunchProgress progress) {
		Thread t = new Thread(() -> doLaunch(ctx, progress), "game-launcher");
		t.setDaemon(true);
		t.start();
	}

	private void doLaunch(LaunchContext ctx, LaunchProgress progress) {
		try {
			// ── 1. Resolve strategy ───────────────────────────────────────────
			LaunchStrategy strategy = resolveStrategy(ctx);

			// ── 2. Prepare (downloads, token refresh, native extraction, etc.) ─
			strategy.prepare(ctx, progress);

			// ── 3. Build command ───────────────────────────────────────────────
			List<String> command = strategy.buildCommand(ctx);
			progress.log("Launching: " + String.join(" ", command));
			log.info("Launching game: {}", String.join(" ", command));

			// ── 4. Apply LauncherVisibility before process start ───────────────
			LauncherVisibility visibility = ctx.getProfile().getLauncherVisibility();
			if (visibility == null) visibility = LauncherVisibility.CLOSE_LAUNCHER;

			if (visibility == LauncherVisibility.CLOSE_LAUNCHER && frame != null) {
				SwingUtilities.invokeLater(() -> frame.dispose());
			} else if (visibility == LauncherVisibility.HIDE_LAUNCHER && frame != null) {
				SwingUtilities.invokeLater(() -> frame.setVisible(false));
			}

			// ── 5. Start process ───────────────────────────────────────────────
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.redirectErrorStream(true);
			pb.directory(ctx.getGameDirectory());
			ctx.getGameDirectory().mkdirs();
			Process process = pb.start();

			// Stream output to log
			final Thread reader = new Thread(() -> {
				try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
					String line;
					while ((line = br.readLine()) != null) {
						progress.log(line);
					}
				} catch (Exception ignored) {
				}
			}, "game-output-reader");
			reader.setDaemon(true);
			reader.start();

			int exitCode = process.waitFor();
			reader.join();

			progress.log("[Game exited with code " + exitCode + "]");
			log.info("Game exited with code {}", exitCode);

			// Restore launcher window if it was hidden
			if (visibility == LauncherVisibility.HIDE_LAUNCHER && frame != null) {
				SwingUtilities.invokeLater(() -> frame.setVisible(true));
			}

			progress.onLaunchComplete(exitCode == 0);

		} catch (Exception e) {
			log.error("Launch failed", e);
			progress.log("[ERROR] Launch failed: " + e.getMessage());
			progress.onLaunchComplete(false);
		}
	}

	/**
	 * Resolves the {@link LaunchStrategy}: provider override takes precedence over the game's built-in default.
	 */
	private LaunchStrategy resolveStrategy(LaunchContext ctx) {
		Account account = ctx.getAccount();
		Game game = ctx.getGame();
		Settings settings = Settings.getInstance();
		Optional<AccountProvider> optProvider = settings.getAccounts().getProvider(account.getType());
		if (optProvider.isPresent()) {
			return optProvider.get().getLaunchStrategy(game);
		}
		return game.createLaunchStrategy();
	}

}
