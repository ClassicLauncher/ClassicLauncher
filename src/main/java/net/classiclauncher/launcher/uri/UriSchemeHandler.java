package net.classiclauncher.launcher.uri;

import java.io.File;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stateless parser for {@code classiclauncher://} URIs.
 *
 * <p>
 * All methods are static; this class cannot be instantiated.
 *
 * <h3>Supported URI formats</h3>
 * <ul>
 * <li>{@code classiclauncher://install?url=<encoded-manifest-url>} — remote install</li>
 * <li>{@code classiclauncher://install?manifest=<encoded-path>&jar=<encoded-path>} — local install</li>
 * </ul>
 *
 * <p>
 * Relative paths in {@code manifest} and {@code jar} parameters are resolved against the directory that contains the
 * launcher JAR (i.e. the install directory when running from a jpackage native installer). If the JAR location cannot
 * be determined, relative paths fall back to {@code user.dir}.
 */
public final class UriSchemeHandler {

	private static final String SCHEME = "classiclauncher://";

	private UriSchemeHandler() {
	}

	/**
	 * Returns {@code true} if the given string starts with the {@code classiclauncher://} scheme (case-insensitive). A
	 * {@code null} or empty input returns {@code false}.
	 */
	public static boolean isClassicLauncherUri(String uri) {
		return uri != null && uri.toLowerCase().startsWith(SCHEME);
	}

	/**
	 * Parses a {@code classiclauncher://install} URI and returns the corresponding {@link ExtensionInstallRequest}.
	 *
	 * @param rawUri
	 *            the full URI string (e.g. {@code classiclauncher://install?url=https://...})
	 * @return a fully populated {@link ExtensionInstallRequest}
	 * @throws IllegalArgumentException
	 *             if the URI scheme is not {@code classiclauncher://}, the host is not {@code install}, or the required
	 *             query parameters are missing
	 */
	public static ExtensionInstallRequest parse(String rawUri) {
		if (!isClassicLauncherUri(rawUri))
			throw new IllegalArgumentException("Not a classiclauncher:// URI: " + rawUri);

		// Strip scheme, then extract host (the part before '?' or '/')
		String withoutScheme = rawUri.substring(SCHEME.length());
		int questionIdx = withoutScheme.indexOf('?');
		int slashIdx = withoutScheme.indexOf('/');

		String host;
		if (questionIdx >= 0 && (slashIdx < 0 || questionIdx < slashIdx)) {
			host = withoutScheme.substring(0, questionIdx);
		} else if (slashIdx >= 0) {
			host = withoutScheme.substring(0, slashIdx);
		} else {
			host = withoutScheme;
		}

		if (!host.equalsIgnoreCase("install"))
			throw new IllegalArgumentException("Unknown URI action: \"" + host + "\". Only 'install' is supported.");

		Map<String, String> params = parseQueryParams(rawUri);

		if (params.containsKey("url")) {
			String url = params.get("url");
			if (url.isEmpty()) throw new IllegalArgumentException("'url' parameter must not be empty");
			return ExtensionInstallRequest.remote(url);

		} else if (params.containsKey("manifest") && params.containsKey("jar")) {
			File manifestFile = resolvePath(params.get("manifest"));
			File jarFile = resolvePath(params.get("jar"));
			return ExtensionInstallRequest.local(manifestFile, jarFile);

		} else {
			throw new IllegalArgumentException("URI must contain either a 'url' parameter (remote install) "
					+ "or both 'manifest' and 'jar' parameters (local install). " + "Received params: "
					+ params.keySet());
		}
	}

	/**
	 * Parses the query string of the given URI into a key→value map. Values are percent-decoded using UTF-8. Duplicate
	 * keys: last one wins.
	 *
	 * @param uri
	 *            the raw URI string (scheme + host + path + ?query)
	 * @return a mutable map of decoded parameter names to decoded values; empty if no query string
	 */
	static Map<String, String> parseQueryParams(String uri) {
		Map<String, String> params = new LinkedHashMap<>();
		int queryIdx = uri.indexOf('?');
		if (queryIdx < 0) return params;

		String query = uri.substring(queryIdx + 1);
		for (String pair : query.split("&")) {
			if (pair.isEmpty()) continue;
			int eqIdx = pair.indexOf('=');
			if (eqIdx < 0) continue;
			try {
				String key = URLDecoder.decode(pair.substring(0, eqIdx), "UTF-8");
				String value = URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8");
				params.put(key, value);
			} catch (Exception e) {
				// Skip malformed parameter pair; continue parsing the rest
			}
		}
		return params;
	}

	/**
	 * Resolves a path string to a {@link File}.
	 *
	 * <ul>
	 * <li>Absolute paths are returned as-is.</li>
	 * <li>Relative paths are resolved against the directory containing the launcher JAR (the install directory for
	 * jpackage builds), falling back to {@code user.dir} when the code-source location cannot be determined (e.g. when
	 * running from the IDE).</li>
	 * </ul>
	 *
	 * @param path
	 *            the path string (absolute or relative)
	 * @return the resolved {@link File}; never null
	 * @throws IllegalArgumentException
	 *             if {@code path} is null or empty
	 */
	static File resolvePath(String path) {
		if (path == null || path.isEmpty()) throw new IllegalArgumentException("path must not be null or empty");

		File f = new File(path);
		if (f.isAbsolute()) return f;

		// Relative path: resolve against the launcher JAR's parent directory
		File base = getExecutableDir();
		return new File(base, path);
	}

	/**
	 * Returns the directory containing the launcher JAR, or {@code user.dir} as a fallback. Uses the code-source
	 * location of this class so it works correctly when running from a jpackage installer (where the JAR is in the
	 * app's lib/ directory).
	 */
	private static File getExecutableDir() {
		try {
			java.security.CodeSource cs = UriSchemeHandler.class.getProtectionDomain().getCodeSource();
			if (cs != null && cs.getLocation() != null) {
				File f = new File(cs.getLocation().toURI());
				return f.isFile() ? f.getParentFile() : f;
			}
		} catch (Exception ignored) {
			// Fall through to user.dir
		}
		return new File(System.getProperty("user.dir", "."));
	}

}
