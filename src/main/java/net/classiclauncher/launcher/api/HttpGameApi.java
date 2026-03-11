package net.classiclauncher.launcher.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import net.classiclauncher.launcher.LauncherVersion;

/**
 * Abstract base class for {@link GameApi} implementations that communicate over HTTP/HTTPS.
 *
 * <p>
 * Provides production-ready helpers with:
 * <ul>
 * <li>Scheme validation (only {@code http} and {@code https} are allowed)</li>
 * <li>Path-traversal prevention (rejects {@code ..} and {@code .} segments)</li>
 * <li>Automatic redirect following (up to 5 hops; cross-host and non-http/s schemes rejected)</li>
 * <li>Response-size cap ({@value MAX_RESPONSE_BYTES} bytes — prevents memory exhaustion)</li>
 * <li>Exponential-backoff retry on network errors (3 attempts; 4xx responses are not retried)</li>
 * <li>Standard {@code User-Agent} header identifying the launcher version</li>
 * </ul>
 *
 * <p>
 * Subclasses implement {@link GameApi#getAvailableVersions()} and {@link GameApi#getVersion(String)} using the
 * protected helpers {@link #fetchText(String)} and {@link #fetchBytes(String)}.
 *
 * <pre>{@code
 * public class MojangVersionApi extends HttpGameApi {
 *
 * 	public MojangVersionApi() {
 * 		super("https://launchermeta.mojang.com");
 * 	}
 *
 * 	@Override
 * 	public List<Version> getAvailableVersions() {
 * 		try {
 * 			String json = fetchText("/mc/game/version_manifest_v2.json");
 * 			return parseVersionManifest(json);
 * 		} catch (IOException e) {
 * 			return Collections.emptyList();
 * 		}
 * 	}
 *
 * }
 * }</pre>
 */
public abstract class HttpGameApi implements GameApi {

	/**
	 * Maximum number of bytes read from a single HTTP response (10 MiB).
	 */
	protected static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;

	/**
	 * Maximum number of retry attempts for transient network errors.
	 */
	protected static final int MAX_RETRIES = 3;

	/**
	 * Initial back-off delay in milliseconds before the first retry. Doubles on each attempt.
	 */
	protected static final long INITIAL_BACKOFF_MS = 250L;

	/**
	 * Maximum number of HTTP redirects followed per request.
	 */
	private static final int MAX_REDIRECTS = 5;

	private final String baseUrl;
	private final int connectTimeoutMs;
	private final int readTimeoutMs;

	/**
	 * Creates an {@code HttpGameApi} with default timeouts (5 000 ms connect, 15 000 ms read).
	 *
	 * @param baseUrl
	 *            the root URL of the API (e.g. {@code "https://api.example.com"}); trailing slashes are stripped; must
	 *            use {@code http} or {@code https}
	 * @throws IllegalArgumentException
	 *             if {@code baseUrl} is null, blank, or uses an unsupported scheme
	 */
	protected HttpGameApi(String baseUrl) {
		this(baseUrl, 5_000, 15_000);
	}

	/**
	 * Creates an {@code HttpGameApi} with explicit timeouts.
	 *
	 * @param baseUrl
	 *            the root URL of the API; trailing slashes are stripped
	 * @param connectTimeoutMs
	 *            TCP connection timeout in milliseconds (must be &gt; 0)
	 * @param readTimeoutMs
	 *            socket read timeout in milliseconds (must be &gt; 0)
	 * @throws IllegalArgumentException
	 *             if {@code baseUrl} is invalid or timeouts are non-positive
	 */
	protected HttpGameApi(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
		if (baseUrl == null || baseUrl.trim().isEmpty()) {
			throw new IllegalArgumentException("baseUrl must not be null or blank");
		}
		if (connectTimeoutMs <= 0) {
			throw new IllegalArgumentException("connectTimeoutMs must be > 0, got: " + connectTimeoutMs);
		}
		if (readTimeoutMs <= 0) {
			throw new IllegalArgumentException("readTimeoutMs must be > 0, got: " + readTimeoutMs);
		}
		String trimmed = baseUrl.trim();
		// Strip trailing slash(es)
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		validateScheme(trimmed);
		this.baseUrl = trimmed;
		this.connectTimeoutMs = connectTimeoutMs;
		this.readTimeoutMs = readTimeoutMs;
	}

	// ── GameApi ───────────────────────────────────────────────────────────────

	@Override
	public final String getBaseUrl() {
		return baseUrl;
	}

	// ── Protected helpers ─────────────────────────────────────────────────────

	/**
	 * Performs a GET request for {@code relativePath} and returns the response body as a UTF-8 decoded string.
	 *
	 * @param relativePath
	 *            path relative to {@link #getBaseUrl()} (must start with {@code /})
	 * @return the response body as a UTF-8 string
	 * @throws IOException
	 *             if the request fails, the scheme is invalid, the path contains traversal segments, or the response
	 *             exceeds {@value MAX_RESPONSE_BYTES} bytes
	 */
	protected final String fetchText(String relativePath) throws IOException {
		return new String(fetchBytes(relativePath), StandardCharsets.UTF_8);
	}

	/**
	 * Performs a GET request for {@code relativePath} and returns the raw response bytes. Retries up to
	 * {@value MAX_RETRIES} times on transient network errors with exponential back-off; 4xx client errors are not
	 * retried.
	 *
	 * @param relativePath
	 *            path relative to {@link #getBaseUrl()} (must start with {@code /})
	 * @return the raw response body
	 * @throws IOException
	 *             if all retry attempts fail or a non-retryable error occurs
	 */
	protected final byte[] fetchBytes(String relativePath) throws IOException {
		IOException lastException = null;
		long backoff = INITIAL_BACKOFF_MS;

		for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
			try {
				return doGet(relativePath);
			} catch (ClientErrorException e) {
				// 4xx: no point retrying
				throw new IOException("HTTP client error: " + e.getMessage(), e);
			} catch (IOException e) {
				lastException = e;
				if (attempt < MAX_RETRIES) {
					try {
						Thread.sleep(backoff);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						throw new IOException("Request interrupted during retry back-off", ie);
					}
					backoff *= 2;
				}
			}
		}
		throw new IOException("Request failed after " + MAX_RETRIES + " attempts: " + relativePath, lastException);
	}

	// ── Private internals ─────────────────────────────────────────────────────

	/**
	 * Builds the full URL, validates it, opens the connection, follows redirects, reads and returns the response body.
	 *
	 * @throws ClientErrorException
	 *             if the server responds with 4xx
	 * @throws IOException
	 *             for any other failure
	 */
	private byte[] doGet(String relativePath) throws IOException {
		URL url = buildAndValidateUrl(baseUrl, relativePath);
		int redirectCount = 0;
		String currentHost = url.getHost();

		while (true) {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(connectTimeoutMs);
			conn.setReadTimeout(readTimeoutMs);
			conn.setInstanceFollowRedirects(false); // manual redirect handling
			conn.setRequestProperty("User-Agent", "ClassicLauncher/" + LauncherVersion.VERSION);
			conn.setRequestProperty("Accept", "*/*");

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
					URL redirectUrl = new URL(url, location);
					String redirectScheme = redirectUrl.getProtocol();
					if (!redirectScheme.equals("http") && !redirectScheme.equals("https")) {
						throw new IOException("Redirect to disallowed scheme: " + redirectScheme);
					}
					String redirectHost = redirectUrl.getHost();
					if (!redirectHost.equalsIgnoreCase(currentHost)) {
						throw new IOException("Cross-host redirect rejected: " + currentHost + " → " + redirectHost);
					}
					url = redirectUrl;
					redirectCount++;
					continue;
				}

				if (status >= 400 && status < 500) {
					throw new ClientErrorException(status + " " + conn.getResponseMessage());
				}

				if (status < 200 || status >= 300) {
					throw new IOException("Unexpected HTTP status: " + status + " " + conn.getResponseMessage());
				}

				try (InputStream in = conn.getInputStream()) {
					return readCapped(in);
				}
			} finally {
				conn.disconnect();
			}
		}
	}

	/**
	 * Reads at most {@value MAX_RESPONSE_BYTES} bytes from {@code in}.
	 *
	 * @throws IOException
	 *             if the stream exceeds the cap
	 */
	private static byte[] readCapped(InputStream in) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(MAX_RESPONSE_BYTES, 8192));
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

	/**
	 * Combines {@code baseUrl} with {@code relativePath}, validates the resulting URL, and returns it.
	 *
	 * @throws MalformedURLException
	 *             if the URL is invalid or the path contains traversal segments
	 */
	private static URL buildAndValidateUrl(String baseUrl, String relativePath) throws MalformedURLException {
		if (relativePath == null) {
			throw new MalformedURLException("relativePath must not be null");
		}
		// Require leading slash for clarity; also prevents double-slash ambiguity
		String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;

		// Path traversal prevention: reject any segment that is "." or ".."
		String[] segments = path.split("/", -1);
		for (String segment : segments) {
			if (".".equals(segment) || "..".equals(segment)) {
				throw new MalformedURLException("Path traversal detected in relative path: " + relativePath);
			}
		}

		URL url;
		try {
			url = new URL(baseUrl + path);
		} catch (Exception e) {
			throw new MalformedURLException("Invalid URL: " + baseUrl + path);
		}
		validateScheme(url.toExternalForm());
		return url;
	}

	/**
	 * Throws {@link IllegalArgumentException} if the scheme is not {@code http} or {@code https}.
	 */
	private static void validateScheme(String urlString) {
		String lower = urlString.toLowerCase();
		if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
			throw new IllegalArgumentException("Only http and https schemes are allowed, got: " + urlString);
		}
	}

	// ── Inner sentinel exception ──────────────────────────────────────────────

	/**
	 * Signals a 4xx response that should not be retried.
	 */
	private static final class ClientErrorException extends IOException {

		ClientErrorException(String message) {
			super(message);
		}

	}

}
