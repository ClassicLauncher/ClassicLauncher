package net.classiclauncher.launcher.update.source.github;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import net.classiclauncher.launcher.update.ReleaseInfo;

class GitHubReleaseSourceTest {

	private final List<HttpServer> servers = new ArrayList<>();

	@AfterEach
	void tearDown() {
		for (HttpServer server : servers) {
			server.stop(0);
		}
		servers.clear();
	}

	// ── fetchReleases returns parsed releases ─────────────────────────────

	@Test
	void fetchReleases_twoReleases_parsedCorrectly() throws IOException {
		String json = "["
				+ "{\"tag_name\":\"v1.0.2\",\"name\":\"1.0.2\",\"body\":\"B\",\"draft\":false,\"prerelease\":false,"
				+ "\"assets\":[{\"name\":\"app.jar\",\"browser_download_url\":\"https://example.com/app.jar\",\"size\":1024}]},"
				+ "{\"tag_name\":\"v1.0.1\",\"name\":\"1.0.1\",\"body\":\"A\",\"draft\":false,\"prerelease\":false,\"assets\":[]}"
				+ "]";
		HttpServer server = startServer("/repos/test/repo/releases", json);
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:" + server.getAddress().getPort());
		GitHubReleaseSource source = new GitHubReleaseSource("test/repo", client);

		List<ReleaseInfo> releases = source.fetchReleases();

		assertEquals(2, releases.size());
		assertEquals("v1.0.2", releases.get(0).getTagName());
		assertEquals("v1.0.1", releases.get(1).getTagName());
		assertEquals(1, releases.get(0).getAssets().size());
	}

	// ── fetchReleases filters drafts and prereleases ──────────────────────

	@Test
	void fetchReleases_draftsAndPrereleases_filtered() throws IOException {
		String json = "["
				+ "{\"tag_name\":\"v2.0.0\",\"name\":\"D\",\"body\":\"\",\"draft\":true,\"prerelease\":false,\"assets\":[]},"
				+ "{\"tag_name\":\"v1.5.0-rc\",\"name\":\"P\",\"body\":\"\",\"draft\":false,\"prerelease\":true,\"assets\":[]},"
				+ "{\"tag_name\":\"v1.0.1\",\"name\":\"S\",\"body\":\"\",\"draft\":false,\"prerelease\":false,\"assets\":[]}"
				+ "]";
		HttpServer server = startServer("/repos/test/repo/releases", json);
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:" + server.getAddress().getPort());
		GitHubReleaseSource source = new GitHubReleaseSource("test/repo", client);

		List<ReleaseInfo> releases = source.fetchReleases();

		assertEquals(1, releases.size());
		assertEquals("v1.0.1", releases.get(0).getTagName());
	}

	// ── fetchReleases with empty response ─────────────────────────────────

	@Test
	void fetchReleases_emptyArray_returnsEmptyList() throws IOException {
		HttpServer server = startServer("/repos/test/repo/releases", "[]");
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:" + server.getAddress().getPort());
		GitHubReleaseSource source = new GitHubReleaseSource("test/repo", client);

		List<ReleaseInfo> releases = source.fetchReleases();

		assertTrue(releases.isEmpty());
	}

	// ── fetchReleases with network error ──────────────────────────────────

	@Test
	void fetchReleases_networkError_throwsIOException() {
		// Port 1 is not listening — expect IOException
		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:1");
		GitHubReleaseSource source = new GitHubReleaseSource("test/repo", client);

		assertThrows(IOException.class, source::fetchReleases);
	}

	// ── fetchReleases with HTTP error ─────────────────────────────────────

	@Test
	void fetchReleases_httpError_throwsIOException() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/repos/test/repo/releases", exchange -> {
			exchange.sendResponseHeaders(404, -1);
			exchange.close();
		});
		server.start();
		servers.add(server);

		GitHubReleasesClient client = new GitHubReleasesClient("http://localhost:" + server.getAddress().getPort());
		GitHubReleaseSource source = new GitHubReleaseSource("test/repo", client);

		assertThrows(IOException.class, source::fetchReleases);
	}

	// ── fromLauncherConfig ────────────────────────────────────────────────

	@Test
	void fromLauncherConfig_returnsNonNullWhenConfigured() {
		// Maven filters github.repo to "ClassicLauncher/ClassicLauncher" at build time
		GitHubReleaseSource source = GitHubReleaseSource.fromLauncherConfig();

		assertNotNull(source, "Expected non-null when GITHUB_REPO is configured via Maven");
	}

	// ── Helpers ────────────────────────────────────────────────────────────

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
