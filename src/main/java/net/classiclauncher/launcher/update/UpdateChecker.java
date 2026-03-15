package net.classiclauncher.launcher.update;

import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.SwingUtilities;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.classiclauncher.launcher.LauncherVersion;
import net.classiclauncher.launcher.settings.LauncherSettings;
import net.classiclauncher.launcher.ui.update.UpdateDialog;

/**
 * Orchestrates the update availability check.
 *
 * <p>
 * {@link #checkAsync(ReleaseSource, String, LauncherSettings, Supplier)} spawns a daemon thread that:
 * <ol>
 * <li>Fetches releases from the provided {@link ReleaseSource}.</li>
 * <li>Filters to releases newer than the given current version.</li>
 * <li>Skips the latest if it matches the user's "skip this version" setting.</li>
 * <li>Dispatches to the EDT to show {@link UpdateDialog} if updates exist.</li>
 * </ol>
 *
 * <p>
 * The checker is fully decoupled from any specific release provider — pass a
 * {@link net.classiclauncher.launcher.update.source.github.GitHubReleaseSource} for GitHub, or any other
 * {@link ReleaseSource} implementation (including lambdas for testing).
 *
 * <p>
 * Network errors are logged at {@code WARN} level and silently suppressed — a failed update check must never interrupt
 * the normal launcher startup.
 */
public class UpdateChecker {

	private static final Logger LOG = LogManager.getLogger(UpdateChecker.class);

	private UpdateChecker() {
	}

	/**
	 * Performs an asynchronous update check on a daemon thread. If a newer version is found, shows the
	 * {@link UpdateDialog} on the EDT using the window returned by {@code windowSupplier}.
	 *
	 * <p>
	 * Returns immediately. If {@code settings.isUpdateCheckEnabled()} is {@code false} or {@code source} is
	 * {@code null}, this method is a no-op.
	 *
	 * @param source
	 *            the release source to query; may be {@code null} (e.g. when repo is not configured)
	 * @param currentVersion
	 *            the version to treat as current (normally {@link LauncherVersion#VERSION})
	 * @param settings
	 *            launcher settings (for {@code check-enabled} and {@code skipped-version})
	 * @param windowSupplier
	 *            lazily evaluated on the EDT to find the parent window for the dialog
	 */
	public static void checkAsync(ReleaseSource source, String currentVersion, LauncherSettings settings,
			Supplier<Window> windowSupplier) {
		if (source == null) return;
		if (!settings.isUpdateCheckEnabled()) return;

		Thread thread = new Thread(() -> {
			try {
				UpdatePlan plan = checkInternal(source, currentVersion, settings, false);
				if (plan == null || !plan.hasUpdate()) return;

				final UpdatePlan finalPlan = plan;
				SwingUtilities.invokeLater(() -> {
					Window owner = windowSupplier.get();
					new UpdateDialog(owner, finalPlan, settings).setVisible(true);
				});
			} catch (IOException e) {
				LOG.warn("Update check failed (network): {}", e.getMessage());
			} catch (Exception e) {
				LOG.warn("Update check failed unexpectedly", e);
			}
		}, "update-check");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Performs a synchronous update check, ignoring the "skip this version" setting. Intended for the "Check Now"
	 * button in settings so users always see available updates on demand.
	 *
	 * @param source
	 *            the release source to query; may be {@code null}
	 * @param currentVersion
	 *            the version to treat as current
	 * @param settings
	 *            launcher settings
	 * @return the update plan, or {@code null} if {@code source} is {@code null}
	 * @throws IOException
	 *             on network or parse errors
	 */
	public static UpdatePlan checkManual(ReleaseSource source, String currentVersion, LauncherSettings settings)
			throws IOException {
		return checkInternal(source, currentVersion, settings, true);
	}

	/**
	 * Full-fidelity check with injectable parameters — used by both public methods and tests.
	 *
	 * @param source
	 *            the release source to query; may be {@code null}
	 * @param currentVersion
	 *            the version to treat as current (normally {@link LauncherVersion#VERSION})
	 * @param settings
	 *            launcher settings
	 * @param ignoreSkippedVersion
	 *            if {@code true}, the skipped-version filter is bypassed
	 * @return the update plan, or {@code null} if {@code source} is {@code null}
	 * @throws IOException
	 *             on network or parse errors
	 */
	static UpdatePlan checkInternal(ReleaseSource source, String currentVersion, LauncherSettings settings,
			boolean ignoreSkippedVersion) throws IOException {
		if (source == null) {
			LOG.debug("No release source provided — skipping update check");
			return null;
		}

		List<ReleaseInfo> allReleases = source.fetchReleases();

		String current = (currentVersion != null && !currentVersion.isEmpty())
				? currentVersion
				: LauncherVersion.VERSION;
		List<ReleaseInfo> newerReleases = new ArrayList<>();
		for (ReleaseInfo release : allReleases) {
			String tag = release.getTagName();
			String version = tag.startsWith("v") ? tag.substring(1) : tag;
			if (LauncherVersion.compare(version, current) > 0) {
				newerReleases.add(release);
			}
		}

		// Sort ascending (oldest first) so changelogs read chronologically
		Collections.sort(newerReleases, new Comparator<ReleaseInfo>() {

			@Override
			public int compare(ReleaseInfo a, ReleaseInfo b) {
				String va = a.getTagName();
				String vb = b.getTagName();
				if (va.startsWith("v")) va = va.substring(1);
				if (vb.startsWith("v")) vb = vb.substring(1);
				return LauncherVersion.compare(va, vb);
			}

		});

		if (newerReleases.isEmpty()) return new UpdatePlan(current, Collections.<ReleaseInfo>emptyList());

		// Skip if the latest version is exactly the one the user asked to skip
		String skipped = ignoreSkippedVersion ? null : settings.getSkippedVersion();
		if (skipped != null && !skipped.isEmpty()) {
			ReleaseInfo latest = newerReleases.get(newerReleases.size() - 1);
			String latestTag = latest.getTagName();
			String latestVersion = latestTag.startsWith("v") ? latestTag.substring(1) : latestTag;
			if (latestVersion.equals(skipped) || latestTag.equals(skipped)) {
				return new UpdatePlan(current, Collections.<ReleaseInfo>emptyList());
			}
		}

		return new UpdatePlan(current, newerReleases);
	}

}
