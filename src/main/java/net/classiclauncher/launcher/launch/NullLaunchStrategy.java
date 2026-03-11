package net.classiclauncher.launcher.launch;

import java.util.List;

/**
 * No-op {@link LaunchStrategy} used when no strategy has been configured for a game.
 *
 * <p>
 * {@link #prepare} is a no-op. {@link #buildCommand} always throws {@link UnsupportedOperationException} — the caller
 * must check that a real strategy is configured before launching.
 */
public final class NullLaunchStrategy implements LaunchStrategy {

	public static final NullLaunchStrategy INSTANCE = new NullLaunchStrategy();

	private NullLaunchStrategy() {
	}

	@Override
	public void prepare(LaunchContext ctx, LaunchProgress progress) {
		// No-op: nothing to prepare for an unconfigured game.
	}

	@Override
	public List<String> buildCommand(LaunchContext ctx) {
		throw new UnsupportedOperationException("No launch strategy configured for game: " + ctx.getGame().getGameId());
	}

}
