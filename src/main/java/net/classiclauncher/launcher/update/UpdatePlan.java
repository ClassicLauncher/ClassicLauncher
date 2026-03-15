package net.classiclauncher.launcher.update;

import java.util.Collections;
import java.util.List;

/**
 * Result of an update availability check.
 *
 * <p>
 * {@link #getReleases()} returns every release newer than the installed version, ordered oldest to newest. The list is
 * empty when the launcher is already up to date.
 */
public final class UpdatePlan {

	private final String currentVersion;
	private final List<ReleaseInfo> releases;

	/**
	 * @param currentVersion
	 *            the currently installed launcher version
	 * @param releases
	 *            releases newer than {@code currentVersion}, sorted oldest → newest; must not be {@code null}
	 */
	public UpdatePlan(String currentVersion, List<ReleaseInfo> releases) {
		this.currentVersion = currentVersion != null ? currentVersion : "0.0.0";
		this.releases = releases != null
				? Collections.unmodifiableList(releases)
				: Collections.<ReleaseInfo>emptyList();
	}

	/** Returns the currently installed launcher version string. */
	public String getCurrentVersion() {
		return currentVersion;
	}

	/**
	 * Returns all releases newer than the current version, sorted oldest to newest. Empty when already up to date.
	 */
	public List<ReleaseInfo> getReleases() {
		return releases;
	}

	/** Returns {@code true} if at least one newer release is available. */
	public boolean hasUpdate() {
		return !releases.isEmpty();
	}

	/**
	 * Returns the tag name of the latest available release, or {@link #getCurrentVersion()} if no updates are
	 * available.
	 */
	public String latestVersion() {
		if (releases.isEmpty()) return currentVersion;
		String tag = releases.get(releases.size() - 1).getTagName();
		return tag.startsWith("v") ? tag.substring(1) : tag;
	}

}
