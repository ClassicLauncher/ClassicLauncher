package net.classiclauncher.launcher.update;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.settings.LauncherSettings;

class UpdateCheckerTest {

	@TempDir
	File tempDir;

	private LauncherSettings settings;

	@BeforeEach
	void setUp() throws Exception {
		// Inject the context directly so the dataDir is always the isolated tempDir,
		// bypassing platform-specific logic (e.g. XDG_CONFIG_HOME on Linux CI) that
		// would cause all tests to share the same settings file.
		injectLauncherContext(tempDir);
		settings = new LauncherSettings();
	}

	// ── 2 newer versions found ─────────────────────────────────────────────

	@Test
	void check_twoNewerVersions_returnsBothSortedAscending() throws IOException {
		// GitHub returns newest first; UpdateChecker sorts ascending
		ReleaseSource source = () -> Arrays.asList(release("v1.0.2"), release("v1.0.1"));

		UpdatePlan plan = UpdateChecker.checkInternal(source, "1.0.0", settings, false);

		assertNotNull(plan);
		assertTrue(plan.hasUpdate());
		assertEquals(2, plan.getReleases().size());
		// Oldest first
		assertEquals("v1.0.1", plan.getReleases().get(0).getTagName());
		assertEquals("v1.0.2", plan.getReleases().get(1).getTagName());
		assertEquals("1.0.2", plan.latestVersion());
	}

	// ── Already latest → empty plan ───────────────────────────────────────

	@Test
	void check_alreadyLatest_returnsEmptyPlan() throws IOException {
		ReleaseSource source = () -> Collections.singletonList(release("v1.0.0"));

		UpdatePlan plan = UpdateChecker.checkInternal(source, "1.0.0", settings, false);

		assertNotNull(plan);
		assertFalse(plan.hasUpdate());
	}

	@Test
	void check_olderVersionOnServer_returnsEmptyPlan() throws IOException {
		ReleaseSource source = () -> Collections.singletonList(release("v0.9.0"));

		UpdatePlan plan = UpdateChecker.checkInternal(source, "1.0.0", settings, false);

		assertNotNull(plan);
		assertFalse(plan.hasUpdate());
	}

	// ── Network error → graceful IOException ──────────────────────────────

	@Test
	void check_networkError_throwsIOException() {
		ReleaseSource source = () -> {
			throw new IOException("Connection refused");
		};

		assertThrows(IOException.class, () -> UpdateChecker.checkInternal(source, "1.0.0", settings, false));
	}

	// ── Skipped version ────────────────────────────────────────────────────

	@Test
	void check_latestVersionIsSkipped_returnsEmptyPlan() throws IOException {
		settings.setSkippedVersion("1.0.2");
		ReleaseSource source = () -> Arrays.asList(release("v1.0.2"), release("v1.0.1"));

		UpdatePlan plan = UpdateChecker.checkInternal(source, "1.0.0", settings, false);

		assertNotNull(plan);
		assertFalse(plan.hasUpdate(), "Plan should be empty when latest matches skipped version");
	}

	@Test
	void check_latestVersionIsSkipped_ignoredWhenManualCheck() throws IOException {
		settings.setSkippedVersion("1.0.2");
		ReleaseSource source = () -> Arrays.asList(release("v1.0.2"), release("v1.0.1"));

		// ignoreSkippedVersion=true → manual check bypasses the skip filter
		UpdatePlan plan = UpdateChecker.checkInternal(source, "1.0.0", settings, true);

		assertNotNull(plan);
		assertTrue(plan.hasUpdate(), "Manual check should show updates even when version is skipped");
	}

	@Test
	void check_intermediateVersionSkipped_latestStillShown() throws IOException {
		// Only skip 1.0.1, not 1.0.2 (the latest)
		settings.setSkippedVersion("1.0.1");
		ReleaseSource source = () -> Arrays.asList(release("v1.0.2"), release("v1.0.1"));

		UpdatePlan plan = UpdateChecker.checkInternal(source, "1.0.0", settings, false);

		assertNotNull(plan);
		assertTrue(plan.hasUpdate(), "1.0.2 is newer than skipped 1.0.1, so updates should appear");
	}

	// ── Null source ───────────────────────────────────────────────────────

	@Test
	void check_nullSource_returnsNull() throws IOException {
		UpdatePlan plan = UpdateChecker.checkInternal(null, "1.0.0", settings, false);

		assertNull(plan);
	}

	// ── Empty release list ────────────────────────────────────────────────

	@Test
	void check_emptyReleaseList_returnsEmptyPlan() throws IOException {
		ReleaseSource source = Collections::emptyList;

		UpdatePlan plan = UpdateChecker.checkInternal(source, "1.0.0", settings, false);

		assertNotNull(plan);
		assertFalse(plan.hasUpdate());
	}

	// ── Tag with "v" prefix stripped correctly ────────────────────────────

	@Test
	void check_tagWithVPrefix_comparedCorrectly() throws IOException {
		ReleaseSource source = () -> Collections.singletonList(release("v1.0.1"));

		UpdatePlan plan = UpdateChecker.checkInternal(source, "1.0.0", settings, false);

		assertNotNull(plan);
		assertTrue(plan.hasUpdate());
		assertEquals("1.0.1", plan.latestVersion()); // v prefix stripped in latestVersion()
	}

	// ── Multiple versions sorted correctly ────────────────────────────────

	@Test
	void check_multipleVersions_sortedAscending() throws IOException {
		ReleaseSource source = () -> Arrays.asList(release("v1.0.5"), release("v1.0.1"), release("v1.0.3"));

		UpdatePlan plan = UpdateChecker.checkInternal(source, "1.0.0", settings, false);

		assertNotNull(plan);
		assertEquals(3, plan.getReleases().size());
		assertEquals("v1.0.1", plan.getReleases().get(0).getTagName());
		assertEquals("v1.0.3", plan.getReleases().get(1).getTagName());
		assertEquals("v1.0.5", plan.getReleases().get(2).getTagName());
	}

	// ── Mixed newer and older versions ────────────────────────────────────

	@Test
	void check_mixedVersions_onlyNewerIncluded() throws IOException {
		ReleaseSource source = () -> Arrays.asList(release("v2.0.0"), release("v1.0.0"), release("v0.5.0"),
				release("v1.5.0"));

		UpdatePlan plan = UpdateChecker.checkInternal(source, "1.0.0", settings, false);

		assertNotNull(plan);
		assertEquals(2, plan.getReleases().size());
		assertEquals("v1.5.0", plan.getReleases().get(0).getTagName());
		assertEquals("v2.0.0", plan.getReleases().get(1).getTagName());
	}

	// ── checkManual with null source ──────────────────────────────────────

	@Test
	void checkManual_nullSource_returnsNull() throws IOException {
		UpdatePlan plan = UpdateChecker.checkManual(null, "1.0.0", settings);

		assertNull(plan);
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	private static ReleaseInfo release(String tagName) {
		String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;
		List<AssetInfo> assets = new ArrayList<>();
		assets.add(new AssetInfo("app-" + version + ".jar", "https://example.com/app-" + version + ".jar", 1024));
		return new ReleaseInfo(tagName, "Release " + version, "Changelog for " + version, assets);
	}

	/**
	 * Injects a {@link LauncherContext} whose {@code dataDir} is set directly to {@code dataDir}, bypassing
	 * platform-specific path resolution (e.g. {@code XDG_CONFIG_HOME} on Linux). This mirrors the same isolation
	 * pattern used in {@code ExtensionsTest} to prevent settings written by one test from leaking into the next when
	 * running on Linux CI.
	 */
	private static void injectLauncherContext(File dataDir) throws Exception {
		Constructor<LauncherContext> ctor = LauncherContext.class.getDeclaredConstructor(String.class);
		ctor.setAccessible(true);
		LauncherContext ctx = ctor.newInstance("test-updater");
		Field dataDirField = LauncherContext.class.getDeclaredField("dataDir");
		dataDirField.setAccessible(true);
		dataDirField.set(ctx, dataDir);
		Field instanceField = LauncherContext.class.getDeclaredField("instance");
		instanceField.setAccessible(true);
		instanceField.set(null, ctx);
	}

}
