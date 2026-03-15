package net.classiclauncher.launcher.update;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.classiclauncher.launcher.platform.Platform;
import net.classiclauncher.launcher.update.install.ArtifactSelector;
import net.classiclauncher.launcher.update.install.DistributionMode;

class ArtifactSelectorTest {

	private final ArtifactSelector selector = new ArtifactSelector();

	// ── JAR mode (all platforms) ───────────────────────────────────────────

	@Test
	void select_jarMode_windows_returnsJar() {
		ReleaseInfo release = releaseWithAssets("app.jar", "app.msi", "app.dmg", "app.deb");

		Optional<AssetInfo> result = selector.select(release, Platform.WINDOWS, DistributionMode.JAR);

		assertTrue(result.isPresent());
		assertEquals("app.jar", result.get().getName());
	}

	@Test
	void select_jarMode_macos_returnsJar() {
		ReleaseInfo release = releaseWithAssets("app.jar", "app.dmg");

		Optional<AssetInfo> result = selector.select(release, Platform.MACOS, DistributionMode.JAR);

		assertTrue(result.isPresent());
		assertEquals("app.jar", result.get().getName());
	}

	@Test
	void select_jarMode_linux_returnsJar() {
		ReleaseInfo release = releaseWithAssets("app.jar", "app.deb");

		Optional<AssetInfo> result = selector.select(release, Platform.LINUX, DistributionMode.JAR);

		assertTrue(result.isPresent());
		assertEquals("app.jar", result.get().getName());
	}

	@Test
	void select_jarMode_unknown_returnsJar() {
		ReleaseInfo release = releaseWithAssets("app.jar", "app.msi");

		Optional<AssetInfo> result = selector.select(release, Platform.UNKNOWN, DistributionMode.JAR);

		assertTrue(result.isPresent());
		assertEquals("app.jar", result.get().getName());
	}

	// ── INSTALLER mode ─────────────────────────────────────────────────────

	@Test
	void select_installerMode_macos_returnsDmg() {
		ReleaseInfo release = releaseWithAssets("app.jar", "app.dmg", "app.msi", "app.deb");

		Optional<AssetInfo> result = selector.select(release, Platform.MACOS, DistributionMode.INSTALLER);

		assertTrue(result.isPresent());
		assertEquals("app.dmg", result.get().getName());
	}

	@Test
	void select_installerMode_windows_returnsMsi() {
		ReleaseInfo release = releaseWithAssets("app.jar", "app.dmg", "app.msi", "app.deb");

		Optional<AssetInfo> result = selector.select(release, Platform.WINDOWS, DistributionMode.INSTALLER);

		assertTrue(result.isPresent());
		assertEquals("app.msi", result.get().getName());
	}

	@Test
	void select_installerMode_linux_prefersDeb() {
		ReleaseInfo release = releaseWithAssets("app.jar", "app.deb", "app.rpm");

		Optional<AssetInfo> result = selector.select(release, Platform.LINUX, DistributionMode.INSTALLER);

		assertTrue(result.isPresent());
		assertEquals("app.deb", result.get().getName());
	}

	@Test
	void select_installerMode_linux_rpmWhenNoDebPresent() {
		ReleaseInfo release = releaseWithAssets("app.jar", "app.rpm");

		Optional<AssetInfo> result = selector.select(release, Platform.LINUX, DistributionMode.INSTALLER);

		assertTrue(result.isPresent());
		assertEquals("app.rpm", result.get().getName());
	}

	// ── Unknown platform → JAR fallback ───────────────────────────────────

	@Test
	void select_installerMode_unknown_fallsBackToJar() {
		ReleaseInfo release = releaseWithAssets("app.jar", "app.deb");

		Optional<AssetInfo> result = selector.select(release, Platform.UNKNOWN, DistributionMode.INSTALLER);

		assertTrue(result.isPresent());
		assertEquals("app.jar", result.get().getName());
	}

	// ── No matching asset ──────────────────────────────────────────────────

	@Test
	void select_noMatchingAsset_returnsEmpty() {
		ReleaseInfo release = releaseWithAssets("source.tar.gz");

		Optional<AssetInfo> result = selector.select(release, Platform.WINDOWS, DistributionMode.INSTALLER);

		assertFalse(result.isPresent());
	}

	@Test
	void select_emptyAssets_returnsEmpty() {
		ReleaseInfo release = new ReleaseInfo("v1.0.1", "Release", "", new ArrayList<>());

		Optional<AssetInfo> result = selector.select(release, Platform.MACOS, DistributionMode.INSTALLER);

		assertFalse(result.isPresent());
	}

	@Test
	void select_nullRelease_returnsEmpty() {
		Optional<AssetInfo> result = selector.select(null, Platform.WINDOWS, DistributionMode.JAR);

		assertFalse(result.isPresent());
	}

	// ── Helpers ────────────────────────────────────────────────────────────

	private static ReleaseInfo releaseWithAssets(String... names) {
		List<AssetInfo> assets = new ArrayList<>();
		for (String name : names) {
			assets.add(new AssetInfo(name, "https://example.com/download/" + name, 1024));
		}
		return new ReleaseInfo("v1.0.1", "Release 1.0.1", "Changelog", assets);
	}

}
