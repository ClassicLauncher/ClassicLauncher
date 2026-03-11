package net.classiclauncher.launcher.account;

/**
 * Authentication method used by an {@link AccountProvider}.
 *
 * <ul>
 * <li>{@link #FORM} — credentials collected via username/password fields and submitted directly.</li>
 * <li>{@link #BROWSER} — user is redirected to an external URL; the callback arrives via a local HTTP server.</li>
 * </ul>
 */
public enum AuthMethod {
	FORM, BROWSER
}
