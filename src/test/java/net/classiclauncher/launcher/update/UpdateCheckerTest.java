package net.classiclauncher.launcher.update;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.settings.LauncherSettings;

class UpdateCheckerTest {

	@TempDir
	File tempDir;

	private final List<HttpServer> servers = new ArrayList<>();
	private LauncherSettings settings;

	@BeforeEach
	void setUp() {
		System.setProperty("user.home", tempDir.getAbsolutePath());
		LauncherContext.initialize("test-updater");
		settings = new LauncherSettings();
	}

	@AfterEach
	void tearDown() {
		for (HttpServer server : servers) {
			server.stop(0);
		}
		servers.clear();
	}

	// ── 2 newer versions found ─────────────────────────────────────────────

	@Test
	void check_twoNewerVersions_returnsBothSortedAscending() throws IOException {
		// GitHub returns newest first; UpdateChecker sorts ascending
		String json = buildReleasesJson("v1.0.2", "v1.0.1");
		HttpServer server = startServer("/repos/test/repo/releases", json);
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:" + server.getAddress().getPort());

		UpdatePlan plan = UpdateChecker.checkInternal("test/repo", "1.0.0", settings, false, client);

		assertNotNull(plan);
		assertTrue(plan.hasUpdate());
		assertEquals(2, plan.getReleases().size());
		// Oldest first
		assertEquals("v1.0.1", plan.getReleases().get(0).getTagName());
		assertEquals("v1.0.2", plan.getReleases().get(1).getTagName());
		assertEquals("1.0.2", plan.latestVersion());
	}

	// ── Already latest → empty plan ───────────────────────────────────────

	@Test
	void check_alreadyLatest_returnsEmptyPlan() throws IOException {
		String json = buildReleasesJson("v1.0.0");
		HttpServer server = startServer("/repos/test/repo/releases", json);
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:" + server.getAddress().getPort());

		UpdatePlan plan = UpdateChecker.checkInternal("test/repo", "1.0.0", settings, false, client);

		assertNotNull(plan);
		assertFalse(plan.hasUpdate());
	}

	@Test
	void check_olderVersionOnServer_returnsEmptyPlan() throws IOException {
		String json = buildReleasesJson("v0.9.0");
		HttpServer server = startServer("/repos/test/repo/releases", json);
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:" + server.getAddress().getPort());

		UpdatePlan plan = UpdateChecker.checkInternal("test/repo", "1.0.0", settings, false, client);

		assertNotNull(plan);
		assertFalse(plan.hasUpdate());
	}

	// ── Network error → graceful IOException ──────────────────────────────

	@Test
	void check_networkError_throwsIOException() {
		// Port 1 is not listening — expect IOException
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:1");

		assertThrows(IOException.class,
				() -> UpdateChecker.checkInternal("test/repo", "1.0.0", settings, false, client));
	}

	// ── Skipped version ────────────────────────────────────────────────────

	@Test
	void check_latestVersionIsSkipped_returnsEmptyPlan() throws IOException {
		settings.setSkippedVersion("1.0.2");
		String json = buildReleasesJson("v1.0.2", "v1.0.1");
		HttpServer server = startServer("/repos/test/repo/releases", json);
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:" + server.getAddress().getPort());

		UpdatePlan plan = UpdateChecker.checkInternal("test/repo", "1.0.0", settings, false, client);

		assertNotNull(plan);
		assertFalse(plan.hasUpdate(), "Plan should be empty when latest matches skipped version");
	}

	@Test
	void check_latestVersionIsSkipped_ignoredWhenManualCheck() throws IOException {
		settings.setSkippedVersion("1.0.2");
		String json = buildReleasesJson("v1.0.2", "v1.0.1");
		HttpServer server = startServer("/repos/test/repo/releases", json);
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:" + server.getAddress().getPort());

		// ignoreSkippedVersion=true → manual check bypasses the skip filter
		UpdatePlan plan = UpdateChecker.checkInternal("test/repo", "1.0.0", settings, true, client);

		assertNotNull(plan);
		assertTrue(plan.hasUpdate(), "Manual check should show updates even when version is skipped");
	}

	@Test
	void check_intermediateVersionSkipped_latestStillShown() throws IOException {
		// Only skip 1.0.1, not 1.0.2 (the latest)
		settings.setSkippedVersion("1.0.1");
		String json = buildReleasesJson("v1.0.2", "v1.0.1");
		HttpServer server = startServer("/repos/test/repo/releases", json);
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:" + server.getAddress().getPort());

		UpdatePlan plan = UpdateChecker.checkInternal("test/repo", "1.0.0", settings, false, client);

		assertNotNull(plan);
		assertTrue(plan.hasUpdate(), "1.0.2 is newer than skipped 1.0.1, so updates should appear");
	}

	// ── Repo not configured ────────────────────────────────────────────────

	@Test
	void check_repoNotConfigured_returnsNull() throws IOException {
		GitHubReleasesClient client = new GitHubReleasesClient();

		UpdatePlan plan = UpdateChecker.checkInternal("", "1.0.0", settings, false, client);

		assertNull(plan);
	}

	@Test
	void check_repoIsPlaceholder_returnsNull() throws IOException {
		GitHubReleasesClient client = new GitHubReleasesClient();

		UpdatePlan plan = UpdateChecker.checkInternal("${github.repo}", "1.0.0", settings, false, client);

		assertNull(plan);
	}

	// ── Tag with "v" prefix stripped correctly ────────────────────────────

	@Test
	void check_tagWithVPrefix_comparedCorrectly() throws IOException {
		String json = buildReleasesJson("v1.0.1");
		HttpServer server = startServer("/repos/test/repo/releases", json);
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:" + server.getAddress().getPort());

		UpdatePlan plan = UpdateChecker.checkInternal("test/repo", "1.0.0", settings, false, client);

		assertNotNull(plan);
		assertTrue(plan.hasUpdate());
		assertEquals("1.0.1", plan.latestVersion()); // v prefix stripped in latestVersion()
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	private String buildReleasesJson(String... tagNames) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < tagNames.length; i++) {
			if (i > 0) sb.append(",");
			String tag = tagNames[i];
			String version = tag.startsWith("v") ? tag.substring(1) : tag;
			sb.append("{\"tag_name\":\"").append(tag).append("\",").append("\"name\":\"Release ").append(version)
					.append("\",").append("\"body\":\"Changelog for ").append(version).append("\",")
					.append("\"draft\":false,").append("\"prerelease\":false,").append("\"assets\":[{\"name\":\"app-")
					.append(version).append(".jar\",").append("\"browser_download_url\":\"https://example.com/app-")
					.append(version).append(".jar\",").append("\"size\":1024}]}");
		}
		sb.append("]");
		return sb.toString();
	}

	private HttpServer startServer(String path, String jsonContent) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		byte[] bytes = jsonContent.getBytes(StandardCharsets.UTF_8);
		server.createContext(path, exchange -> {
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		server.start();
		servers.add(server);
		return server;
	}

}
