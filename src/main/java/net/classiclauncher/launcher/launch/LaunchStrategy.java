package net.classiclauncher.launcher.launch;

import java.util.List;

/**
 * Encapsulates the full launch pipeline for a specific game/provider combination.
 *
 * <p>
 * The launcher calls {@link #prepare} then {@link #buildCommand} sequentially on a single background thread.
 * Implementations may store intermediate state as instance fields because both methods are always called in order on
 * the same thread.
 */
public interface LaunchStrategy {

	/**
	 * Performs all pre-launch work: downloads, SHA1 verification, native extraction, token refresh, etc.
	 *
	 * @param ctx
	 *            the launch context containing account, game, profile and paths
	 * @param progress
	 *            callback for progress reporting and logging
	 * @throws Exception
	 *             if preparation fails; the launcher will abort the launch and report the error via
	 *             {@link LaunchProgress#onLaunchComplete(boolean)}
	 */
	void prepare(LaunchContext ctx, LaunchProgress progress) throws Exception;

	/**
	 * Builds the process command line. Called immediately after {@link #prepare} returns successfully.
	 *
	 * @param ctx
	 *            the launch context
	 * @return the full command including the Java executable, JVM args, main class, and game args; never null or empty
	 * @throws Exception
	 *             if the command cannot be built
	 */
	List<String> buildCommand(LaunchContext ctx) throws Exception;

}
