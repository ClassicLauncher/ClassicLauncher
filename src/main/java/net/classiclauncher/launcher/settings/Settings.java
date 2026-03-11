package net.classiclauncher.launcher.settings;

import lombok.Getter;
import net.classiclauncher.launcher.account.Accounts;
import net.classiclauncher.launcher.extension.Extensions;
import net.classiclauncher.launcher.jre.JavaManager;
import net.classiclauncher.launcher.profile.Profiles;

public class Settings {

	private static Settings instance;

	private final LauncherSettings launcherSettings;
	private final AccountSettings accountSettings;
	@Getter
	private final Accounts accounts;
	@Getter
	private final Profiles profiles;
	@Getter
	private final JavaManager javaManager;
	@Getter
	private final Extensions extensions;

	private Settings() {
		launcherSettings = new LauncherSettings();
		accountSettings = new AccountSettings();
		accounts = new Accounts();
		accounts.load();
		profiles = new Profiles();
		profiles.load();
		javaManager = new JavaManager();
		javaManager.load();
		extensions = new Extensions();
		extensions.load();
	}

	public static Settings getInstance() {
		if (instance == null) instance = new Settings();
		return instance;
	}

	public LauncherSettings getLauncher() {
		return launcherSettings;
	}

	public AccountSettings getAccount() {
		return accountSettings;
	}

}
