package net.classiclauncher.launcher.update;

import java.util.Collections;
import java.util.List;

/**
 * Immutable DTO representing a single GitHub release.
 */
public final class ReleaseInfo {

	private final String tagName;
	private final String name;
	private final String body;
	private final List<AssetInfo> assets;

	public ReleaseInfo(String tagName, String name, String body, List<AssetInfo> assets) {
		this.tagName = tagName != null ? tagName : "";
		this.name = name != null ? name : "";
		this.body = body != null ? body : "";
		this.assets = assets != null ? Collections.unmodifiableList(assets) : Collections.<AssetInfo>emptyList();
	}

	/** The Git tag name of this release (e.g. {@code "v1.0.3"}). */
	public String getTagName() {
		return tagName;
	}

	/** Human-readable release title (e.g. {@code "1.0.3 — Bug fixes"}). */
	public String getName() {
		return name;
	}

	/** Markdown changelog body. May be empty if the release has no description. */
	public String getBody() {
		return body;
	}

	/** Downloadable assets attached to this release. */
	public List<AssetInfo> getAssets() {
		return assets;
	}

}
