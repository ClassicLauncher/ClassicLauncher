package net.classiclauncher.launcher.account;

import java.util.*;

import lombok.Getter;
import top.wavelength.betterreflection.BetterReflectionClass;

/**
 * Registry of known account types and their associated reflection metadata.
 *
 * <p>
 * The launcher core registers {@link #OFFLINE} automatically. Extensions register additional types in their
 * {@code onLoad} via {@link #register(String, BetterReflectionClass, BetterReflectionClass)}.
 *
 * <p>
 * This registry is for introspection and tooling. Deserialization is driven separately by
 * {@link Accounts#registerProvider(AccountProvider)}.
 *
 * <h3>Example — registering a type from an extension</h3>
 *
 * <pre>{@code
 * // Inside LauncherExtension.onLoad():
 * AccountType.register(MyAccountType.MY_TYPE, MyAccount.CLASS, MyAccountProvider.CLASS);
 * }</pre>
 */
public final class AccountType {

	/**
	 * Built-in type ID for offline (unauthenticated) accounts.
	 */
	public static final String OFFLINE = "OFFLINE";

	private static final Map<String, Entry> registry = new LinkedHashMap<>();

	static {
		register(OFFLINE, OfflineAccount.CLASS, OfflineAccountProvider.CLASS);
	}

	/**
	 * Registers an account type with its associated {@link Account} and {@link AccountProvider} reflection classes.
	 *
	 * <p>
	 * If a type with the same ID (case-insensitive) is already registered it is replaced, allowing extensions to
	 * upgrade built-in stubs.
	 *
	 * @param typeId
	 *            the stable, uppercase type identifier stored in YAML
	 * @param accountClass
	 *            {@code BetterReflectionClass} for the {@link Account} subclass
	 * @param providerClass
	 *            {@code BetterReflectionClass} for the {@link AccountProvider} subclass
	 */
	public static void register(String typeId, BetterReflectionClass<? extends Account> accountClass,
			BetterReflectionClass<? extends AccountProvider> providerClass) {
		if (typeId == null || typeId.isEmpty()) throw new IllegalArgumentException("typeId must not be blank");
		registry.put(typeId.toUpperCase(), new Entry(typeId.toUpperCase(), accountClass, providerClass));
	}

	/**
	 * Looks up a registered entry by type ID (case-insensitive).
	 *
	 * @param typeId
	 *            the type identifier
	 * @return the entry, or empty if not registered
	 */
	public static Optional<Entry> getEntry(String typeId) {
		if (typeId == null) return Optional.empty();
		return Optional.ofNullable(registry.get(typeId.toUpperCase()));
	}

	/**
	 * Returns the {@link Account} {@code BetterReflectionClass} for the given type ID, or empty if not registered.
	 */
	public static Optional<BetterReflectionClass<? extends Account>> getAccountClass(String typeId) {
		return getEntry(typeId).map(Entry::getAccountClass);
	}

	/**
	 * Returns the {@link AccountProvider} {@code BetterReflectionClass} for the given type ID, or empty if not
	 * registered.
	 */
	public static Optional<BetterReflectionClass<? extends AccountProvider>> getProviderClass(String typeId) {
		return getEntry(typeId).map(Entry::getProviderClass);
	}

	/**
	 * Returns an unmodifiable view of all registered entries in registration order.
	 */
	public static Collection<Entry> getAll() {
		return Collections.unmodifiableCollection(registry.values());
	}

	private AccountType() {
	}

	// ── Entry ─────────────────────────────────────────────────────────────────

	/**
	 * Immutable record of a registered account type.
	 */
	@Getter
	public static final class Entry {

		private final String typeId;

		private final BetterReflectionClass<? extends Account> accountClass;

		private final BetterReflectionClass<? extends AccountProvider> providerClass;

		Entry(String typeId, BetterReflectionClass<? extends Account> accountClass,
				BetterReflectionClass<? extends AccountProvider> providerClass) {
			this.typeId = typeId;
			this.accountClass = accountClass;
			this.providerClass = providerClass;
		}

	}

}
