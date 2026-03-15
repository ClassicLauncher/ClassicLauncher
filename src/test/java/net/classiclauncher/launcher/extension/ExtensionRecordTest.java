package net.classiclauncher.launcher.extension;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.LauncherContext;

class ExtensionRecordTest {

	@TempDir
	File tempDir;

	@BeforeEach
	void setUp() {
		System.setProperty("user.home", tempDir.getAbsolutePath());
		LauncherContext.initialize("test-ext");
	}

	// ── fromConfig deserialization ─────────────────────────────────────────

	@Test
	void fromConfig_allFields_deserializedCorrectly() {
		File file = new File(tempDir, "ext.yml");
		YmlConfig config = new YmlConfig(file);
		config.set("manifestUrl", "https://example.com/manifest.yml");
		config.set("name", "My Extension");
		config.set("description", "A great extension");
		config.set("pageUrl", "https://example.com/page");
		config.set("minLauncherVersion", "1.0.0");
		config.set("groupId", "com.example");
		config.set("artifactId", "my-ext");
		config.set("installedVersion", "2.0.0");
		config.set("enabled", "true");
		config.set("autoUpdate", "false");
		config.set("repositories.0.url", "https://repo.example.com/");
		config.save();

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord record = ExtensionRecord.fromConfig("test-id", loaded);

		assertEquals("test-id", record.getId());
		assertEquals("https://example.com/manifest.yml", record.getManifestUrl());
		assertEquals("My Extension", record.getName());
		assertEquals("A great extension", record.getDescription());
		assertEquals("https://example.com/page", record.getPageUrl());
		assertEquals("1.0.0", record.getMinLauncherVersion());
		assertEquals("com.example", record.getGroupId());
		assertEquals("my-ext", record.getArtifactId());
		assertEquals("2.0.0", record.getInstalledVersion());
		assertTrue(record.isEnabled());
		assertFalse(record.isAutoUpdate());
		assertEquals(1, record.getRepositoryUrls().size());
		assertEquals("https://repo.example.com/", record.getRepositoryUrls().get(0));
	}

	@Test
	void fromConfig_defaultEnabled_trueWhenMissing() {
		File file = new File(tempDir, "ext.yml");
		YmlConfig config = new YmlConfig(file);
		config.set("groupId", "com.example");
		config.set("artifactId", "ext");
		config.set("installedVersion", "1.0.0");
		config.save();

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord record = ExtensionRecord.fromConfig("id", loaded);

		assertTrue(record.isEnabled());
	}

	@Test
	void fromConfig_defaultAutoUpdate_trueWhenMissing() {
		File file = new File(tempDir, "ext.yml");
		YmlConfig config = new YmlConfig(file);
		config.set("groupId", "com.example");
		config.set("artifactId", "ext");
		config.set("installedVersion", "1.0.0");
		config.save();

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord record = ExtensionRecord.fromConfig("id", loaded);

		assertTrue(record.isAutoUpdate());
	}

	@Test
	void fromConfig_enabledFalse_persisted() {
		File file = new File(tempDir, "ext.yml");
		YmlConfig config = new YmlConfig(file);
		config.set("groupId", "com.example");
		config.set("artifactId", "ext");
		config.set("installedVersion", "1.0.0");
		config.set("enabled", "false");
		config.save();

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord record = ExtensionRecord.fromConfig("id", loaded);

		assertFalse(record.isEnabled());
	}

	@Test
	void fromConfig_enabledCaseInsensitive() {
		File file = new File(tempDir, "ext.yml");
		YmlConfig config = new YmlConfig(file);
		config.set("groupId", "com.example");
		config.set("artifactId", "ext");
		config.set("installedVersion", "1.0.0");
		config.set("enabled", "TRUE");
		config.save();

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord record = ExtensionRecord.fromConfig("id", loaded);

		assertTrue(record.isEnabled());
	}

	@Test
	void fromConfig_multipleRepositoryUrls_allLoaded() {
		File file = new File(tempDir, "ext.yml");
		YmlConfig config = new YmlConfig(file);
		config.set("groupId", "com.example");
		config.set("artifactId", "ext");
		config.set("installedVersion", "1.0.0");
		config.set("repositories.0.url", "https://repo1.example.com/");
		config.set("repositories.1.url", "https://repo2.example.com/");
		config.save();

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord record = ExtensionRecord.fromConfig("id", loaded);

		assertEquals(2, record.getRepositoryUrls().size());
		assertEquals("https://repo1.example.com/", record.getRepositoryUrls().get(0));
		assertEquals("https://repo2.example.com/", record.getRepositoryUrls().get(1));
	}

	@Test
	void fromConfig_multipleDependencies_allLoaded() {
		File file = new File(tempDir, "ext.yml");
		YmlConfig config = new YmlConfig(file);
		config.set("groupId", "com.example");
		config.set("artifactId", "ext");
		config.set("installedVersion", "1.0.0");
		config.set("dependencies.0.groupId", "com.dep1");
		config.set("dependencies.0.artifactId", "dep1");
		config.set("dependencies.0.version", "1.0.0");
		config.set("dependencies.1.groupId", "com.dep2");
		config.set("dependencies.1.artifactId", "dep2");
		config.set("dependencies.1.version", "2.0.0");
		config.save();

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord record = ExtensionRecord.fromConfig("id", loaded);

		assertEquals(2, record.getDependencies().size());
		assertEquals("dep1", record.getDependencies().get(0).getArtifactId());
		assertEquals("dep2", record.getDependencies().get(1).getArtifactId());
	}

	@Test
	void fromConfig_multipleRequiredExtensionUrls_allLoaded() {
		File file = new File(tempDir, "ext.yml");
		YmlConfig config = new YmlConfig(file);
		config.set("groupId", "com.example");
		config.set("artifactId", "ext");
		config.set("installedVersion", "1.0.0");
		config.set("requiredExtensions.0.url", "https://example.com/ext-a/manifest.yml");
		config.set("requiredExtensions.1.url", "https://example.com/ext-b/manifest.yml");
		config.save();

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord record = ExtensionRecord.fromConfig("id", loaded);

		assertEquals(2, record.getRequiredExtensionUrls().size());
		assertEquals("https://example.com/ext-a/manifest.yml", record.getRequiredExtensionUrls().get(0));
		assertEquals("https://example.com/ext-b/manifest.yml", record.getRequiredExtensionUrls().get(1));
	}

	@Test
	void fromConfig_emptyManifestUrl_emptyStringNotNull() {
		File file = new File(tempDir, "ext.yml");
		YmlConfig config = new YmlConfig(file);
		config.set("groupId", "com.example");
		config.set("artifactId", "ext");
		config.set("installedVersion", "1.0.0");
		config.save();

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord record = ExtensionRecord.fromConfig("id", loaded);

		assertNotNull(record.getManifestUrl());
		assertEquals("", record.getManifestUrl());
	}

	// ── fromManifest factory ───────────────────────────────────────────────

	@Test
	void fromManifest_idIsSet() {
		ExtensionManifest manifest = buildManifest("com.example", "my-ext", "1.0.0", "https://example.com/m.yml",
				new ArrayList<>());
		ExtensionRecord record = ExtensionRecord.fromManifest("my-unique-id", manifest);

		assertEquals("my-unique-id", record.getId());
	}

	@Test
	void fromManifest_enabledTrueByDefault() {
		ExtensionManifest manifest = buildManifest("com.example", "my-ext", "1.0.0", "https://example.com/m.yml",
				new ArrayList<>());
		ExtensionRecord record = ExtensionRecord.fromManifest("id", manifest);

		assertTrue(record.isEnabled());
	}

	@Test
	void fromManifest_autoUpdateTrueByDefault() {
		ExtensionManifest manifest = buildManifest("com.example", "my-ext", "1.0.0", "https://example.com/m.yml",
				new ArrayList<>());
		ExtensionRecord record = ExtensionRecord.fromManifest("id", manifest);

		assertTrue(record.isAutoUpdate());
	}

	@Test
	void fromManifest_requiredExtensionUrlsExtractedFromSpecs() {
		List<String> reqUrls = Arrays.asList("https://example.com/req-a.yml", "https://example.com/req-b.yml");
		ExtensionManifest manifest = buildManifest("com.example", "my-ext", "1.0.0", "https://example.com/m.yml",
				reqUrls);
		ExtensionRecord record = ExtensionRecord.fromManifest("id", manifest);

		assertEquals(2, record.getRequiredExtensionUrls().size());
		assertEquals("https://example.com/req-a.yml", record.getRequiredExtensionUrls().get(0));
		assertEquals("https://example.com/req-b.yml", record.getRequiredExtensionUrls().get(1));
	}

	@Test
	void fromManifest_dependenciesCopied() {
		List<ExtensionManifest.DependencySpec> deps = new ArrayList<>();
		deps.add(new ExtensionManifest.DependencySpec("com.dep", "dep-lib", "1.0.0", new ArrayList<>()));
		ExtensionManifest manifest = buildManifestWithDeps("com.example", "my-ext", "1.0.0",
				"https://example.com/m.yml", deps);
		ExtensionRecord record = ExtensionRecord.fromManifest("id", manifest);

		assertEquals(1, record.getDependencies().size());
		assertEquals("dep-lib", record.getDependencies().get(0).getArtifactId());
	}

	// ── save + round-trip ──────────────────────────────────────────────────

	@Test
	void save_then_fromConfig_allFieldsPreserved() {
		List<String> repoUrls = Arrays.asList("https://repo.example.com/");
		List<ExtensionManifest.DependencySpec> deps = new ArrayList<>();
		deps.add(new ExtensionManifest.DependencySpec("com.dep", "dep-lib", "1.0.0", new ArrayList<>()));
		List<String> reqUrls = Arrays.asList("https://example.com/req.yml");

		ExtensionRecord original = new ExtensionRecord("saved-id", "https://example.com/manifest.yml", "Saved Ext",
				"A saved extension", "https://example.com/page", "1.0.0", "com.example", "saved-ext", "3.0.0", repoUrls,
				deps, reqUrls, true, false);

		File file = new File(tempDir, original.getId() + ".yml");
		YmlConfig config = new YmlConfig(file);
		original.save(config);

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord restored = ExtensionRecord.fromConfig(original.getId(), loaded);

		assertEquals(original.getId(), restored.getId());
		assertEquals(original.getManifestUrl(), restored.getManifestUrl());
		assertEquals(original.getName(), restored.getName());
		assertEquals(original.getDescription(), restored.getDescription());
		assertEquals(original.getPageUrl(), restored.getPageUrl());
		assertEquals(original.getMinLauncherVersion(), restored.getMinLauncherVersion());
		assertEquals(original.getGroupId(), restored.getGroupId());
		assertEquals(original.getArtifactId(), restored.getArtifactId());
		assertEquals(original.getInstalledVersion(), restored.getInstalledVersion());
		assertEquals(original.isEnabled(), restored.isEnabled());
		assertEquals(original.isAutoUpdate(), restored.isAutoUpdate());
		assertEquals(original.getRepositoryUrls(), restored.getRepositoryUrls());
		assertEquals(1, restored.getDependencies().size());
		assertEquals("dep-lib", restored.getDependencies().get(0).getArtifactId());
		assertEquals(original.getRequiredExtensionUrls(), restored.getRequiredExtensionUrls());
	}

	@Test
	void save_enabledFalse_persistedAndReloadable() {
		ExtensionRecord record = new ExtensionRecord("id", "", "Ext", "", "", "0.0.0", "com.example", "ext", "1.0.0",
				new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false, true);

		File file = new File(tempDir, "id.yml");
		YmlConfig config = new YmlConfig(file);
		record.save(config);

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord restored = ExtensionRecord.fromConfig("id", loaded);

		assertFalse(restored.isEnabled());
	}

	@Test
	void save_nullableFields_handledGracefully() {
		ExtensionRecord record = new ExtensionRecord("id", null, "Ext", null, null, null, "com.example", "ext", "1.0.0",
				null, null, null, true, true);

		File file = new File(tempDir, "id.yml");
		YmlConfig config = new YmlConfig(file);
		// Should not throw NullPointerException
		assertDoesNotThrow(() -> record.save(config));

		YmlConfig loaded = new YmlConfig(file);
		loaded.load();
		ExtensionRecord restored = ExtensionRecord.fromConfig("id", loaded);

		assertNotNull(restored.getManifestUrl());
		assertNotEquals("null", restored.getManifestUrl());
	}

	// ── Getters / mutators ─────────────────────────────────────────────────

	@Test
	void getCoordinateKey_returnsGroupIdColonArtifactId() {
		ExtensionRecord record = buildRecord("id", "com.example", "my-ext", "1.0.0", true);
		assertEquals("com.example:my-ext", record.getCoordinateKey());
	}

	@Test
	void hasPageUrl_emptyPageUrl_returnsFalse() {
		ExtensionRecord record = new ExtensionRecord("id", "", "Ext", "", "", "0.0.0", "com.example", "ext", "1.0.0",
				new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), true, true);
		assertFalse(record.hasPageUrl());
	}

	@Test
	void hasPageUrl_nonEmptyPageUrl_returnsTrue() {
		ExtensionRecord record = new ExtensionRecord("id", "", "Ext", "", "https://example.com/page", "0.0.0",
				"com.example", "ext", "1.0.0", new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), true, true);
		assertTrue(record.hasPageUrl());
	}

	@Test
	void hasPageUrl_nullPageUrl_returnsFalse() {
		ExtensionRecord record = new ExtensionRecord("id", "", "Ext", "", null, "0.0.0", "com.example", "ext", "1.0.0",
				new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), true, true);
		assertFalse(record.hasPageUrl());
	}

	@Test
	void setEnabled_changesState() {
		ExtensionRecord record = buildRecord("id", "com.example", "ext", "1.0.0", true);
		assertTrue(record.isEnabled());

		record.setEnabled(false);
		assertFalse(record.isEnabled());

		record.setEnabled(true);
		assertTrue(record.isEnabled());
	}

	@Test
	void setAutoUpdate_changesState() {
		ExtensionRecord record = buildRecord("id", "com.example", "ext", "1.0.0", true);
		assertTrue(record.isAutoUpdate());

		record.setAutoUpdate(false);
		assertFalse(record.isAutoUpdate());
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	private ExtensionRecord buildRecord(String id, String groupId, String artifactId, String version, boolean enabled) {
		return new ExtensionRecord(id, "", "Ext " + id, "", "", "0.0.0", groupId, artifactId, version,
				new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), enabled, true);
	}

	/**
	 * Builds a minimal ExtensionManifest by writing a YAML file and parsing it. The manifest URL is set to the given
	 * value via the fetch path in tests that need it, or via fromFile (empty manifestUrl) here.
	 */
	private ExtensionManifest buildManifest(String groupId, String artifactId, String version, String manifestUrl,
			List<String> requiredUrls) {
		// Build using direct constructor indirectly: write YAML, parse, then wrap
		// Since ExtensionManifest constructor is private we go through fromFile for local construction
		// and manually set URL via fromManifest indirection in ExtensionRecord
		StringBuilder sb = new StringBuilder();
		sb.append("maven:\n");
		sb.append("  groupId: ").append(groupId).append("\n");
		sb.append("  artifactId: ").append(artifactId).append("\n");
		sb.append("  version: ").append(version).append("\n");
		if (!requiredUrls.isEmpty()) {
			sb.append("requiredExtensions:\n");
			for (int i = 0; i < requiredUrls.size(); i++) {
				sb.append("  ").append(i).append(":\n");
				sb.append("    url: \"").append(requiredUrls.get(i)).append("\"\n");
			}
		}
		try {
			File file = new File(tempDir, "manifest-" + System.nanoTime() + ".yml");
			Files.write(file.toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return ExtensionManifest.fromFile(file);
		} catch (Exception e) {
			throw new RuntimeException("Failed to build test manifest", e);
		}
	}

	private ExtensionManifest buildManifestWithDeps(String groupId, String artifactId, String version,
			String manifestUrl, List<ExtensionManifest.DependencySpec> deps) {
		StringBuilder sb = new StringBuilder();
		sb.append("maven:\n");
		sb.append("  groupId: ").append(groupId).append("\n");
		sb.append("  artifactId: ").append(artifactId).append("\n");
		sb.append("  version: ").append(version).append("\n");
		if (!deps.isEmpty()) {
			sb.append("dependencies:\n");
			for (int i = 0; i < deps.size(); i++) {
				ExtensionManifest.DependencySpec dep = deps.get(i);
				sb.append("  ").append(i).append(":\n");
				sb.append("    groupId: ").append(dep.getGroupId()).append("\n");
				sb.append("    artifactId: ").append(dep.getArtifactId()).append("\n");
				sb.append("    version: ").append(dep.getVersion()).append("\n");
			}
		}
		try {
			File file = new File(tempDir, "manifest-" + System.nanoTime() + ".yml");
			Files.write(file.toPath(), sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			return ExtensionManifest.fromFile(file);
		} catch (Exception e) {
			throw new RuntimeException("Failed to build test manifest", e);
		}
	}

}
