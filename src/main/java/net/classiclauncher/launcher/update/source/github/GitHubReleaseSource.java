package net.classiclauncher.launcher.update.source.github;

import java.io.IOException;
import java.util.List;

import net.classiclauncher.launcher.LauncherVersion;
import net.classiclauncher.launcher.update.ReleaseInfo;
import net.classiclauncher.launcher.update.ReleaseSource;

/**
 * {@link ReleaseSource} that fetches releases from the GitHub Releases API.
 *
 * <p>
 * Usage — launcher's own updates:
 *
 * <pre>{@code
 * ReleaseSource source = GitHubReleaseSource.fromLauncherConfig();
 * if (source != null) {
 * 	UpdateChecker.checkAsync(source, LauncherVersion.VERSION, settings, windowSupplier);
 * }
 * }</pre>
 *
 * <p>
 * Usage — extension's own updates:
 *
 * <pre>{@code
 * ReleaseSource source = new GitHubReleaseSource("my-org/my-extension");
 * UpdateChecker.checkAsync(source, currentVersion, launcherSettings, () -> window);
 * }</pre>
 */
public class GitHubReleaseSource implements ReleaseSource {

	private final String repo;
	private final GitHubReleasesClient client;

	/**
	 * Creates a source that fetches from the real GitHub API.
	 *
	 * @param repo
	 *            GitHub repository in {@code owner/name} form (e.g. {@code "ClassicLauncher/ClassicLauncher"})
	 */
	public GitHubReleaseSource(String repo) {
		this(repo, new GitHubReleasesClient());
	}

	/**
	 * Creates a source with a custom HTTP client. Intended for testing — pass a client pointing at a local test server.
	 */
	GitHubReleaseSource(String repo, GitHubReleasesClient client) {
		this.repo = repo;
		this.client = client;
	}

	@Override
	public List<ReleaseInfo> fetchReleases() throws IOException {
		String json = client.fetchReleasesJson(repo);
		return GitHubJsonParser.parse(json);
	}

	/**
	 * Creates a {@link GitHubReleaseSource} configured for the launcher's own GitHub repository
	 * ({@link LauncherVersion#GITHUB_REPO}), or {@code null} when the repository is not configured (blank or left as
	 * the Maven placeholder {@code ${github.repo}}).
	 *
	 * <p>
	 * Use this in {@code Main.java} and {@code LauncherV1_1.java} to avoid duplicating the "is configured?" check:
	 *
	 * <pre>{@code
	 * ReleaseSource source = GitHubReleaseSource.fromLauncherConfig();
	 * UpdateChecker.checkAsync(source, LauncherVersion.VERSION, settings, windowSupplier);
	 * }</pre>
	 */
	public static GitHubReleaseSource fromLauncherConfig() {
		String repo = LauncherVersion.GITHUB_REPO;
		if (repo == null || repo.isEmpty() || repo.startsWith("${")) return null;
		return new GitHubReleaseSource(repo);
	}

}
