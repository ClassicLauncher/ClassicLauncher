package net.classiclauncher.launcher.extension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

import com.sun.net.httpserver.HttpServer;

import dev.utano.librarymanager.CustomURLClassLoader;
import dev.utano.librarymanager.Dependency;
import dev.utano.librarymanager.LibraryManager;
import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.LauncherContext;

class ExtensionsTest {

	@TempDir
	File tempDir;

	private Extensions extensions;
	private final List<HttpServer> servers = new ArrayList<>();

	@BeforeEach
	void setUp() throws Exception {
		injectLauncherContext(tempDir);
		extensions = new Extensions();
	}

	@AfterEach
	void tearDown() {
		for (HttpServer server : servers) {
			server.stop(0);
		}
		servers.clear();
	}

	// ── load() ────────────────────────────────────────────────────────────

	@Test
	void load_emptyDirectory_noRecords() {
		File dir = LauncherContext.getInstance().resolve("extensions");
		dir.mkdirs();

		extensions.load();

		assertTrue(extensions.getAll().isEmpty());
	}

	@Test
	void load_nonExistentDirectory_noRecords() {
		// extensions dir not created
		extensions.load();

		assertTrue(extensions.getAll().isEmpty());
	}

	@Test
	void load_withMultipleYamlFiles_allRecordsLoaded() {
		ExtensionRecord r1 = buildRecord("id-1", "com.example", "ext-a", "1.0.0", true);
		ExtensionRecord r2 = buildRecord("id-2", "com.example", "ext-b", "2.0.0", true);
		writeRecordYaml(r1);
		writeRecordYaml(r2);

		extensions.load();

		assertEquals(2, extensions.getAll().size());
	}

	@Test
	void load_yamlWithCustomFields_deserializedCorrectly() {
		ExtensionRecord r = buildRecord("custom-id", "com.custom", "custom-ext", "3.0.0", false,
				"https://example.com/manifest.yml", Collections.singletonList("https://example.com/req.yml"));
		writeRecordYaml(r);

		extensions.load();

		assertEquals(1, extensions.getAll().size());
		ExtensionRecord loaded = extensions.getAll().get(0);
		assertEquals("custom-id", loaded.getId());
		assertFalse(loaded.isEnabled());
		assertEquals(1, loaded.getRequiredExtensionUrls().size());
	}

	@Test
	void load_ignoresNonYamlFiles() {
		ExtensionRecord r = buildRecord("id-1", "com.example", "ext-a", "1.0.0", true);
		writeRecordYaml(r);

		File txtFile = LauncherContext.getInstance().resolve("extensions", "ignored.txt");
		try {
			Files.write(txtFile.toPath(), "not a yml".getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		extensions.load();

		assertEquals(1, extensions.getAll().size());
	}

	@Test
	void load_clearsExistingRecordsOnReload() {
		ExtensionRecord r1 = buildRecord("id-1", "com.example", "ext-a", "1.0.0", true);
		writeRecordYaml(r1);
		extensions.load();
		assertEquals(1, extensions.getAll().size());

		// Remove file and reload
		LauncherContext.getInstance().resolve("extensions", "id-1.yml").delete();
		extensions.load();

		assertTrue(extensions.getAll().isEmpty());
	}

	// ── getAll / getById / getLoadIssues ──────────────────────────────────

	@Test
	void getAll_returnsUnmodifiableList() {
		assertThrows(UnsupportedOperationException.class, () -> extensions.getAll().add(null));
	}

	@Test
	void getById_existingId_returnsRecord() {
		ExtensionRecord r = buildRecord("find-me", "com.example", "ext", "1.0.0", true);
		writeRecordYaml(r);
		extensions.load();

		Optional<ExtensionRecord> result = extensions.getById("find-me");

		assertTrue(result.isPresent());
		assertEquals("find-me", result.get().getId());
	}

	@Test
	void getById_unknownId_returnsEmpty() {
		Optional<ExtensionRecord> result = extensions.getById("nonexistent-id");

		assertFalse(result.isPresent());
	}

	@Test
	void getLoadIssues_initiallyEmpty() {
		assertTrue(extensions.getLoadIssues().isEmpty());
	}

	// ── setEnabled ────────────────────────────────────────────────────────

	@Test
	void setEnabled_enabledToFalse_persistedToDisk() {
		ExtensionRecord r = buildRecord("toggle-id", "com.example", "ext", "1.0.0", true);
		writeRecordYaml(r);
		extensions.load();

		extensions.setEnabled("toggle-id", false);

		// Reload in new instance to verify persistence
		Extensions fresh = new Extensions();
		fresh.load();
		Optional<ExtensionRecord> reloaded = fresh.getById("toggle-id");
		assertTrue(reloaded.isPresent());
		assertFalse(reloaded.get().isEnabled());
	}

	@Test
	void setEnabled_unknownId_noOp() {
		// Should not throw
		assertDoesNotThrow(() -> extensions.setEnabled("nonexistent", false));
	}

	// ── installLocal ──────────────────────────────────────────────────────

	@Test
	void installLocal_withEmptyDeps_addsRecordAndFile() throws IOException {
		File manifestFile = writeLocalManifestFile(
				localManifestYaml("Local Ext", "com.local", "local-ext", "1.0.0", "0.0.0"));
		ExtensionManifest manifest = ExtensionManifest.fromFile(manifestFile);
		File jarFile = new File(tempDir, "local-ext.jar");
		Files.write(jarFile.toPath(), minimalJar());

		extensions.installLocal(manifest, jarFile, null);

		assertEquals(1, extensions.getAll().size());
		// Record file must exist on disk
		String id = extensions.getAll().get(0).getId();
		File recordFile = LauncherContext.getInstance().resolve("extensions", id + ".yml");
		assertTrue(recordFile.exists());
		// JAR copied to LibraryManager path
		String baseDir = LauncherContext.getInstance().getDataDir().getAbsolutePath();
		Dependency dep = new Dependency("com.local", "local-ext", "1.0.0", null);
		File expectedJar = new File(dep.getLocalPath(baseDir));
		assertTrue(expectedJar.exists());
	}

	@Test
	void installLocal_versionCheckFails_throwsIOException() throws IOException {
		File manifestFile = writeLocalManifestFile(
				localManifestYaml("Version Ext", "com.ver", "ver-ext", "1.0.0", "99.0.0"));
		ExtensionManifest manifest = ExtensionManifest.fromFile(manifestFile);
		File jarFile = new File(tempDir, "ver-ext.jar");
		Files.write(jarFile.toPath(), minimalJar());

		assertThrows(IOException.class, () -> extensions.installLocal(manifest, jarFile, null));
	}

	@Test
	void installLocal_jarNotFound_throwsIOException() throws IOException {
		File manifestFile = writeLocalManifestFile(
				localManifestYaml("Missing Ext", "com.missing", "missing-ext", "1.0.0", "0.0.0"));
		ExtensionManifest manifest = ExtensionManifest.fromFile(manifestFile);
		File missingJar = new File(tempDir, "does-not-exist.jar");

		assertThrows(IOException.class, () -> extensions.installLocal(manifest, missingJar, null));
	}

	@Test
	void installLocal_duplicateCoordinates_skipsIdempotently() throws IOException {
		File manifestFile = writeLocalManifestFile(
				localManifestYaml("Dup Ext", "com.dup", "dup-ext", "1.0.0", "0.0.0"));
		ExtensionManifest manifest = ExtensionManifest.fromFile(manifestFile);
		File jarFile = new File(tempDir, "dup-ext.jar");
		Files.write(jarFile.toPath(), minimalJar());

		extensions.installLocal(manifest, jarFile, null);
		extensions.installLocal(manifest, jarFile, null);

		assertEquals(1, extensions.getAll().size());
	}

	@Test
	void installLocal_extractsIconFromJar() throws IOException {
		File manifestFile = writeLocalManifestFile(
				localManifestYaml("Icon Ext", "com.icon", "icon-ext", "1.0.0", "0.0.0"));
		ExtensionManifest manifest = ExtensionManifest.fromFile(manifestFile);
		File jarFile = new File(tempDir, "icon-ext.jar");
		Files.write(jarFile.toPath(), jarWithIcon("icon.svg", "<svg/>".getBytes(StandardCharsets.UTF_8)));

		extensions.installLocal(manifest, jarFile, null);

		String id = extensions.getAll().get(0).getId();
		File iconFile = extensions.getIconCacheFile(id);
		assertNotNull(iconFile);
		assertTrue(iconFile.getName().endsWith(".svg"));
	}

	@Test
	void installLocal_extractsPngIconFromJar_whenSvgAbsent() throws IOException {
		File manifestFile = writeLocalManifestFile(
				localManifestYaml("PngIcon Ext", "com.pngicon", "pngicon-ext", "1.0.0", "0.0.0"));
		ExtensionManifest manifest = ExtensionManifest.fromFile(manifestFile);
		File jarFile = new File(tempDir, "pngicon-ext.jar");
		byte[] pngBytes = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
		Files.write(jarFile.toPath(), jarWithIcon("icon.png", pngBytes));

		extensions.installLocal(manifest, jarFile, null);

		String id = extensions.getAll().get(0).getId();
		File iconFile = extensions.getIconCacheFile(id);
		assertNotNull(iconFile);
		assertTrue(iconFile.getName().endsWith(".png"));
	}

	// ── installManifest ───────────────────────────────────────────────────

	@Test
	void installManifest_versionCheckFails_throwsIOException() throws IOException {
		HttpServer server = startManifestServer("/manifest.yml",
				manifestYaml("Ext", "com.test", "ext", "1.0.0", "99.0.0", new ArrayList<>()));
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";
		ExtensionManifest manifest = ExtensionManifest.fetch(url);

		try (MockedConstruction<LibraryManager> mc = Mockito.mockConstruction(LibraryManager.class)) {
			assertThrows(IOException.class, () -> extensions.installManifest(manifest));
		}
	}

	@Test
	void installManifest_duplicateCoordinates_skips() throws IOException {
		HttpServer server = startManifestServer("/manifest.yml",
				manifestYaml("Ext", "com.test", "dup-ext", "1.0.0", "0.0.0", new ArrayList<>()));
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";
		ExtensionManifest manifest = ExtensionManifest.fetch(url);

		try (MockedConstruction<LibraryManager> mc = Mockito.mockConstruction(LibraryManager.class)) {
			extensions.installManifest(manifest);
			extensions.installManifest(manifest);
		}

		assertEquals(1, extensions.getAll().size());
	}

	@Test
	void installManifest_downloadsAndSavesRecord() throws IOException {
		HttpServer server = startManifestServer("/manifest.yml",
				manifestYaml("Download Ext", "com.dl", "dl-ext", "1.0.0", "0.0.0", new ArrayList<>()));
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";
		ExtensionManifest manifest = ExtensionManifest.fetch(url);

		try (MockedConstruction<LibraryManager> mc = Mockito.mockConstruction(LibraryManager.class)) {
			extensions.installManifest(manifest, InstallProgressListener.NOOP);

			assertEquals(1, extensions.getAll().size());
			LibraryManager lm = mc.constructed().get(0);
			verify(lm).downloadLibraries();
		}

		// Record YAML must be on disk
		String id = extensions.getAll().get(0).getId();
		File recordFile = LauncherContext.getInstance().resolve("extensions", id + ".yml");
		assertTrue(recordFile.exists());
	}

	@Test
	void installManifest_nullListener_usesNoop() throws IOException {
		HttpServer server = startManifestServer("/manifest.yml",
				manifestYaml("NoopExt", "com.noop", "noop-ext", "1.0.0", "0.0.0", new ArrayList<>()));
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";
		ExtensionManifest manifest = ExtensionManifest.fetch(url);

		try (MockedConstruction<LibraryManager> mc = Mockito.mockConstruction(LibraryManager.class)) {
			// null listener must not cause NullPointerException
			assertDoesNotThrow(() -> extensions.installManifest(manifest, null));
		}
	}

	// ── resolveInstallPlan ────────────────────────────────────────────────

	@Test
	void resolveInstallPlan_nullUrl_throwsIOException() {
		assertThrows(IOException.class, () -> extensions.resolveInstallPlan(null));
	}

	@Test
	void resolveInstallPlan_emptyUrl_throwsIOException() {
		assertThrows(IOException.class, () -> extensions.resolveInstallPlan("   "));
	}

	@Test
	void resolveInstallPlan_singleExtensionNoDeps_returnsSingleEntry() throws IOException {
		HttpServer server = startManifestServer("/manifest.yml",
				manifestYaml("Root Ext", "com.root", "root-ext", "1.0.0", "0.0.0", new ArrayList<>()));
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";

		List<Extensions.ResolvedDependency> plan = extensions.resolveInstallPlan(url);

		assertEquals(1, plan.size());
		assertEquals("com.root:root-ext", plan.get(0).manifest.getCoordinateKey());
		assertNull(plan.get(0).requiredBy);
	}

	@Test
	void resolveInstallPlan_linearDependencyChain_correctOrder() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		int port = server.getAddress().getPort();
		String urlB = "http://localhost:" + port + "/manifest/b";
		String urlA = "http://localhost:" + port + "/manifest/a";

		byte[] yamlB = manifestYaml("ExtB", "com.test", "ext-b", "1.0.0", "0.0.0", new ArrayList<>())
				.getBytes(StandardCharsets.UTF_8);
		byte[] yamlA = manifestYaml("ExtA", "com.test", "ext-a", "1.0.0", "0.0.0", Collections.singletonList(urlB))
				.getBytes(StandardCharsets.UTF_8);

		server.createContext("/manifest/b", exchange -> {
			exchange.sendResponseHeaders(200, yamlB.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(yamlB);
			}
		});
		server.createContext("/manifest/a", exchange -> {
			exchange.sendResponseHeaders(200, yamlA.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(yamlA);
			}
		});
		server.start();
		servers.add(server);

		List<Extensions.ResolvedDependency> plan = extensions.resolveInstallPlan(urlA);

		assertEquals(2, plan.size());
		assertEquals("com.test:ext-b", plan.get(0).manifest.getCoordinateKey());
		assertEquals("com.test:ext-a", plan.get(1).manifest.getCoordinateKey());
		assertNull(plan.get(1).requiredBy);
		assertEquals("ExtA", plan.get(0).requiredBy);
	}

	@Test
	void resolveInstallPlan_diamondDependency_deduplicates() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		int port = server.getAddress().getPort();
		String urlD = "http://localhost:" + port + "/manifest/d";
		String urlB = "http://localhost:" + port + "/manifest/b";
		String urlC = "http://localhost:" + port + "/manifest/c";
		String urlA = "http://localhost:" + port + "/manifest/a";

		byte[] yamlD = manifestYaml("ExtD", "com.diamond", "ext-d", "1.0.0", "0.0.0", new ArrayList<>())
				.getBytes(StandardCharsets.UTF_8);
		byte[] yamlB = manifestYaml("ExtB", "com.diamond", "ext-b", "1.0.0", "0.0.0", Collections.singletonList(urlD))
				.getBytes(StandardCharsets.UTF_8);
		byte[] yamlC = manifestYaml("ExtC", "com.diamond", "ext-c", "1.0.0", "0.0.0", Collections.singletonList(urlD))
				.getBytes(StandardCharsets.UTF_8);
		byte[] yamlA = manifestYaml("ExtA", "com.diamond", "ext-a", "1.0.0", "0.0.0", Arrays.asList(urlB, urlC))
				.getBytes(StandardCharsets.UTF_8);

		server.createContext("/manifest/d", exchange -> {
			exchange.sendResponseHeaders(200, yamlD.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(yamlD);
			}
		});
		server.createContext("/manifest/b", exchange -> {
			exchange.sendResponseHeaders(200, yamlB.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(yamlB);
			}
		});
		server.createContext("/manifest/c", exchange -> {
			exchange.sendResponseHeaders(200, yamlC.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(yamlC);
			}
		});
		server.createContext("/manifest/a", exchange -> {
			exchange.sendResponseHeaders(200, yamlA.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(yamlA);
			}
		});
		server.start();
		servers.add(server);

		List<Extensions.ResolvedDependency> plan = extensions.resolveInstallPlan(urlA);

		// D appears exactly once
		long dCount = plan.stream().filter(e -> "com.diamond:ext-d".equals(e.manifest.getCoordinateKey())).count();
		assertEquals(1, dCount);
		// Total: D, B, C, A (4 unique extensions)
		assertEquals(4, plan.size());
	}

	@Test
	void resolveInstallPlan_circularDependency_throwsIOException() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		int port = server.getAddress().getPort();
		String urlA = "http://localhost:" + port + "/manifest/a";
		String urlB = "http://localhost:" + port + "/manifest/b";

		byte[] yamlA = manifestYaml("ExtA", "com.cycle", "ext-a", "1.0.0", "0.0.0", Collections.singletonList(urlB))
				.getBytes(StandardCharsets.UTF_8);
		byte[] yamlB = manifestYaml("ExtB", "com.cycle", "ext-b", "1.0.0", "0.0.0", Collections.singletonList(urlA))
				.getBytes(StandardCharsets.UTF_8);

		server.createContext("/manifest/a", exchange -> {
			exchange.sendResponseHeaders(200, yamlA.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(yamlA);
			}
		});
		server.createContext("/manifest/b", exchange -> {
			exchange.sendResponseHeaders(200, yamlB.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(yamlB);
			}
		});
		server.start();
		servers.add(server);

		assertThrows(IOException.class, () -> extensions.resolveInstallPlan(urlA));
	}

	@Test
	void resolveInstallPlan_alreadyInstalledExtension_excluded() throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		int port = server.getAddress().getPort();
		String urlB = "http://localhost:" + port + "/manifest/b";
		String urlA = "http://localhost:" + port + "/manifest/a";

		byte[] yamlB = manifestYaml("ExtB", "com.pre", "ext-b", "1.0.0", "0.0.0", new ArrayList<>())
				.getBytes(StandardCharsets.UTF_8);
		byte[] yamlA = manifestYaml("ExtA", "com.pre", "ext-a", "1.0.0", "0.0.0", Collections.singletonList(urlB))
				.getBytes(StandardCharsets.UTF_8);

		server.createContext("/manifest/b", exchange -> {
			exchange.sendResponseHeaders(200, yamlB.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(yamlB);
			}
		});
		server.createContext("/manifest/a", exchange -> {
			exchange.sendResponseHeaders(200, yamlA.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(yamlA);
			}
		});
		server.start();
		servers.add(server);

		// Pre-install B
		ExtensionRecord preB = buildRecord("pre-b-id", "com.pre", "ext-b", "1.0.0", true);
		writeRecordYaml(preB);
		extensions.load();

		List<Extensions.ResolvedDependency> plan = extensions.resolveInstallPlan(urlA);

		// Only A should be in the plan (B is already installed)
		assertEquals(1, plan.size());
		assertEquals("com.pre:ext-a", plan.get(0).manifest.getCoordinateKey());
	}

	// ── uninstall ─────────────────────────────────────────────────────────

	@Test
	void uninstall_unknownId_noOp() {
		// Should not throw
		assertDoesNotThrow(() -> extensions.uninstall("nonexistent-id"));
	}

	@Test
	void uninstall_removesRecordFromMemory() {
		ExtensionRecord r = buildRecord("remove-me", "com.example", "ext", "1.0.0", true);
		writeRecordYaml(r);
		extensions.load();
		assertEquals(1, extensions.getAll().size());

		extensions.uninstall("remove-me");

		assertTrue(extensions.getAll().isEmpty());
	}

	@Test
	void uninstall_deletesRecordYamlFile() {
		ExtensionRecord r = buildRecord("del-yaml", "com.example", "ext", "1.0.0", true);
		writeRecordYaml(r);
		extensions.load();

		File recordFile = LauncherContext.getInstance().resolve("extensions", "del-yaml.yml");
		assertTrue(recordFile.exists());

		extensions.uninstall("del-yaml");

		assertFalse(recordFile.exists());
	}

	@Test
	void uninstall_deletesJarFile() throws IOException {
		ExtensionRecord r = buildRecord("del-jar", "com.deljar", "del-jar", "1.0.0", true);
		writeRecordYaml(r);
		extensions.load();

		// Create a fake JAR at the expected path
		String baseDir = LauncherContext.getInstance().getDataDir().getAbsolutePath();
		Dependency dep = new Dependency("com.deljar", "del-jar", "1.0.0", null);
		File jarFile = new File(dep.getLocalPath(baseDir));
		jarFile.getParentFile().mkdirs();
		Files.write(jarFile.toPath(), minimalJar());
		assertTrue(jarFile.exists());

		extensions.uninstall("del-jar");

		assertFalse(jarFile.exists());
	}

	@Test
	void uninstall_deletesIconCache() throws IOException {
		ExtensionRecord r = buildRecord("del-icon", "com.example", "ext", "1.0.0", true);
		writeRecordYaml(r);
		extensions.load();

		// Create fake icon
		File iconsDir = LauncherContext.getInstance().resolve("extensions", "icons");
		iconsDir.mkdirs();
		File iconFile = new File(iconsDir, "del-icon.svg");
		Files.write(iconFile.toPath(), "<svg/>".getBytes(StandardCharsets.UTF_8));
		assertTrue(iconFile.exists());

		extensions.uninstall("del-icon");

		assertFalse(iconFile.exists());
	}

	@Test
	void uninstall_deletesManifestCache() throws IOException {
		String manifestUrl = "https://example.com/del-manifest.yml";
		ExtensionRecord r = buildRecord("del-manifest", "com.example", "ext", "1.0.0", true, manifestUrl,
				new ArrayList<>());
		writeRecordYaml(r);
		extensions.load();

		// Create fake manifest cache
		writeManifestCache(manifestUrl, "com.example", "ext");
		File cacheFile = LauncherContext.getInstance().resolve("extensions", "manifests",
				Math.abs(manifestUrl.hashCode()) + ".yml");
		assertTrue(cacheFile.exists());

		extensions.uninstall("del-manifest");

		assertFalse(cacheFile.exists());
	}

	// ── update ────────────────────────────────────────────────────────────

	@Test
	void update_unknownId_throwsIOException() {
		assertThrows(IOException.class, () -> extensions.update("nonexistent-id"));
	}

	@Test
	void update_newerVersionAvailable_updatesRecord() throws IOException {
		AtomicReference<String> servedYaml = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/manifest.yml", exchange -> {
			byte[] bytes = servedYaml.get().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		server.start();
		servers.add(server);
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";

		// Install v1.0.0
		servedYaml.set(manifestYaml("UpdateExt", "com.upd", "upd-ext", "1.0.0", "0.0.0", new ArrayList<>()));
		ExtensionManifest manifest = ExtensionManifest.fetch(url);
		try (MockedConstruction<LibraryManager> mc = Mockito.mockConstruction(LibraryManager.class)) {
			extensions.installManifest(manifest, InstallProgressListener.NOOP);
		}
		String id = extensions.getAll().get(0).getId();

		// Serve v1.1.0 and update
		servedYaml.set(manifestYaml("UpdateExt", "com.upd", "upd-ext", "1.1.0", "0.0.0", new ArrayList<>()));
		try (MockedConstruction<LibraryManager> mc = Mockito.mockConstruction(LibraryManager.class)) {
			extensions.update(id);
		}

		assertEquals("1.1.0", extensions.getById(id).get().getInstalledVersion());
	}

	@Test
	void update_sameVersion_stillUpdatesRecord() throws IOException {
		AtomicReference<String> servedYaml = new AtomicReference<>();
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/manifest.yml", exchange -> {
			byte[] bytes = servedYaml.get().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		server.start();
		servers.add(server);
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";

		servedYaml.set(manifestYaml("SameVer", "com.same", "same-ext", "1.0.0", "0.0.0", new ArrayList<>()));
		ExtensionManifest manifest = ExtensionManifest.fetch(url);
		try (MockedConstruction<LibraryManager> mc = Mockito.mockConstruction(LibraryManager.class)) {
			extensions.installManifest(manifest, InstallProgressListener.NOOP);
		}
		String id = extensions.getAll().get(0).getId();

		try (MockedConstruction<LibraryManager> mc = Mockito.mockConstruction(LibraryManager.class)) {
			assertDoesNotThrow(() -> extensions.update(id));
		}

		assertEquals("1.0.0", extensions.getById(id).get().getInstalledVersion());
	}

	// ── checkForUpdates ───────────────────────────────────────────────────

	@Test
	void checkForUpdates_noManifestUrl_skipped() {
		ExtensionRecord r = buildRecord("no-url-id", "com.example", "ext", "1.0.0", true);
		writeRecordYaml(r);
		extensions.load();

		List<ExtensionRecord> outdated = extensions.checkForUpdates();

		assertTrue(outdated.isEmpty());
	}

	@Test
	void checkForUpdates_fetchFails_silentlySkipped() {
		ExtensionRecord r = buildRecord("dead-url", "com.example", "ext", "1.0.0", true, "http://localhost:1/dead.yml",
				new ArrayList<>());
		writeRecordYaml(r);
		extensions.load();

		// Should not throw even though URL is unreachable
		List<ExtensionRecord> outdated = assertDoesNotThrow(() -> extensions.checkForUpdates());
		assertTrue(outdated.isEmpty());
	}

	@Test
	void checkForUpdates_sameVersion_notInResult() throws IOException {
		AtomicReference<String> servedYaml = new AtomicReference<>();
		HttpServer server = startDynamicServer("/manifest.yml", servedYaml);
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";

		servedYaml.set(manifestYaml("CheckExt", "com.chk", "chk-ext", "1.0.0", "0.0.0", new ArrayList<>()));
		ExtensionRecord r = buildRecord("chk-same", "com.chk", "chk-ext", "1.0.0", true, url, new ArrayList<>());
		writeRecordYaml(r);
		extensions.load();

		List<ExtensionRecord> outdated = extensions.checkForUpdates();

		assertTrue(outdated.isEmpty());
	}

	@Test
	void checkForUpdates_newerVersionAvailable_inResult() throws IOException {
		AtomicReference<String> servedYaml = new AtomicReference<>();
		HttpServer server = startDynamicServer("/manifest.yml", servedYaml);
		String url = "http://localhost:" + server.getAddress().getPort() + "/manifest.yml";

		// Record has v1.0.0 installed, manifest serves v1.1.0
		servedYaml.set(manifestYaml("NewerExt", "com.newer", "newer-ext", "1.1.0", "0.0.0", new ArrayList<>()));
		ExtensionRecord r = buildRecord("newer-id", "com.newer", "newer-ext", "1.0.0", true, url, new ArrayList<>());
		writeRecordYaml(r);
		extensions.load();

		List<ExtensionRecord> outdated = extensions.checkForUpdates();

		assertEquals(1, outdated.size());
		assertEquals("newer-id", outdated.get(0).getId());
	}

	// ── loadAll ───────────────────────────────────────────────────────────

	@Test
	void loadAll_noRecords_emptyIssues() {
		CustomURLClassLoader cl = new CustomURLClassLoader(new URL[0], getClass().getClassLoader());
		extensions.loadAll(cl, null);

		assertTrue(extensions.getLoadIssues().isEmpty());
	}

	@Test
	void loadAll_disabledRecords_skipped() {
		ExtensionRecord r = buildRecord("disabled-id", "com.dis", "dis-ext", "1.0.0", false);
		writeRecordYaml(r);
		extensions.load();

		CustomURLClassLoader cl = new CustomURLClassLoader(new URL[0], getClass().getClassLoader());
		extensions.loadAll(cl, null);

		assertTrue(extensions.getLoadIssues().isEmpty());
	}

	@Test
	void loadAll_missingJar_disablesExtensionAndRecordsIssue() {
		ExtensionRecord r = buildRecord("missing-jar-id", "com.missing", "missing-jar", "1.0.0", true);
		writeRecordYaml(r);
		extensions.load();

		CustomURLClassLoader cl = new CustomURLClassLoader(new URL[0], getClass().getClassLoader());
		extensions.loadAll(cl, null);

		assertFalse(extensions.getLoadIssues().isEmpty());
		assertFalse(extensions.getById("missing-jar-id").get().isEnabled());
	}

	@Test
	void loadAll_corruptJar_disablesAndRecordsIssue() throws IOException {
		ExtensionRecord r = buildRecord("corrupt-id", "com.corrupt", "corrupt-jar", "1.0.0", true);
		writeRecordYaml(r);
		extensions.load();

		// Write corrupt (non-ZIP) bytes to the JAR path
		String baseDir = LauncherContext.getInstance().getDataDir().getAbsolutePath();
		Dependency dep = new Dependency("com.corrupt", "corrupt-jar", "1.0.0", null);
		File jarFile = new File(dep.getLocalPath(baseDir));
		jarFile.getParentFile().mkdirs();
		Files.write(jarFile.toPath(), "not a zip file content".getBytes(StandardCharsets.UTF_8));

		CustomURLClassLoader cl = new CustomURLClassLoader(new URL[0], getClass().getClassLoader());
		extensions.loadAll(cl, null);

		assertFalse(extensions.getLoadIssues().isEmpty());
		assertFalse(extensions.getById("corrupt-id").get().isEnabled());
	}

	@Test
	void loadAll_cycleDetected_allCyclicExtensionsDisabled() {
		// Use the fallback URL-match approach: set each record's manifestUrl equal
		// to the URL in the other record's requiredExtensionUrls
		String urlA = "http://cycle-a.example.com/manifest.yml";
		String urlB = "http://cycle-b.example.com/manifest.yml";

		ExtensionRecord recA = buildRecord("cycle-a", "com.cycle", "ext-a", "1.0.0", true, urlA,
				Collections.singletonList(urlB));
		ExtensionRecord recB = buildRecord("cycle-b", "com.cycle", "ext-b", "1.0.0", true, urlB,
				Collections.singletonList(urlA));
		writeRecordYaml(recA);
		writeRecordYaml(recB);
		extensions.load();

		CustomURLClassLoader cl = new CustomURLClassLoader(new URL[0], getClass().getClassLoader());
		extensions.loadAll(cl, null);

		// Both should be disabled
		assertFalse(extensions.getById("cycle-a").get().isEnabled());
		assertFalse(extensions.getById("cycle-b").get().isEnabled());
		assertEquals(2, extensions.getLoadIssues().size());
	}

	@Test
	void loadAll_linearDependency_loadedInCorrectOrder() throws IOException {
		String urlB = "http://linear-b.example.com/manifest.yml";

		ExtensionRecord recB = buildRecord("lin-b", "com.linear", "ext-b", "1.0.0", true, urlB, new ArrayList<>());
		ExtensionRecord recA = buildRecord("lin-a", "com.linear", "ext-a", "1.0.0", true,
				"http://linear-a.example.com/manifest.yml", Collections.singletonList(urlB));
		writeRecordYaml(recB);
		writeRecordYaml(recA);

		// Write manifest caches for URL resolution
		writeManifestCache(urlB, "com.linear", "ext-b");

		// Write minimal valid JARs
		String baseDir = LauncherContext.getInstance().getDataDir().getAbsolutePath();
		Dependency depA = new Dependency("com.linear", "ext-a", "1.0.0", null);
		Dependency depB = new Dependency("com.linear", "ext-b", "1.0.0", null);
		File jarA = new File(depA.getLocalPath(baseDir));
		File jarB = new File(depB.getLocalPath(baseDir));
		jarA.getParentFile().mkdirs();
		jarB.getParentFile().mkdirs();
		Files.write(jarA.toPath(), minimalJar());
		Files.write(jarB.toPath(), minimalJar());

		extensions.load();
		CustomURLClassLoader cl = new CustomURLClassLoader(new URL[0], getClass().getClassLoader());
		extensions.loadAll(cl, null);

		assertTrue(extensions.getLoadIssues().isEmpty(),
				"Expected no load issues but got: " + extensions.getLoadIssues());
	}

	@Test
	void loadAll_successfulLoad_clearsIssues() throws IOException {
		// First pass: missing JAR produces issues
		ExtensionRecord r = buildRecord("clear-issues-id", "com.clear", "clear-ext", "1.0.0", true);
		writeRecordYaml(r);
		extensions.load();
		CustomURLClassLoader cl = new CustomURLClassLoader(new URL[0], getClass().getClassLoader());
		extensions.loadAll(cl, null);
		assertFalse(extensions.getLoadIssues().isEmpty());

		// Second pass: no records, issues must be cleared
		Extensions fresh = new Extensions();
		fresh.loadAll(cl, null);
		assertTrue(fresh.getLoadIssues().isEmpty());
	}

	// ── getDirectDependents / getAllEnabledDependents / getAllDisabledRequirements

	@Test
	void getDirectDependents_noDependents_emptyList() {
		ExtensionRecord r = buildRecord("solo-id", "com.solo", "solo-ext", "1.0.0", true);
		writeRecordYaml(r);
		extensions.load();

		List<ExtensionRecord> dependents = extensions.getDirectDependents("solo-id");

		assertTrue(dependents.isEmpty());
	}

	@Test
	void getDirectDependents_hasDependents_returnsThem() throws IOException {
		String reqUrl = "http://base.example.com/manifest.yml";
		ExtensionRecord base = buildRecord("base-id", "com.base", "base-ext", "1.0.0", true, reqUrl, new ArrayList<>());
		ExtensionRecord dependent = buildRecord("dep-id", "com.dep", "dep-ext", "1.0.0", true, "",
				Collections.singletonList(reqUrl));
		writeRecordYaml(base);
		writeRecordYaml(dependent);
		writeManifestCache(reqUrl, "com.base", "base-ext");
		extensions.load();

		List<ExtensionRecord> dependents = extensions.getDirectDependents("base-id");

		assertEquals(1, dependents.size());
		assertEquals("dep-id", dependents.get(0).getId());
	}

	@Test
	void getAllEnabledDependents_transitiveDependents_returnsAll() throws IOException {
		String urlA = "http://chain-a.example.com/manifest.yml";
		String urlB = "http://chain-b.example.com/manifest.yml";

		ExtensionRecord a = buildRecord("chain-a", "com.chain", "ext-a", "1.0.0", true, urlA, new ArrayList<>());
		ExtensionRecord b = buildRecord("chain-b", "com.chain", "ext-b", "1.0.0", true, urlB,
				Collections.singletonList(urlA));
		ExtensionRecord c = buildRecord("chain-c", "com.chain", "ext-c", "1.0.0", true, "",
				Collections.singletonList(urlB));
		writeRecordYaml(a);
		writeRecordYaml(b);
		writeRecordYaml(c);
		writeManifestCache(urlA, "com.chain", "ext-a");
		writeManifestCache(urlB, "com.chain", "ext-b");
		extensions.load();

		List<ExtensionRecord> dependents = extensions.getAllEnabledDependents("chain-a");

		assertEquals(2, dependents.size());
	}

	@Test
	void getAllEnabledDependents_disabledDependents_notReturned() throws IOException {
		String reqUrl = "http://disabled-dep.example.com/manifest.yml";
		ExtensionRecord base = buildRecord("base-disabled", "com.bd", "base-ext", "1.0.0", true, reqUrl,
				new ArrayList<>());
		ExtensionRecord disabled = buildRecord("dep-disabled", "com.bd", "dep-ext", "1.0.0", false, "",
				Collections.singletonList(reqUrl));
		writeRecordYaml(base);
		writeRecordYaml(disabled);
		writeManifestCache(reqUrl, "com.bd", "base-ext");
		extensions.load();

		List<ExtensionRecord> dependents = extensions.getAllEnabledDependents("base-disabled");

		assertTrue(dependents.isEmpty());
	}

	@Test
	void getAllDisabledRequirements_disabledRequiredExtensions_returnsAll() throws IOException {
		String reqUrl = "http://disreq.example.com/manifest.yml";
		ExtensionRecord requiredDisabled = buildRecord("req-dis", "com.rd", "req-ext", "1.0.0", false, reqUrl,
				new ArrayList<>());
		ExtensionRecord consumer = buildRecord("consumer", "com.rd", "consumer-ext", "1.0.0", true, "",
				Collections.singletonList(reqUrl));
		writeRecordYaml(requiredDisabled);
		writeRecordYaml(consumer);
		writeManifestCache(reqUrl, "com.rd", "req-ext");
		extensions.load();

		List<ExtensionRecord> disabledReqs = extensions.getAllDisabledRequirements("consumer");

		assertEquals(1, disabledReqs.size());
		assertEquals("req-dis", disabledReqs.get(0).getId());
	}

	@Test
	void getAllDisabledRequirements_enabledRequirements_notReturned() throws IOException {
		String reqUrl = "http://enabled-req.example.com/manifest.yml";
		ExtensionRecord requiredEnabled = buildRecord("req-en", "com.re", "req-ext", "1.0.0", true, reqUrl,
				new ArrayList<>());
		ExtensionRecord consumer = buildRecord("consumer2", "com.re", "consumer-ext", "1.0.0", true, "",
				Collections.singletonList(reqUrl));
		writeRecordYaml(requiredEnabled);
		writeRecordYaml(consumer);
		writeManifestCache(reqUrl, "com.re", "req-ext");
		extensions.load();

		List<ExtensionRecord> disabledReqs = extensions.getAllDisabledRequirements("consumer2");

		assertTrue(disabledReqs.isEmpty());
	}

	// ── getIconCacheFile ──────────────────────────────────────────────────

	@Test
	void getIconCacheFile_noIconFile_returnsNull() {
		assertNull(extensions.getIconCacheFile("no-icon-id"));
	}

	@Test
	void getIconCacheFile_svgExists_returnsSvgFile() throws IOException {
		File iconsDir = LauncherContext.getInstance().resolve("extensions", "icons");
		iconsDir.mkdirs();
		File svg = new File(iconsDir, "svg-id.svg");
		Files.write(svg.toPath(), "<svg/>".getBytes(StandardCharsets.UTF_8));

		File result = extensions.getIconCacheFile("svg-id");

		assertNotNull(result);
		assertTrue(result.getName().endsWith(".svg"));
	}

	@Test
	void getIconCacheFile_pngExists_returnsPngFile() throws IOException {
		File iconsDir = LauncherContext.getInstance().resolve("extensions", "icons");
		iconsDir.mkdirs();
		File png = new File(iconsDir, "png-id.png");
		Files.write(png.toPath(), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

		File result = extensions.getIconCacheFile("png-id");

		assertNotNull(result);
		assertTrue(result.getName().endsWith(".png"));
	}

	@Test
	void getIconCacheFile_bothExist_prefersSvg() throws IOException {
		File iconsDir = LauncherContext.getInstance().resolve("extensions", "icons");
		iconsDir.mkdirs();
		Files.write(new File(iconsDir, "both-id.svg").toPath(), "<svg/>".getBytes(StandardCharsets.UTF_8));
		Files.write(new File(iconsDir, "both-id.png").toPath(), new byte[]{0x00});

		File result = extensions.getIconCacheFile("both-id");

		assertNotNull(result);
		assertTrue(result.getName().endsWith(".svg"));
	}

	// ── isNewerVersion (private — via reflection) ─────────────────────────

	@Test
	void isNewerVersion_availableIsNewer_returnsTrue() throws Exception {
		assertTrue(invokeIsNewerVersion("1.0.0", "1.0.1"));
	}

	@Test
	void isNewerVersion_sameVersion_returnsFalse() throws Exception {
		assertFalse(invokeIsNewerVersion("1.0.0", "1.0.0"));
	}

	@Test
	void isNewerVersion_availableIsOlder_returnsFalse() throws Exception {
		assertFalse(invokeIsNewerVersion("1.1.0", "1.0.0"));
	}

	@Test
	void isNewerVersion_currentNull_returnsTrue() throws Exception {
		assertTrue(invokeIsNewerVersion(null, "1.0.1"));
	}

	@Test
	void isNewerVersion_currentEmpty_returnsTrue() throws Exception {
		assertTrue(invokeIsNewerVersion("", "1.0.1"));
	}

	@Test
	void isNewerVersion_availableNull_returnsFalse() throws Exception {
		assertFalse(invokeIsNewerVersion("1.0.0", null));
	}

	@Test
	void isNewerVersion_availableEmpty_returnsFalse() throws Exception {
		assertFalse(invokeIsNewerVersion("1.0.0", ""));
	}

	@Test
	void isNewerVersion_snapshotSuffix_strippedBeforeComparison() throws Exception {
		assertTrue(invokeIsNewerVersion("1.0.0-SNAPSHOT", "1.0.1"));
	}

	@Test
	void isNewerVersion_majorVersionDifference() throws Exception {
		assertTrue(invokeIsNewerVersion("1.9.9", "2.0.0"));
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	private void writeRecordYaml(ExtensionRecord record) {
		File dir = LauncherContext.getInstance().resolve("extensions");
		dir.mkdirs();
		File file = new File(dir, record.getId() + ".yml");
		YmlConfig config = new YmlConfig(file);
		record.save(config);
	}

	private void writeManifestCache(String url, String groupId, String artifactId) throws IOException {
		File cacheDir = LauncherContext.getInstance().resolve("extensions", "manifests");
		cacheDir.mkdirs();
		String hash = String.valueOf(Math.abs(url.hashCode()));
		File cacheFile = new File(cacheDir, hash + ".yml");
		YmlConfig config = new YmlConfig(cacheFile);
		config.set("maven.groupId", groupId);
		config.set("maven.artifactId", artifactId);
		config.set("maven.version", "1.0.0");
		config.set("name", "Cached");
		config.save();
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

	private HttpServer startDynamicServer(String path, AtomicReference<String> content) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext(path, exchange -> {
			byte[] bytes = content.get().getBytes(StandardCharsets.UTF_8);
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

	private String localManifestYaml(String name, String groupId, String artifactId, String version,
			String minVersion) {
		return "name: " + name + "\n" + "minLauncherVersion: \"" + minVersion + "\"\n" + "maven:\n" + "  groupId: "
				+ groupId + "\n" + "  artifactId: " + artifactId + "\n" + "  version: " + version + "\n";
	}

	private File writeLocalManifestFile(String yaml) throws IOException {
		File file = new File(tempDir, "manifest-" + System.nanoTime() + ".yml");
		Files.write(file.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
		return file;
	}

	private byte[] minimalJar() throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(baos)) {
			zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
			zos.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
		}
		return baos.toByteArray();
	}

	private byte[] jarWithIcon(String iconEntryName, byte[] iconBytes) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (ZipOutputStream zos = new ZipOutputStream(baos)) {
			zos.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
			zos.write("Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8));
			zos.closeEntry();
			zos.putNextEntry(new ZipEntry(iconEntryName));
			zos.write(iconBytes);
			zos.closeEntry();
		}
		return baos.toByteArray();
	}

	private ExtensionRecord buildRecord(String id, String groupId, String artifactId, String version, boolean enabled) {
		return new ExtensionRecord(id, "", "Ext " + id, "", "", "0.0.0", groupId, artifactId, version,
				new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), enabled, true);
	}

	private ExtensionRecord buildRecord(String id, String groupId, String artifactId, String version, boolean enabled,
			String manifestUrl, List<String> requiredExtUrls) {
		return new ExtensionRecord(id, manifestUrl, "Ext " + id, "", "", "0.0.0", groupId, artifactId, version,
				new ArrayList<>(), new ArrayList<>(), requiredExtUrls, enabled, true);
	}

	private boolean invokeIsNewerVersion(String current, String available) throws Exception {
		Method m = Extensions.class.getDeclaredMethod("isNewerVersion", String.class, String.class);
		m.setAccessible(true);
		return (Boolean) m.invoke(null, current, available);
	}

	/**
	 * Injects a {@link LauncherContext} whose dataDir is exactly {@code dataDir}, bypassing the platform path
	 * resolution that reads {@code XDG_CONFIG_HOME} on Linux. Each test gets a unique {@code @TempDir}, so this
	 * guarantees complete isolation even on CI runners where {@code XDG_CONFIG_HOME} is set.
	 */
	private static void injectLauncherContext(File dataDir) throws Exception {
		Constructor<LauncherContext> ctor = LauncherContext.class.getDeclaredConstructor(String.class);
		ctor.setAccessible(true);
		LauncherContext ctx = ctor.newInstance("test-ext");
		Field dataDirField = LauncherContext.class.getDeclaredField("dataDir");
		dataDirField.setAccessible(true);
		dataDirField.set(ctx, dataDir);
		Field instanceField = LauncherContext.class.getDeclaredField("instance");
		instanceField.setAccessible(true);
		instanceField.set(null, ctx);
	}

}
