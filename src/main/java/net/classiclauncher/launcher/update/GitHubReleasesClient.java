package net.classiclauncher.launcher.update;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import net.classiclauncher.launcher.LauncherVersion;

/**
 * Thin HTTP client for the GitHub Releases API.
 *
 * <p>
 * Operates on full URLs (not base+relative like {@code HttpGameApi}) because the API endpoint is fixed and known at
 * construction time.
 *
 * <p>
 * Security measures applied:
 * <ul>
 * <li>Only {@code http} and {@code https} schemes are permitted (including after redirects).</li>
 * <li>Maximum of {@value MAX_REDIRECTS} redirects followed per request.</li>
 * <li>Response size capped at {@value MAX_RESPONSE_BYTES} bytes.</li>
 * <li>Standard {@code User-Agent} header with launcher version.</li>
 * </ul>
 */
public class GitHubReleasesClient {

	private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024; // 10 MiB
	private static final int CONNECT_TIMEOUT_MS = 5_000;
	private static final int READ_TIMEOUT_MS = 15_000;
	private static final int MAX_REDIRECTS = 5;

	private static final String DEFAULT_API_BASE = "https://api.github.com";

	private final String apiBaseUrl;

	/** Creates a client that targets the real GitHub API ({@code https://api.github.com}). */
	public GitHubReleasesClient() {
		this(DEFAULT_API_BASE);
	}

	/**
	 * Creates a client that targets a custom base URL. Intended for testing — pass {@code "http://localhost:PORT"} to
	 * redirect requests to a local test server.
	 *
	 * @param apiBaseUrl
	 *            base URL without trailing slash (e.g. {@code "http://localhost:8080"})
	 */
	GitHubReleasesClient(String apiBaseUrl) {
		this.apiBaseUrl = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
	}

	/**
	 * Fetches the raw JSON string from {@code GET {apiBase}/repos/{repo}/releases}.
	 *
	 * @param repo
	 *            GitHub repository in {@code owner/name} form (e.g. {@code "ClassicLauncher/ClassicLauncher"})
	 * @return raw JSON string
	 * @throws IOException
	 *             on network error, HTTP error, or response too large
	 */
	public String fetchReleasesJson(String repo) throws IOException {
		if (repo == null || repo.trim().isEmpty()) {
			throw new IOException("GitHub repo must not be null or blank");
		}
		String url = apiBaseUrl + "/repos/" + repo.trim() + "/releases";
		byte[] bytes = fetchBytes(url);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private byte[] fetchBytes(String urlString) throws IOException {
		URL url = new URL(urlString);
		validateScheme(url.getProtocol());

		int redirectCount = 0;
		while (true) {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setInstanceFollowRedirects(false);
			conn.setRequestProperty("User-Agent", "ClassicLauncher/" + LauncherVersion.VERSION);
			conn.setRequestProperty("Accept", "application/vnd.github+json");
			conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

			try {
				int status = conn.getResponseCode();

				if (status >= 300 && status < 400) {
					if (redirectCount >= MAX_REDIRECTS) {
						throw new IOException("Too many redirects (max " + MAX_REDIRECTS + ")");
					}
					String location = conn.getHeaderField("Location");
					if (location == null || location.isEmpty()) {
						throw new IOException("Redirect with no Location header (HTTP " + status + ")");
					}
					URL redirectUrl = new URL(location);
					validateScheme(redirectUrl.getProtocol());
					url = redirectUrl;
					redirectCount++;
					continue;
				}

				if (status == 403 || status == 429) {
					throw new IOException("GitHub API rate limited (HTTP " + status + "). Try again later.");
				}

				if (status >= 400 && status < 500) {
					throw new IOException("GitHub API client error (HTTP " + status + ")");
				}

				if (status < 200 || status >= 300) {
					throw new IOException("Unexpected HTTP status: " + status);
				}

				try (InputStream in = conn.getInputStream()) {
					return readCapped(in);
				}
			} finally {
				conn.disconnect();
			}
		}
	}

	private static void validateScheme(String scheme) throws IOException {
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			throw new IOException("Only http and https schemes are allowed, got: " + scheme);
		}
	}

	private static byte[] readCapped(InputStream in) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
		byte[] chunk = new byte[8192];
		int totalRead = 0;
		int read;
		while ((read = in.read(chunk)) != -1) {
			totalRead += read;
			if (totalRead > MAX_RESPONSE_BYTES) {
				throw new IOException("Response exceeds maximum allowed size of " + MAX_RESPONSE_BYTES + " bytes");
			}
			buffer.write(chunk, 0, read);
		}
		return buffer.toByteArray();
	}

}
