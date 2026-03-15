package net.classiclauncher.launcher.update;

import java.io.IOException;
import java.util.List;

/**
 * Source of available launcher releases.
 *
 * <p>
 * Implementations fetch and return the raw list of releases from a remote service. Filtering, version comparison, and
 * skip-logic are all handled by {@link UpdateChecker} — a source only needs to fetch and return.
 *
 * <p>
 * Because this is a {@link FunctionalInterface}, it can be implemented inline as a lambda:
 *
 * <pre>{@code
 *
 * ReleaseSource mock = () -> Arrays.asList(new ReleaseInfo("v1.0.1", "Fix", "", assets));
 * }</pre>
 *
 * <p>
 * This makes it trivial for extensions to register their own update feed without touching the core update logic:
 *
 * <pre>{@code
 * ReleaseSource source = new GitHubReleaseSource("my-org/my-extension");
 * UpdateChecker.checkAsync(source, currentVersion, launcherSettings, () -> window);
 * }</pre>
 */
@FunctionalInterface
public interface ReleaseSource {

	/**
	 * Fetches the list of available releases. Implementations should return them newest-first (as most remote APIs do),
	 * and should filter out drafts and pre-releases before returning.
	 *
	 * @return list of releases; never {@code null}; may be empty
	 * @throws IOException
	 *             on network or parse errors
	 */
	List<ReleaseInfo> fetchReleases() throws IOException;

}
