package net.classiclauncher.launcher.extension;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import net.classiclauncher.launcher.LauncherContext;

class ExtensionManifestTest {

	@TempDir
	File tempDir;

	private final List<HttpServer> servers = new ArrayList<>();

	@BeforeEach
	void setUp() {
		System.setProperty("user.home", tempDir.getAbsolutePath());
		LauncherContext.initialize("test-ext");
	}

	@AfterEach
	void tearDown() {
		for (HttpServer server : servers) {
			server.stop(0);
		}
		servers.clear();
	}

	// ── fromFile happy path ────────────────────────────────────────────────

	@Test
	void fromFile_validYaml_parsesAllFields() throws IOException {
		String yaml = "name: My Extension\n" + "description: Adds custom game support\n"
				+ "minLauncherVersion: \"1.0.0\"\n" + "pageUrl: https://example.com/my-extension\n" + "maven:\n"
				+ "  groupId: com.example\n" + "  artifactId: my-ext\n" + "  version: 1.2.3\n" + "repositories:\n"
				+ "  0:\n" + "    url: https://repo.example.com/maven2/\n" + "dependencies:\n" + "  0:\n"
				+ "    groupId: com.dep\n" + "    artifactId: dep-lib\n" + "    version: 1.0.0\n"
				+ "    repositories:\n" + "      0:\n" + "        url: https://repo.dep.com/maven2/\n"
				+ "requiredExtensions:\n" + "  0:\n" + "    url: https://example.com/base-extension/manifest.yml\n";
		File file = writeManifestFile(yaml);

		ExtensionManifest m = ExtensionManifest.fromFile(file);

		assertEquals("My Extension", m.getName());
		assertEquals("Adds custom game support", m.getDescription());
		assertEquals("1.0.0", m.getMinLauncherVersion());
		assertEquals("https://example.com/my-extension", m.getPageUrl());
		assertEquals("com.example", m.getGroupId());
		assertEquals("my-ext", m.getArtifactId());
		assertEquals("1.2.3", m.getVersion());
		assertEquals(1, m.getRepositoryUrls().size());
		assertEquals("https://repo.example.com/maven2/", m.getRepositoryUrls().get(0));
		assertEquals(1, m.getDependencies().size());
		ExtensionManifest.DependencySpec dep = m.getDependencies().get(0);
		assertEquals("com.dep", dep.getGroupId());
		assertEquals("dep-lib", dep.getArtifactId());
		assertEquals("1.0.0", dep.getVersion());
		assertEquals(1, dep.getRepositoryUrls().size());
		assertEquals("https://repo.dep.com/maven2/", dep.getRepositoryUrls().get(0));
		assertEquals(1, m.getRequiredExtensions().size());
		assertEquals("https://example.com/base-extension/manifest.yml", m.getRequiredExtensions().get(0).getUrl());
	}

	@Test
	void fromFile_minimalYaml_onlyMavenCoordinates() throws IOException {
		String yaml = "maven:\n" + "  groupId: com.example\n" + "  artifactId: my-ext\n" + "  version: 1.0.0\n";
		File file = writeManifestFile(yaml);

		ExtensionManifest m = ExtensionManifest.fromFile(file);

		assertEquals("com.example", m.getGroupId());
		assertEquals("my-ext", m.getArtifactId());
		assertEquals("1.0.0", m.getVersion());
		assertEquals("0.0.0", m.getMinLauncherVersion());
		assertTrue(m.getRepositoryUrls().isEmpty());
		assertTrue(m.getDependencies().isEmpty());
		assertTrue(m.getRequiredExtensions().isEmpty());
	}

	@Test
	void fromFile_multipleDependencies_allParsed() throws IOException {
		String yaml = "maven:\n" + "  groupId: com.example\n" + "  artifactId: my-ext\n" + "  version: 1.0.0\n"
				+ "dependencies:\n" + "  0:\n" + "    groupId: com.dep1\n" + "    artifactId: dep1\n"
				+ "    version: 1.0.0\n" + "  1:\n" + "    groupId: com.dep2\n" + "    artifactId: dep2\n"
				+ "    version: 2.0.0\n";
		File file = writeManifestFile(yaml);

		ExtensionManifest m = ExtensionManifest.fromFile(file);

		assertEquals(2, m.getDependencies().size());
		assertEquals("dep1", m.getDependencies().get(0).getArtifactId());
		assertEquals("dep2", m.getDependencies().get(1).getArtifactId());
	}

	@Test
	void fromFile_multipleRequiredExtensions_allParsed() throws IOException {
		String yaml = "maven:\n" + "  groupId: com.example\n" + "  artifactId: my-ext\n" + "  version: 1.0.0\n"
				+ "requiredExtensions:\n" + "  0:\n" + "    url: https://example.com/ext-a/manifest.yml\n" + "  1:\n"
				+ "    url: https://example.com/ext-b/manifest.yml\n";
		File file = writeManifestFile(yaml);

		ExtensionManifest m = ExtensionManifest.fromFile(file);

		assertEquals(2, m.getRequiredExtensions().size());
		assertEquals("https://example.com/ext-a/manifest.yml", m.getRequiredExtensions().get(0).getUrl());
		assertEquals("https://example.com/ext-b/manifest.yml", m.getRequiredExtensions().get(1).getUrl());
	}

	@Test
	void fromFile_manifestUrlIsEmpty() throws IOException {
		File file = writeManifestFile(minimalManifestYaml("com.example", "my-ext", "1.0.0"));

		ExtensionManifest m = ExtensionManifest.fromFile(file);

		assertNotNull(m.getManifestUrl());
		assertEquals("", m.getManifestUrl());
	}

	@Test
	void fromFile_getCoordinateKey_returnsGroupIdColonArtifactId() throws IOException {
		File file = writeManifestFile(minimalManifestYaml("com.example", "my-ext", "1.0.0"));

		ExtensionManifest m = ExtensionManifest.fromFile(file);

		assertEquals("com.example:my-ext", m.getCoordinateKey());
	}

	// ── fromFile errors ────────────────────────────────────────────────────

	@Test
	void fromFile_null_throwsIOException() {
		assertThrows(IOException.class, () -> ExtensionManifest.fromFile(null));
	}

	@Test
	void fromFile_nonExistentFile_throwsIOException() {
		File absent = new File(tempDir, "nonexistent.yml");
		assertThrows(IOException.class, () -> ExtensionManifest.fromFile(absent));
	}

	@Test
	void fromFile_missingGroupId_throwsIOException() throws IOException {
		String yaml = "maven:\n" + "  artifactId: my-ext\n" + "  version: 1.0.0\n";
		File file = writeManifestFile(yaml);
		assertThrows(IOException.class, () -> ExtensionManifest.fromFile(file));
	}

	@Test
	void fromFile_missingArtifactId_throwsIOException() throws IOException {
		String yaml = "maven:\n" + "  groupId: com.example\n" + "  version: 1.0.0\n";
		File file = writeManifestFile(yaml);
		assertThrows(IOException.class, () -> ExtensionManifest.fromFile(file));
	}

	@Test
	void fromFile_missingVersion_throwsIOException() throws IOException {
		String yaml = "maven:\n" + "  groupId: com.example\n" + "  artifactId: my-ext\n";
		File file = writeManifestFile(yaml);
		assertThrows(IOException.class, () -> ExtensionManifest.fromFile(file));
	}

	// ── fetch happy path ───────────────────────────────────────────────────

	@Test
	void fetch_validYaml_parsesAllFields() throws IOException {
		String yaml = manifestYaml("Test Extension", "com.test", "test-ext", "2.0.0", "0.0.0", new ArrayList<>());
		HttpServer server = startManifestServer("/manifest.yml", yaml);
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";

		ExtensionManifest m = ExtensionManifest.fetch(url);

		assertEquals("Test Extension", m.getName());
		assertEquals("com.test", m.getGroupId());
		assertEquals("test-ext", m.getArtifactId());
		assertEquals("2.0.0", m.getVersion());
		assertEquals("0.0.0", m.getMinLauncherVersion());
	}

	@Test
	void fetch_setsManifestUrl() throws IOException {
		String yaml = manifestYaml("Ext", "com.test", "ext", "1.0.0", "0.0.0", new ArrayList<>());
		HttpServer server = startManifestServer("/manifest.yml", yaml);
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";

		ExtensionManifest m = ExtensionManifest.fetch(url);

		assertEquals(url, m.getManifestUrl());
	}

	@Test
	void fetch_writesCacheFile() throws IOException {
		String yaml = manifestYaml("Ext", "com.test", "ext", "1.0.0", "0.0.0", new ArrayList<>());
		HttpServer server = startManifestServer("/manifest.yml", yaml);
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";

		ExtensionManifest.fetch(url);

		String hash = String.valueOf(Math.abs(url.hashCode()));
		File cacheFile = LauncherContext.getInstance().resolve("extensions", "manifests", hash + ".yml");
		assertTrue(cacheFile.exists(), "Cache file should be created at " + cacheFile.getAbsolutePath());
	}

	// ── fetch errors ───────────────────────────────────────────────────────

	@Test
	void fetch_null_throwsIOException() {
		assertThrows(IOException.class, () -> ExtensionManifest.fetch(null));
	}

	@Test
	void fetch_empty_throwsIOException() {
		assertThrows(IOException.class, () -> ExtensionManifest.fetch("   "));
	}

	@Test
	void fetch_networkError_throwsIOException() {
		// Use a port that is not listening
		assertThrows(IOException.class, () -> ExtensionManifest.fetch("http://localhost:1/manifest.yml"));
	}

	@Test
	void fetch_missingCoordinates_throwsIOException() throws IOException {
		String yaml = "name: No Coordinates\ndescription: Missing maven block\n";
		HttpServer server = startManifestServer("/manifest.yml", yaml);
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";

		assertThrows(IOException.class, () -> ExtensionManifest.fetch(url));
	}

	// ── Inner classes ──────────────────────────────────────────────────────

	@Test
	void dependencySpec_getterValues_correct() {
		List<String> repos = new ArrayList<>();
		repos.add("https://repo.example.com/");
		ExtensionManifest.DependencySpec dep = new ExtensionManifest.DependencySpec("com.test", "lib", "1.0.0", repos);

		assertEquals("com.test", dep.getGroupId());
		assertEquals("lib", dep.getArtifactId());
		assertEquals("1.0.0", dep.getVersion());
		assertEquals(1, dep.getRepositoryUrls().size());
		assertEquals("https://repo.example.com/", dep.getRepositoryUrls().get(0));
	}

	@Test
	void dependencySpec_nullRepositories_emptyList() {
		ExtensionManifest.DependencySpec dep = new ExtensionManifest.DependencySpec("com.test", "lib", "1.0.0", null);

		assertNotNull(dep.getRepositoryUrls());
		assertTrue(dep.getRepositoryUrls().isEmpty());
	}

	@Test
	void requiredExtensionSpec_nullUrl_throwsIllegalArgumentException() {
		assertThrows(IllegalArgumentException.class, () -> new ExtensionManifest.RequiredExtensionSpec(null));
	}

	@Test
	void requiredExtensionSpec_emptyUrl_throwsIllegalArgumentException() {
		assertThrows(IllegalArgumentException.class, () -> new ExtensionManifest.RequiredExtensionSpec("   "));
	}

	@Test
	void requiredExtensionSpec_trimsWhitespace() {
		ExtensionManifest.RequiredExtensionSpec spec = new ExtensionManifest.RequiredExtensionSpec(
				"  https://example.com/  ");

		assertEquals("https://example.com/", spec.getUrl());
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	private File writeManifestFile(String yaml) throws IOException {
		File file = new File(tempDir, "manifest-" + System.nanoTime() + ".yml");
		Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	private HttpServer startManifestServer(String path, String yamlContent) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		byte[] bytes = yamlContent.getBytes(StandardCharsets.UTF_8);
		server.createContext(path, exchange -> {
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		server.start();
		servers.add(server);
		return server;
	}

	private String manifestYaml(String name, String groupId, String artifactId, String version, String minVersion,
			List<String> requiredUrls) {
		StringBuilder sb = new StringBuilder();
		sb.append("name: ").append(name).append("\n");
		sb.append("description: Test extension\n");
		sb.append("minLauncherVersion: \"").append(minVersion).append("\"\n");
		sb.append("maven:\n");
		sb.append("  groupId: ").append(groupId).append("\n");
		sb.append("  artifactId: ").append(artifactId).append("\n");
		sb.append("  version: ").append(version).append("\n");
		if (requiredUrls != null && !requiredUrls.isEmpty()) {
			sb.append("requiredExtensions:\n");
			for (int i = 0; i < requiredUrls.size(); i++) {
				sb.append("  ").append(i).append(":\n");
				sb.append("    url: \"").append(requiredUrls.get(i)).append("\"\n");
			}
		}
		return sb.toString();
	}

	private String minimalManifestYaml(String groupId, String artifactId, String version) {
		return "maven:\n" + "  groupId: " + groupId + "\n" + "  artifactId: " + artifactId + "\n" + "  version: "
				+ version + "\n";
	}

}
