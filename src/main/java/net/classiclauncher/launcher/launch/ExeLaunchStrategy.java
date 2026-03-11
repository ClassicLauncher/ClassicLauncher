package net.classiclauncher.launcher.launch;

import java.util.ArrayList;
import java.util.List;

/**
 * Launch strategy for native executables (EXE or shell scripts).
 *
 * <p>
 * {@link #prepare} is a no-op — no JVM or download setup is required. {@link #buildCommand} returns the executable path
 * from the game directory.
 */
public final class ExeLaunchStrategy implements LaunchStrategy {

	public static final ExeLaunchStrategy INSTANCE = new ExeLaunchStrategy();

	private ExeLaunchStrategy() {
	}

	@Override
	public void prepare(LaunchContext ctx, LaunchProgress progress) {
		// No-op: native executables require no download or JVM preparation.
	}

	@Override
	public List<String> buildCommand(LaunchContext ctx) {
		List<String> command = new ArrayList<>();
		command.add(ctx.getGameDirectory().getAbsolutePath());
		return command;
	}

}
