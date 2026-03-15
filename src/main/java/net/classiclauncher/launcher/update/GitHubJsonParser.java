package net.classiclauncher.launcher.update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimal, dependency-free parser for the GitHub Releases API JSON response.
 *
 * <p>
 * Parses the top-level releases array returned by {@code GET https://api.github.com/repos/{owner}/{repo}/releases}.
 * Draft and pre-release entries are filtered out. The JSON is processed with a single-pass index-based scanner that
 * correctly handles nested objects, arrays, and all standard JSON string escape sequences (including Unicode escapes).
 *
 * <p>
 * Only the fields needed by the update system are extracted — all other fields are skipped efficiently.
 */
final class GitHubJsonParser {

	private String json;
	private int pos;

	private GitHubJsonParser() {
	}

	/**
	 * Parses a GitHub releases JSON array string and returns the non-draft, non-prerelease entries.
	 *
	 * @param json
	 *            raw JSON string from the GitHub Releases API
	 * @return parsed releases; empty list on parse failure or empty input
	 */
	static List<ReleaseInfo> parse(String json) {
		if (json == null || json.trim().isEmpty()) return Collections.emptyList();
		GitHubJsonParser parser = new GitHubJsonParser();
		parser.json = json;
		parser.pos = 0;
		try {
			return parser.parseReleasesArray();
		} catch (Exception e) {
			return Collections.emptyList();
		}
	}

	// ── Top-level array ───────────────────────────────────────────────────────

	private List<ReleaseInfo> parseReleasesArray() {
		skipWhitespace();
		if (pos >= json.length() || json.charAt(pos) != '[') return Collections.emptyList();
		pos++; // consume '['

		List<ReleaseInfo> releases = new ArrayList<>();
		skipWhitespace();

		while (pos < json.length() && json.charAt(pos) != ']') {
			skipWhitespace();
			if (pos < json.length() && json.charAt(pos) == '{') {
				ReleaseInfo release = parseReleaseObject();
				if (release != null) {
					releases.add(release);
				}
			} else {
				skipValue();
			}
			skipWhitespace();
			if (pos < json.length() && json.charAt(pos) == ',') {
				pos++;
				skipWhitespace();
			}
		}

		return releases;
	}

	// ── Release object ────────────────────────────────────────────────────────

	private ReleaseInfo parseReleaseObject() {
		pos++; // consume '{'

		String tagName = "";
		String name = "";
		String body = "";
		boolean draft = false;
		boolean prerelease = false;
		List<AssetInfo> assets = Collections.emptyList();

		skipWhitespace();
		while (pos < json.length() && json.charAt(pos) != '}') {
			skipWhitespace();
			if (pos >= json.length() || json.charAt(pos) == '}') break;
			String key = readString();
			skipWhitespace();
			if (pos < json.length() && json.charAt(pos) == ':') pos++;
			skipWhitespace();

			if ("tag_name".equals(key)) {
				tagName = readString();
			} else if ("name".equals(key)) {
				name = readString();
			} else if ("body".equals(key)) {
				body = readNullableString();
			} else if ("draft".equals(key)) {
				draft = readBoolean();
			} else if ("prerelease".equals(key)) {
				prerelease = readBoolean();
			} else if ("assets".equals(key)) {
				assets = parseAssetsArray();
			} else {
				skipValue();
			}

			skipWhitespace();
			if (pos < json.length() && json.charAt(pos) == ',') {
				pos++;
				skipWhitespace();
			}
		}

		if (pos < json.length() && json.charAt(pos) == '}') pos();

		// Filter out drafts and pre-releases
		if (draft || prerelease) return null;
		if (tagName.isEmpty()) return null;

		return new ReleaseInfo(tagName, name, body, assets);
	}

	// ── Assets array ──────────────────────────────────────────────────────────

	private List<AssetInfo> parseAssetsArray() {
		skipWhitespace();
		if (pos >= json.length() || json.charAt(pos) != '[') {
			skipValue();
			return Collections.emptyList();
		}
		pos++; // consume '['

		List<AssetInfo> assets = new ArrayList<>();
		skipWhitespace();

		while (pos < json.length() && json.charAt(pos) != ']') {
			skipWhitespace();
			if (pos < json.length() && json.charAt(pos) == '{') {
				AssetInfo asset = parseAssetObject();
				if (asset != null) assets.add(asset);
			} else {
				skipValue();
			}
			skipWhitespace();
			if (pos < json.length() && json.charAt(pos) == ',') {
				pos++;
				skipWhitespace();
			}
		}

		if (pos < json.length() && json.charAt(pos) == ']') pos();
		return assets;
	}

	// ── Asset object ──────────────────────────────────────────────────────────

	private AssetInfo parseAssetObject() {
		pos++; // consume '{'

		String assetName = "";
		String downloadUrl = "";
		long sizeBytes = 0;

		skipWhitespace();
		while (pos < json.length() && json.charAt(pos) != '}') {
			skipWhitespace();
			if (pos >= json.length() || json.charAt(pos) == '}') break;
			String key = readString();
			skipWhitespace();
			if (pos < json.length() && json.charAt(pos) == ':') pos();
			skipWhitespace();

			if ("name".equals(key)) {
				assetName = readString();
			} else if ("browser_download_url".equals(key)) {
				downloadUrl = readString();
			} else if ("size".equals(key)) {
				sizeBytes = readLong();
			} else {
				skipValue();
			}

			skipWhitespace();
			if (pos < json.length() && json.charAt(pos) == ',') {
				pos();
				skipWhitespace();
			}
		}

		if (pos < json.length() && json.charAt(pos) == '}') pos();

		if (assetName.isEmpty() || downloadUrl.isEmpty()) return null;
		return new AssetInfo(assetName, downloadUrl, sizeBytes);
	}

	// ── JSON primitives ───────────────────────────────────────────────────────

	private String readString() {
		skipWhitespace();
		if (pos >= json.length() || json.charAt(pos) != '"') return "";
		pos++; // consume opening '"'

		StringBuilder sb = new StringBuilder();
		while (pos < json.length()) {
			char c = json.charAt(pos++);
			if (c == '"') break;
			if (c == '\\' && pos < json.length()) {
				char esc = json.charAt(pos++);
				switch (esc) {
					case '"' :
						sb.append('"');
						break;
					case '\\' :
						sb.append('\\');
						break;
					case '/' :
						sb.append('/');
						break;
					case 'n' :
						sb.append('\n');
						break;
					case 'r' :
						sb.append('\r');
						break;
					case 't' :
						sb.append('\t');
						break;
					case 'b' :
						sb.append('\b');
						break;
					case 'f' :
						sb.append('\f');
						break;
					case 'u' :
						if (pos + 4 <= json.length()) {
							try {
								int codePoint = Integer.parseInt(json.substring(pos, pos + 4), 16);
								sb.append((char) codePoint);
								pos += 4;
							} catch (NumberFormatException e) {
								sb.append('\\').append('u');
							}
						}
						break;
					default :
						sb.append(esc);
				}
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	/**
	 * Reads a string value that may be JSON {@code null}. Returns an empty string for {@code null}.
	 */
	private String readNullableString() {
		skipWhitespace();
		if (pos + 4 <= json.length() && json.substring(pos, pos + 4).equals("null")) {
			pos += 4;
			return "";
		}
		return readString();
	}

	private boolean readBoolean() {
		skipWhitespace();
		if (pos + 4 <= json.length() && json.substring(pos, pos + 4).equals("true")) {
			pos += 4;
			return true;
		}
		if (pos + 5 <= json.length() && json.substring(pos, pos + 5).equals("false")) {
			pos += 5;
		}
		return false;
	}

	private long readLong() {
		skipWhitespace();
		StringBuilder sb = new StringBuilder();
		if (pos < json.length() && json.charAt(pos) == '-') {
			sb.append(json.charAt(pos++));
		}
		while (pos < json.length() && Character.isDigit(json.charAt(pos))) {
			sb.append(json.charAt(pos++));
		}
		// Skip any trailing decimal/exponent (not expected for sizes, but be safe)
		if (pos < json.length() && (json.charAt(pos) == '.' || json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
			while (pos < json.length() && !isDelimiter(json.charAt(pos)))
				pos++;
		}
		if (sb.length() == 0) return 0;
		try {
			return Long.parseLong(sb.toString());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Skips any JSON value (string, number, boolean, null, object, array). */
	private void skipValue() {
		skipWhitespace();
		if (pos >= json.length()) return;
		char c = json.charAt(pos);
		if (c == '"') {
			readString();
		} else if (c == '{') {
			skipObject();
		} else if (c == '[') {
			skipArray();
		} else if (c == 't' || c == 'f' || c == 'n') {
			while (pos < json.length() && Character.isLetter(json.charAt(pos)))
				pos++;
		} else {
			while (pos < json.length() && !isDelimiter(json.charAt(pos)))
				pos++;
		}
	}

	private void skipObject() {
		if (pos >= json.length() || json.charAt(pos) != '{') return;
		pos++;
		int depth = 1;
		while (pos < json.length() && depth > 0) {
			char c = json.charAt(pos++);
			if (c == '"') {
				while (pos < json.length()) {
					char sc = json.charAt(pos++);
					if (sc == '\\') {
						pos++;
						continue;
					}
					if (sc == '"') break;
				}
			} else if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
			}
		}
	}

	private void skipArray() {
		if (pos >= json.length() || json.charAt(pos) != '[') return;
		pos++;
		int depth = 1;
		while (pos < json.length() && depth > 0) {
			char c = json.charAt(pos++);
			if (c == '"') {
				while (pos < json.length()) {
					char sc = json.charAt(pos++);
					if (sc == '\\') {
						pos++;
						continue;
					}
					if (sc == '"') break;
				}
			} else if (c == '[') {
				depth++;
			} else if (c == ']') {
				depth--;
			}
		}
	}

	private void skipWhitespace() {
		while (pos < json.length() && Character.isWhitespace(json.charAt(pos)))
			pos++;
	}

	/** Advances {@code pos} by one, used in place of bare {@code pos++} for clarity. */
	private void pos() {
		pos++;
	}

	private boolean isDelimiter(char c) {
		return c == ',' || c == '}' || c == ']' || Character.isWhitespace(c);
	}

}
