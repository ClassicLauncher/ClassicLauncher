package net.classiclauncher.launcher.settings;

import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.LauncherContext;

public class AccountSettings {

	private final YmlConfig config;

	public AccountSettings() {
		config = new YmlConfig(LauncherContext.getInstance().getDataDir(), "account-settings");
		config.addDefault("selected-account", "");
		config.generateConfigs();
	}

	public String getSelectedAccountId() {
		String id = config.getString("selected-account", "");
		return id.isEmpty() ? null : id;
	}

	public void setSelectedAccountId(String id) {
		config.set("selected-account", id != null ? id : "");
		config.save();
	}

}
