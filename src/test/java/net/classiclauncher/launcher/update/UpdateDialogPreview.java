package net.classiclauncher.launcher.update;

import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import javax.swing.*;

import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.settings.LauncherSettings;
import net.classiclauncher.launcher.ui.update.UpdateDialog;

/**
 * Visual preview of the {@link UpdateDialog} for manual inspection during development.
 *
 * <p>
 * Run {@link #main(String[])} directly from the IDE (right-click → Run) to open the dialog with three fake releases and
 * verify layout, changelog rendering, and button behaviour.
 *
 * <p>
 * This class is NOT a JUnit test — it has no {@code @Test} annotations and does not run in CI.
 */
public class UpdateDialogPreview {

	public static void main(String[] args) throws Exception {
		UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

		// Set up a temp data directory so LauncherSettings can save
		File tempDir = Files.createTempDirectory("cl-preview").toFile();
		tempDir.deleteOnExit();
		System.setProperty("user.home", tempDir.getAbsolutePath());
		LauncherContext.initialize("preview");
		LauncherSettings settings = new LauncherSettings();

		// Build three fake releases with realistic markdown changelogs
		AssetInfo jar101 = new AssetInfo("ClassicLauncher-1.0.1.jar",
				"https://example.com/releases/v1.0.1/ClassicLauncher-1.0.1.jar", 8_500_000);
		AssetInfo dmg101 = new AssetInfo("ClassicLauncher-1.0.1.dmg",
				"https://example.com/releases/v1.0.1/ClassicLauncher-1.0.1.dmg", 24_000_000);
		ReleaseInfo r101 = new ReleaseInfo("v1.0.1", "1.0.1 — Stability Fixes", "## What's Changed\n\n"
				+ "- Fixed a crash when launching with no Java installations configured\n"
				+ "- Fixed extension icons not loading after update\n"
				+ "- Improved error messages in the launcher log tab\n\n"
				+ "**Full Changelog**: https://github.com/ClassicLauncher/ClassicLauncher/compare/v1.0.0...v1.0.1",
				Arrays.asList(jar101, dmg101));

		AssetInfo jar102 = new AssetInfo("ClassicLauncher-1.0.2.jar",
				"https://example.com/releases/v1.0.2/ClassicLauncher-1.0.2.jar", 8_700_000);
		AssetInfo msi102 = new AssetInfo("ClassicLauncher-1.0.2.msi",
				"https://example.com/releases/v1.0.2/ClassicLauncher-1.0.2.msi", 32_000_000);
		ReleaseInfo r102 = new ReleaseInfo("v1.0.2", "1.0.2 — Profile Editor",
				"## New Features\n\n" + "- Added profile sorting by last used date\n"
						+ "- Profile editor now supports custom JVM arguments per profile\n"
						+ "- Extensions can now declare an `onProfileChange` hook\n\n" + "## Bug Fixes\n\n"
						+ "- Fixed `NullPointerException` when selecting a deleted profile\n"
						+ "- Log tab no longer shows duplicate entries after reload\n",
				Arrays.asList(jar102, msi102));

		AssetInfo jar103 = new AssetInfo("ClassicLauncher-1.0.3.jar",
				"https://example.com/releases/v1.0.3/ClassicLauncher-1.0.3.jar", 8_900_000);
		AssetInfo dmg103 = new AssetInfo("ClassicLauncher-1.0.3.dmg",
				"https://example.com/releases/v1.0.3/ClassicLauncher-1.0.3.dmg", 25_000_000);
		AssetInfo msi103 = new AssetInfo("ClassicLauncher-1.0.3.msi",
				"https://example.com/releases/v1.0.3/ClassicLauncher-1.0.3.msi", 33_000_000);
		AssetInfo deb103 = new AssetInfo("classiclauncher-1.0.3.deb",
				"https://example.com/releases/v1.0.3/classiclauncher-1.0.3.deb", 15_000_000);
		ReleaseInfo r103 = new ReleaseInfo("v1.0.3", "1.0.3 — Auto-Update", "## Highlights\n\n"
				+ "### Auto-Update System\n"
				+ "The launcher now checks for updates on startup and shows a dialog when a newer version is "
				+ "available. You can skip a version or disable the feature entirely from **Settings → Updates**.\n\n"
				+ "### Other Improvements\n\n" + "- Extension settings panel now shows the extension version\n"
				+ "- Reduced startup time by ~200ms on large profiles\n"
				+ "- `DistributionMode` consolidates `jpackage` detection into a single canonical location\n\n"
				+ "## Breaking Changes\n\n"
				+ "- Minimum launcher version for extensions bumped to `1.0.3` for auto-update hook access\n",
				Arrays.asList(jar103, dmg103, msi103, deb103));

		// Ascending order (oldest → newest) as produced by UpdateChecker
		UpdatePlan plan = new UpdatePlan("1.0.0", Arrays.asList(r101, r102, r103));

		SwingUtilities.invokeAndWait(() -> {
			UpdateDialog dialog = new UpdateDialog(null, plan, settings);
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		});

		System.exit(0);
	}

}
