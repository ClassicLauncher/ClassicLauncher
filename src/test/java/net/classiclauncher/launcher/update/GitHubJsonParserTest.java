package net.classiclauncher.launcher.update;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class GitHubJsonParserTest {

	// ── Single release ─────────────────────────────────────────────────────

	@Test
	void parse_singleRelease_returnsOneEntry() {
		String json = "[{\"tag_name\":\"v1.0.1\",\"name\":\"1.0.1\",\"body\":\"Fix bug\","
				+ "\"draft\":false,\"prerelease\":false,\"assets\":[]}]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertEquals(1, releases.size());
		assertEquals("v1.0.1", releases.get(0).getTagName());
		assertEquals("1.0.1", releases.get(0).getName());
		assertEquals("Fix bug", releases.get(0).getBody());
	}

	@Test
	void parse_singleRelease_withAssets_parsesAssets() {
		String json = "[{\"tag_name\":\"v1.0.1\",\"name\":\"Release\",\"body\":\"\","
				+ "\"draft\":false,\"prerelease\":false," + "\"assets\":[{" + "\"name\":\"ClassicLauncher-1.0.1.jar\","
				+ "\"browser_download_url\":\"https://github.com/releases/download/v1.0.1/ClassicLauncher-1.0.1.jar\","
				+ "\"size\":1234567}]}]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertEquals(1, releases.size());
		List<AssetInfo> assets = releases.get(0).getAssets();
		assertEquals(1, assets.size());
		assertEquals("ClassicLauncher-1.0.1.jar", assets.get(0).getName());
		assertEquals("https://github.com/releases/download/v1.0.1/ClassicLauncher-1.0.1.jar",
				assets.get(0).getDownloadUrl());
		assertEquals(1234567L, assets.get(0).getSizeBytes());
	}

	// ── Multiple releases ──────────────────────────────────────────────────

	@Test
	void parse_multipleReleases_returnsAll() {
		String json = "["
				+ "{\"tag_name\":\"v1.0.3\",\"name\":\"1.0.3\",\"body\":\"C\",\"draft\":false,\"prerelease\":false,\"assets\":[]},"
				+ "{\"tag_name\":\"v1.0.2\",\"name\":\"1.0.2\",\"body\":\"B\",\"draft\":false,\"prerelease\":false,\"assets\":[]},"
				+ "{\"tag_name\":\"v1.0.1\",\"name\":\"1.0.1\",\"body\":\"A\",\"draft\":false,\"prerelease\":false,\"assets\":[]}"
				+ "]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertEquals(3, releases.size());
		assertEquals("v1.0.3", releases.get(0).getTagName());
		assertEquals("v1.0.2", releases.get(1).getTagName());
		assertEquals("v1.0.1", releases.get(2).getTagName());
	}

	// ── Draft filtering ────────────────────────────────────────────────────

	@Test
	void parse_draft_isExcluded() {
		String json = "["
				+ "{\"tag_name\":\"v2.0.0\",\"name\":\"Draft\",\"body\":\"\",\"draft\":true,\"prerelease\":false,\"assets\":[]},"
				+ "{\"tag_name\":\"v1.0.1\",\"name\":\"Stable\",\"body\":\"\",\"draft\":false,\"prerelease\":false,\"assets\":[]}"
				+ "]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertEquals(1, releases.size());
		assertEquals("v1.0.1", releases.get(0).getTagName());
	}

	// ── Prerelease filtering ───────────────────────────────────────────────

	@Test
	void parse_prerelease_isExcluded() {
		String json = "["
				+ "{\"tag_name\":\"v2.0.0-beta\",\"name\":\"Beta\",\"body\":\"\",\"draft\":false,\"prerelease\":true,\"assets\":[]},"
				+ "{\"tag_name\":\"v1.0.1\",\"name\":\"Stable\",\"body\":\"\",\"draft\":false,\"prerelease\":false,\"assets\":[]}"
				+ "]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertEquals(1, releases.size());
		assertEquals("v1.0.1", releases.get(0).getTagName());
	}

	// ── Escape sequences in body ───────────────────────────────────────────

	@Test
	void parse_body_unescapesNewline() {
		String json = "[{\"tag_name\":\"v1.0.1\",\"name\":\"R\",\"body\":\"Line 1\\nLine 2\","
				+ "\"draft\":false,\"prerelease\":false,\"assets\":[]}]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertEquals("Line 1\nLine 2", releases.get(0).getBody());
	}

	@Test
	void parse_body_unescapesQuote() {
		String json = "[{\"tag_name\":\"v1.0.1\",\"name\":\"R\","
				+ "\"body\":\"Say \\\"hello\\\"\",\"draft\":false,\"prerelease\":false,\"assets\":[]}]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertEquals("Say \"hello\"", releases.get(0).getBody());
	}

	@Test
	void parse_body_unescapesBackslash() {
		String json = "[{\"tag_name\":\"v1.0.1\",\"name\":\"R\","
				+ "\"body\":\"path\\\\to\\\\file\",\"draft\":false,\"prerelease\":false,\"assets\":[]}]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertEquals("path\\to\\file", releases.get(0).getBody());
	}

	@Test
	void parse_body_nullValue_treatedAsEmpty() {
		String json = "[{\"tag_name\":\"v1.0.1\",\"name\":\"R\","
				+ "\"body\":null,\"draft\":false,\"prerelease\":false,\"assets\":[]}]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertEquals(1, releases.size());
		assertEquals("", releases.get(0).getBody());
	}

	// ── Empty assets ───────────────────────────────────────────────────────

	@Test
	void parse_emptyAssetsArray_returnsEmptyList() {
		String json = "[{\"tag_name\":\"v1.0.1\",\"name\":\"R\",\"body\":\"\","
				+ "\"draft\":false,\"prerelease\":false,\"assets\":[]}]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertTrue(releases.get(0).getAssets().isEmpty());
	}

	@Test
	void parse_multipleAssets_allParsed() {
		String json = "[{\"tag_name\":\"v1.0.1\",\"name\":\"R\",\"body\":\"\","
				+ "\"draft\":false,\"prerelease\":false," + "\"assets\":["
				+ "{\"name\":\"app.jar\",\"browser_download_url\":\"https://example.com/app.jar\",\"size\":100},"
				+ "{\"name\":\"app.dmg\",\"browser_download_url\":\"https://example.com/app.dmg\",\"size\":200},"
				+ "{\"name\":\"app.msi\",\"browser_download_url\":\"https://example.com/app.msi\",\"size\":300}"
				+ "]}]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertEquals(3, releases.get(0).getAssets().size());
		assertEquals("app.jar", releases.get(0).getAssets().get(0).getName());
		assertEquals("app.dmg", releases.get(0).getAssets().get(1).getName());
		assertEquals("app.msi", releases.get(0).getAssets().get(2).getName());
	}

	// ── Empty / null input ─────────────────────────────────────────────────

	@Test
	void parse_emptyArray_returnsEmptyList() {
		List<ReleaseInfo> releases = GitHubJsonParser.parse("[]");

		assertTrue(releases.isEmpty());
	}

	@Test
	void parse_null_returnsEmptyList() {
		List<ReleaseInfo> releases = GitHubJsonParser.parse(null);

		assertTrue(releases.isEmpty());
	}

	@Test
	void parse_emptyString_returnsEmptyList() {
		List<ReleaseInfo> releases = GitHubJsonParser.parse("   ");

		assertTrue(releases.isEmpty());
	}

	@Test
	void parse_notAnArray_returnsEmptyList() {
		List<ReleaseInfo> releases = GitHubJsonParser.parse("{\"not\":\"an array\"}");

		assertTrue(releases.isEmpty());
	}

	// ── All drafts / prereleases → empty ──────────────────────────────────

	@Test
	void parse_allDraftOrPrerelease_returnsEmpty() {
		String json = "["
				+ "{\"tag_name\":\"v2.0.0\",\"name\":\"D\",\"body\":\"\",\"draft\":true,\"prerelease\":false,\"assets\":[]},"
				+ "{\"tag_name\":\"v1.9.0-rc\",\"name\":\"P\",\"body\":\"\",\"draft\":false,\"prerelease\":true,\"assets\":[]}"
				+ "]";

		List<ReleaseInfo> releases = GitHubJsonParser.parse(json);

		assertTrue(releases.isEmpty());
	}

}
