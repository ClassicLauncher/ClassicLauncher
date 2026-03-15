package net.classiclauncher.launcher.update.install;

import java.util.Optional;

import net.classiclauncher.launcher.platform.Platform;
import net.classiclauncher.launcher.update.AssetInfo;
import net.classiclauncher.launcher.update.ReleaseInfo;

/**
 * Selects the appropriate {@link AssetInfo} from a {@link ReleaseInfo} based on the current platform and distribution
 * mode.
 *
 * <p>
 * Selection table:
 * <ul>
 * <li>{@link DistributionMode#JAR} (any platform) → {@code .jar}</li>
 * <li>{@link Platform#MACOS} + {@link DistributionMode#INSTALLER} → {@code .dmg}</li>
 * <li>{@link Platform#WINDOWS} + {@link DistributionMode#INSTALLER} → {@code .msi}</li>
 * <li>{@link Platform#LINUX} + {@link DistributionMode#INSTALLER} → {@code .deb} (preferred), then {@code .rpm}</li>
 * <li>{@link Platform#UNKNOWN} → {@code .jar} fallback</li>
 * </ul>
 */
public class ArtifactSelector {

	/**
	 * Returns the best-matching asset for the given platform and distribution mode, or {@link Optional#empty()} if no
	 * suitable asset is found in the release.
	 *
	 * @param release
	 *            the release to select from
	 * @param platform
	 *            the host OS; use {@link Platform#current()} for the running JVM
	 * @param mode
	 *            whether the launcher is running as a JAR or native installer
	 * @return the matched asset, or empty if none matches
	 */
	public Optional<AssetInfo> select(ReleaseInfo release, Platform platform, DistributionMode mode) {
		if (release == null) return Optional.empty();

		if (mode == DistributionMode.JAR || platform == Platform.UNKNOWN) {
			return findByExtension(release, ".jar");
		}

		switch (platform) {
			case MACOS :
				return findByExtension(release, ".dmg");
			case WINDOWS :
				return findByExtension(release, ".msi");
			case LINUX : {
				Optional<AssetInfo> deb = findByExtension(release, ".deb");
				if (deb.isPresent()) return deb;
				return findByExtension(release, ".rpm");
			}
			default :
				return findByExtension(release, ".jar");
		}
	}

	private Optional<AssetInfo> findByExtension(ReleaseInfo release, String extension) {
		for (AssetInfo asset : release.getAssets()) {
			if (asset.getName().endsWith(extension)) {
				return Optional.of(asset);
			}
		}
		return Optional.empty();
	}

}
