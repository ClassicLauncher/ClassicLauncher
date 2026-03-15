package net.classiclauncher.launcher.settings;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.update.ReleaseSource;

class ReleaseSourceRegistryTest {

	@TempDir
	File tempDir;

	private Settings settings;

	@BeforeEach
	void setUp() throws Exception {
		injectLauncherContext(tempDir);
		resetSettingsSingleton();
		settings = Settings.getInstance();
	}

	// ── setReleaseSource / getReleaseSource (convenience) ─────────────────

	@Test
	void getReleaseSource_noSourceSet_returnsNull() {
		assertNull(settings.getReleaseSource());
	}

	@Test
	void setReleaseSource_nonNull_retrievableViaGetReleaseSource() {
		ReleaseSource source = Collections::emptyList;

		settings.setReleaseSource(source);

		assertSame(source, settings.getReleaseSource());
	}

	@Test
	void setReleaseSource_null_removesLauncherSource() {
		settings.setReleaseSource(Collections::emptyList);
		assertNotNull(settings.getReleaseSource());

		settings.setReleaseSource(null);

		assertNull(settings.getReleaseSource());
	}

	@Test
	void setReleaseSource_registersUnderLauncherSourceId() {
		ReleaseSource source = Collections::emptyList;

		settings.setReleaseSource(source);

		assertSame(source, settings.getReleaseSource(Settings.LAUNCHER_SOURCE_ID));
	}

	@Test
	void setReleaseSource_replacesExistingLauncherSource() {
		ReleaseSource first = Collections::emptyList;
		ReleaseSource second = Collections::emptyList;

		settings.setReleaseSource(first);
		settings.setReleaseSource(second);

		assertSame(second, settings.getReleaseSource());
	}

	// ── addReleaseSource ──────────────────────────────────────────────────

	@Test
	void addReleaseSource_registersNewSource() {
		ReleaseSource source = Collections::emptyList;

		settings.addReleaseSource("my-extension", source);

		assertSame(source, settings.getReleaseSource("my-extension"));
	}

	@Test
	void addReleaseSource_replacesExistingSourceWithSameId() {
		ReleaseSource first = Collections::emptyList;
		ReleaseSource second = Collections::emptyList;

		settings.addReleaseSource("ext", first);
		settings.addReleaseSource("ext", second);

		assertSame(second, settings.getReleaseSource("ext"));
	}

	@Test
	void addReleaseSource_nullId_throwsIllegalArgumentException() {
		assertThrows(IllegalArgumentException.class, () -> settings.addReleaseSource(null, Collections::emptyList));
	}

	@Test
	void addReleaseSource_nullSource_throwsIllegalArgumentException() {
		assertThrows(IllegalArgumentException.class, () -> settings.addReleaseSource("ext", null));
	}

	@Test
	void addReleaseSource_multipleSources_coexist() {
		ReleaseSource launcher = Collections::emptyList;
		ReleaseSource ext1 = Collections::emptyList;
		ReleaseSource ext2 = Collections::emptyList;

		settings.setReleaseSource(launcher);
		settings.addReleaseSource("ext-1", ext1);
		settings.addReleaseSource("ext-2", ext2);

		assertSame(launcher, settings.getReleaseSource());
		assertSame(ext1, settings.getReleaseSource("ext-1"));
		assertSame(ext2, settings.getReleaseSource("ext-2"));
		assertEquals(3, settings.getReleaseSources().size());
	}

	// ── removeReleaseSource ───────────────────────────────────────────────

	@Test
	void removeReleaseSource_existingId_returnsRemovedSource() {
		ReleaseSource source = Collections::emptyList;
		settings.addReleaseSource("ext", source);

		ReleaseSource removed = settings.removeReleaseSource("ext");

		assertSame(source, removed);
		assertNull(settings.getReleaseSource("ext"));
	}

	@Test
	void removeReleaseSource_unknownId_returnsNull() {
		ReleaseSource removed = settings.removeReleaseSource("nonexistent");

		assertNull(removed);
	}

	@Test
	void removeReleaseSource_nullId_returnsNull() {
		assertNull(settings.removeReleaseSource(null));
	}

	@Test
	void removeReleaseSource_doesNotAffectOtherSources() {
		ReleaseSource launcher = Collections::emptyList;
		ReleaseSource ext = Collections::emptyList;
		settings.setReleaseSource(launcher);
		settings.addReleaseSource("ext", ext);

		settings.removeReleaseSource("ext");

		assertSame(launcher, settings.getReleaseSource());
		assertNull(settings.getReleaseSource("ext"));
		assertEquals(1, settings.getReleaseSources().size());
	}

	// ── getReleaseSource(id) ──────────────────────────────────────────────

	@Test
	void getReleaseSourceById_nullId_returnsNull() {
		assertNull(settings.getReleaseSource(null));
	}

	@Test
	void getReleaseSourceById_unknownId_returnsNull() {
		assertNull(settings.getReleaseSource("unknown"));
	}

	// ── getReleaseSources ─────────────────────────────────────────────────

	@Test
	void getReleaseSources_empty_returnsEmptyMap() {
		Map<String, ReleaseSource> all = settings.getReleaseSources();

		assertTrue(all.isEmpty());
	}

	@Test
	void getReleaseSources_returnsAllRegisteredSources() {
		settings.setReleaseSource(Collections::emptyList);
		settings.addReleaseSource("ext-a", Collections::emptyList);
		settings.addReleaseSource("ext-b", Collections::emptyList);

		Map<String, ReleaseSource> all = settings.getReleaseSources();

		assertEquals(3, all.size());
		assertTrue(all.containsKey(Settings.LAUNCHER_SOURCE_ID));
		assertTrue(all.containsKey("ext-a"));
		assertTrue(all.containsKey("ext-b"));
	}

	@Test
	void getReleaseSources_returnedMapIsUnmodifiable() {
		settings.setReleaseSource(Collections::emptyList);
		Map<String, ReleaseSource> all = settings.getReleaseSources();

		assertThrows(UnsupportedOperationException.class, () -> all.put("new", Collections::emptyList));
	}

	// ── addReleaseSource via LAUNCHER_SOURCE_ID is same as setReleaseSource ─

	@Test
	void addReleaseSource_withLauncherId_sameAsSetReleaseSource() {
		ReleaseSource source = Collections::emptyList;

		settings.addReleaseSource(Settings.LAUNCHER_SOURCE_ID, source);

		assertSame(source, settings.getReleaseSource());
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	private static void injectLauncherContext(File dataDir) throws Exception {
		Constructor<LauncherContext> ctor = LauncherContext.class.getDeclaredConstructor(String.class);
		ctor.setAccessible(true);
		LauncherContext ctx = ctor.newInstance("test-registry");
		Field dataDirField = LauncherContext.class.getDeclaredField("dataDir");
		dataDirField.setAccessible(true);
		dataDirField.set(ctx, dataDir);
		Field instanceField = LauncherContext.class.getDeclaredField("instance");
		instanceField.setAccessible(true);
		instanceField.set(null, ctx);
	}

	private static void resetSettingsSingleton() throws Exception {
		Field instanceField = Settings.class.getDeclaredField("instance");
		instanceField.setAccessible(true);
		instanceField.set(null, null);
	}

}
