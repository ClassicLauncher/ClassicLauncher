package net.classiclauncher.launcher.account;

import dev.utano.ymlite.config.YmlConfig;
import lombok.Getter;
import top.wavelength.betterreflection.BetterReflectionClass;

@Getter
public class OfflineAccount extends Account {

	private final String username;

	public OfflineAccount(String id, String username) {
		super(id, username);
		this.username = username;
	}

	public static OfflineAccount create(String username) {
		return new OfflineAccount(generateId(), username);
	}

	public static OfflineAccount fromConfig(String id, YmlConfig config) {
		String username = config.getString("username", config.getString("display-name", "Player"));
		return new OfflineAccount(id, username);
	}

	@Override
	public String getType() {
		return AccountType.OFFLINE;
	}

	@Override
	protected void saveData(YmlConfig config) {
		config.set("display-name", getDisplayName());
		config.set("username", username);
		config.save();
	}

	public static BetterReflectionClass<OfflineAccount> CLASS = new BetterReflectionClass<>(OfflineAccount.class);

}
