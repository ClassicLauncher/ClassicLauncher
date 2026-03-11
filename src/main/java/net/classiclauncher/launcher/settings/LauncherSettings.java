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

}
