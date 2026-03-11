package net.classiclauncher.launcher.account;

import java.util.UUID;

import dev.utano.ymlite.config.YmlConfig;
import lombok.Getter;

/**
 * Abstract base for all account types.
 * <p>
 * To add a new account type in a downstream project:
 * <ol>
 * <li>Extend this class.</li>
 * <li>Define a {@code static fromConfig(String id, YmlConfig)} factory method.</li>
 * <li>Register it: {@code accounts.registerType("MY_TYPE", MyAccount::fromConfig)}</li>
 * </ol>
 */
@Getter
public abstract class Account {

	private final String id;
	private final String displayName;

	protected Account(String id, String displayName) {
		this.id = id;
		this.displayName = displayName;
	}

	/**
	 * Returns the type identifier string that will be stored in this account's YAML file. Use constants from
	 * {@link AccountType} for built-in types, or your own string for custom types.
	 */
	public abstract String getType();

	/**
	 * Serializes this account into the given config and saves it to disk. Writes {@code type} unconditionally, then
	 * delegates field serialization to {@link #saveData(YmlConfig)}.
	 */
	public final void save(YmlConfig config) {
		config.set("type", getType());
		saveData(config);
	}

	/**
	 * Subclass-specific serialization: write all fields except {@code type} (handled by the base class).
	 * Implementations must call {@code config.save()} to persist to disk.
	 */
	protected abstract void saveData(YmlConfig config);

	protected static String generateId() {
		return UUID.randomUUID().toString();
	}

}
