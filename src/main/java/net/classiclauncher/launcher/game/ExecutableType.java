package net.classiclauncher.launcher.game;

/**
 * The execution model of the game binary.
 *
 * <ul>
 * <li>{@link #JAR} — launched via {@code java [-vmArgs] -jar <file> [gameArgs]}; Java Settings are shown in the profile
 * editor.</li>
 * <li>{@link #EXE} — native executable launched directly; Java Settings are hidden.</li>
 * <li>{@link #SHELL} — shell script ({@code sh}/{@code cmd}) launched directly.</li>
 * </ul>
 */
public enum ExecutableType {
	JAR, EXE, SHELL
}
