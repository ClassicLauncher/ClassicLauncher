package net.classiclauncher.launcher.update;

/**
 * Immutable DTO representing a single downloadable artifact attached to a GitHub release.
 */
public final class AssetInfo {

	private final String name;
	private final String downloadUrl;
	private final long sizeBytes;

	public AssetInfo(String name, String downloadUrl, long sizeBytes) {
		this.name = name != null ? name : "";
		this.downloadUrl = downloadUrl != null ? downloadUrl : "";
		this.sizeBytes = sizeBytes;
	}

	/** File name of the asset (e.g. {@code "ClassicLauncher-1.0.3.jar"}). */
	public String getName() {
		return name;
	}

	/** Direct download URL for the asset. */
	public String getDownloadUrl() {
		return downloadUrl;
	}

	/** Asset size in bytes; {@code 0} if unknown. */
	public long getSizeBytes() {
		return sizeBytes;
	}

}
