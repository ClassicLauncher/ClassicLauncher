package net.classiclauncher.launcher.settings;

import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.LauncherContext;

public class LauncherSettings {

	private final YmlConfig config;

	public LauncherSettings() {
		config = new YmlConfig(LauncherContext.getInstance().getDataDir(), "settings");
		config.addDefault("ui.style", LauncherStyle.V1_1.name());
		config.addDefault("update-notes.url", "");
		config.addDefault("selected-profile", "");
		config.addDefault("update.check-enabled", true);
		config.addDefault("update.skipped-version", "");
		config.generateConfigs();
	}

	public LauncherStyle getStyle() {
		String raw = config.getString("ui.style", LauncherStyle.V1_1.name());
		try {
			return LauncherStyle.valueOf(raw.toUpperCase());
		} catch (IllegalArgumentException e) {
			return LauncherStyle.V1_1;
		}
	}

	public void setStyle(LauncherStyle style) {
		config.set("ui.style", style.name());
		config.save();
	}

	public String getUpdateNotesUrl() {
		return config.getString("update-notes.url", "");
	}

	public void setUpdateNotesUrl(String url) {
		config.set("update-notes.url", url != null ? url : "");
		config.save();
	}

	public String getSelectedProfileId() {
		String id = config.getString("selected-profile", "");
		return id.isEmpty() ? null : id;
	}

	public void setSelectedProfileId(String id) {
		config.set("selected-profile", id != null ? id : "");
		config.save();
	}

	/** Returns {@code true} if automatic update checks on startup are enabled (default: {@code true}). */
	public boolean isUpdateCheckEnabled() {
		return config.getBoolean("update.check-enabled", true);
	}

	public void setUpdateCheckEnabled(boolean enabled) {
		config.set("update.check-enabled", enabled);
		config.save();
	}

	/**
	 * Returns the version the user asked to skip, or {@code null} if no version has been skipped. An empty string in
	 * the config is normalised to {@code null}.
	 */
	public String getSkippedVersion() {
		String v = config.getString("update.skipped-version", "");
		return (v == null || v.isEmpty()) ? null : v;
	}

	public void setSkippedVersion(String version) {
		config.set("update.skipped-version", version != null ? version : "");
		config.save();
	}

}
