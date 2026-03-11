package net.classiclauncher.launcher.account;

import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import com.sun.net.httpserver.HttpServer;

/**
 * Utilities for OAuth / browser-based authentication flows.
 *
 * <p>
 * Uses only JDK APIs — no extra dependencies required.
 */
public final class BrowserAuthHelper {

	private static final String AUTH_COMPLETE_HTML = "<!DOCTYPE html><html><head><title>Authentication Complete</title></head><body>"
			+ "<h2>Authentication complete!</h2>" + "<p>You may close this tab and return to the launcher.</p>"
			+ "</body></html>";

	private BrowserAuthHelper() {
	}

	/**
	 * Opens the given URL in the system default browser. Silently ignores failures (e.g. headless environments or
	 * unsupported desktop actions).
	 *
	 * @param url
	 *            the URL to open
	 */
	public static void openUrl(String url) {
		if (!Desktop.isDesktopSupported()) return;
		Desktop desktop = Desktop.getDesktop();
		if (!desktop.isSupported(Desktop.Action.BROWSE)) return;
		try {
			desktop.browse(new URI(url));
		} catch (IOException | URISyntaxException ignored) {
			// User can paste the URL manually if the browser cannot be opened
		}
	}

	/**
	 * Starts a lightweight HTTP server that listens on {@code port} for the OAuth callback.
	 *
	 * <p>
	 * When a request arrives the server:
	 * <ol>
	 * <li>Parses all query parameters from the URL.</li>
	 * <li>Serves a minimal "auth complete" HTML page to the browser.</li>
	 * <li>Calls {@code onCallback} with the request path and parsed parameters.</li>
	 * <li>Shuts itself down on a daemon thread 200 ms later.</li>
	 * </ol>
	 *
	 * @param port
	 *            the local port to listen on (e.g. 8765)
	 * @param onCallback
	 *            invoked once with (requestPath, queryParams) when the callback arrives
	 * @return the running {@link HttpServer} (already started); callers may shut it down early if needed
	 * @throws RuntimeException
	 *             if the server cannot bind to the given port
	 */
	public static HttpServer startCallbackServer(int port, BiConsumer<String, Map<String, String>> onCallback) {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
			server.createContext("/", exchange -> {
				String query = exchange.getRequestURI().getQuery();
				Map<String, String> params = parseQuery(query);

				byte[] body = AUTH_COMPLETE_HTML.getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(body);
				}

				// Shut down after response is delivered
				Thread stopThread = new Thread(() -> {
					try {
						Thread.sleep(200);
					} catch (InterruptedException ignored) {
					}
					server.stop(0);
				});
				stopThread.setDaemon(true);
				stopThread.start();

				onCallback.accept(exchange.getRequestURI().getPath(), params);
			});
			server.setExecutor(null);
			server.start();
			return server;
		} catch (IOException e) {
			throw new RuntimeException("Failed to start OAuth callback server on port " + port, e);
		}
	}

	/**
	 * Parses a URL query string into a key→value map. Keys and values are returned as-is (not URL-decoded).
	 */
	private static Map<String, String> parseQuery(String query) {
		Map<String, String> params = new LinkedHashMap<>();
		if (query == null || query.isEmpty()) return params;
		for (String pair : query.split("&")) {
			int eq = pair.indexOf('=');
			if (eq > 0) {
				params.put(pair.substring(0, eq), pair.substring(eq + 1));
			} else if (!pair.isEmpty()) {
				params.put(pair, "");
			}
		}
		return params;
	}

}
