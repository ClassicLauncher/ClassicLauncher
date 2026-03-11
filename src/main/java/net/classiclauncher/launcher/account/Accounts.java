package net.classiclauncher.launcher.account;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import dev.utano.ymlite.config.YmlConfig;
import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.extension.LauncherExtension;

public class Accounts {

	// ── Static ready-signal ────────────────────────────────────────────────────

	/**
	 * Callbacks waiting for the first {@link Accounts} instance to be signalled as ready. Thread-safe; populated by
	 * {@link #onReady(Consumer)} and drained by {@link #signalReady}.
	 */
	private static final ConcurrentLinkedQueue<Consumer<Accounts>> pendingCallbacks = new ConcurrentLinkedQueue<>();
	private static volatile boolean ready = false;
	private static volatile Accounts readyInstance = null;

	/**
	 * Registers a callback to be invoked with the ready {@link Accounts} instance.
	 *
	 * <p>
	 * If {@link Accounts} is already ready (i.e. {@link #signalReady} has already been called), the callback fires
	 * immediately on the calling thread. Otherwise it is enqueued and will be called by {@link #signalReady} when it
	 * runs during bootstrap.
	 *
	 * <p>
	 * A double-check is performed after enqueueing to prevent a lost-signal race between the {@code ready} flag check
	 * and the queue insertion.
	 *
	 * <p>
	 * Typical usage from an extension's {@link LauncherExtension#onLoad}:
	 *
	 * <pre>{@code
	 * Accounts.onReady(accounts -> accounts.registerProvider(new MyProvider()));
	 * }</pre>
	 *
	 * @param callback
	 *            invoked (possibly immediately) with the ready {@link Accounts} instance
	 */
	public static void onReady(Consumer<Accounts> callback) {
		if (callback == null) return;
		if (ready) {
			System.out.println("[Accounts.onReady] Already ready — firing callback immediately from "
					+ Thread.currentThread().getName());
			callback.accept(readyInstance);
			return;
		}
		pendingCallbacks.add(callback);
		System.out.println("[Accounts.onReady] Callback enqueued (queue size now ~" + pendingCallbacks.size() + ")");
		// Double-check: handle the case where signalReady fired between the ready-check above
		// and the enqueue — without this, the callback would sit in the queue forever.
		if (ready) {
			pendingCallbacks.remove(callback);
			System.out.println("[Accounts.onReady] Race resolved — firing callback immediately");
			callback.accept(readyInstance);
		}
	}

	/**
	 * Marks the given {@link Accounts} instance as ready and drains all pending callbacks. Must be called exactly once
	 * during bootstrap, after all providers have been registered by the launcher core (extensions may still register
	 * via the drained callbacks).
	 *
	 * @param instance
	 *            the fully initialized {@link Accounts} instance
	 */
	public static void signalReady(Accounts instance) {
		System.out.println("[Accounts.signalReady] Signalling ready — pending callbacks: " + pendingCallbacks.size());
		readyInstance = instance;
		ready = true;
		int drained = 0;
		Consumer<Accounts> cb;
		while ((cb = pendingCallbacks.poll()) != null) {
			cb.accept(instance);
			drained++;
		}
		System.out.println("[Accounts.signalReady] Drained " + drained + " callback(s). Providers now registered: "
				+ instance.getProviders().size());
		for (AccountProvider p : instance.getProviders()) {
			System.out.println("[Accounts.signalReady]   provider: " + p.getTypeId() + " / " + p.getDisplayName());
		}
	}

	// ── Instance state ────────────────────────────────────────────────────────

	/**
	 * Maps type identifier strings (uppercase) to account factory functions. Register additional types via
	 * {@link #registerType(String, BiFunction)} or {@link #registerProvider(AccountProvider)}.
	 * <p>
	 * Example (in a Minecraft downstream project):
	 *
	 * <pre>
	 * settings.getAccounts().registerProvider(new MicrosoftAccountProvider());
	 * // or, if you do not need a full provider abstraction:
	 * settings.getAccounts().registerType("MICROSOFT", MicrosoftAccount::fromConfig);
	 * </pre>
	 */
	private final Map<String, BiFunction<String, YmlConfig, Account>> deserializers = new HashMap<>();
	private final List<Account> accounts = new ArrayList<>();
	private final List<AccountProvider> providers = new ArrayList<>();

	public Accounts() {
		registerProvider(new OfflineAccountProvider());
	}

	/**
	 * Registers an {@link AccountProvider} and its deserialization factory.
	 *
	 * <p>
	 * If a provider with the same type ID is already registered, it is replaced — this allows extensions to supply a
	 * full implementation for a type that the launcher core registered as a stub (e.g. the built-in
	 * {@code MicrosoftAccountProvider} stub is automatically replaced when the Microsoft Account Extension is
	 * installed).
	 *
	 * <p>
	 * The provider will appear in the login screen's provider selector.
	 */
	public void registerProvider(AccountProvider provider) {
		System.out.println("[Accounts.registerProvider] Registering: typeId=" + provider.getTypeId() + " displayName="
				+ provider.getDisplayName() + " class=" + provider.getClass().getName());
		// Replace any existing provider with the same typeId so extension implementations
		// can override built-in stubs without duplicating the entry in the picker.
		boolean replaced = providers.removeIf(p -> p.getTypeId().equalsIgnoreCase(provider.getTypeId()));
		if (replaced) {
			System.out.println(
					"[Accounts.registerProvider] Replaced existing provider for typeId=" + provider.getTypeId());
		}
		providers.add(provider);
		registerType(provider.getTypeId(), provider::fromConfig);
	}

	/**
	 * Returns an unmodifiable view of all registered providers, in registration order.
	 */
	public List<AccountProvider> getProviders() {
		return Collections.unmodifiableList(providers);
	}

	/**
	 * Finds the provider registered for the given type ID (case-insensitive).
	 *
	 * @param typeId
	 *            the account type string (e.g. "OFFLINE", "MICROSOFT")
	 * @return the matching provider, or empty if not found
	 */
	public Optional<AccountProvider> getProvider(String typeId) {
		if (typeId == null) return Optional.empty();
		return providers.stream().filter(p -> p.getTypeId().equalsIgnoreCase(typeId)).findFirst();
	}

	/**
	 * Registers a raw deserialization factory without creating a full {@link AccountProvider}. Use this only when you
	 * do not need the provider to appear in the login UI.
	 */
	public void registerType(String typeName, BiFunction<String, YmlConfig, Account> deserializer) {
		deserializers.put(typeName.toUpperCase(), deserializer);
	}

	public void load() {
		accounts.clear();
		File dir = LauncherContext.getInstance().resolve("accounts");
		if (!dir.exists()) return;
		File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
		if (files == null) return;
		for (File file : files) {
			String id = file.getName().substring(0, file.getName().length() - 4);
			YmlConfig config = new YmlConfig(file);
			config.load();
			Account account = deserialize(id, config);
			if (account != null) accounts.add(account);
		}
	}

	private Account deserialize(String id, YmlConfig config) {
		String typeName = config.getString("type", AccountType.OFFLINE).toUpperCase();
		BiFunction<String, YmlConfig, Account> fn = deserializers.get(typeName);
		if (fn == null) return null;
		return fn.apply(id, config);
	}

	public void add(Account account) {
		accounts.add(account);
		save(account);
	}

	public void remove(String id) {
		accounts.removeIf(a -> a.getId().equals(id));
		File file = new File(LauncherContext.getInstance().resolve("accounts"), id + ".yml");
		if (file.exists()) file.delete();
	}

	public void save(Account account) {
		File dir = LauncherContext.getInstance().resolve("accounts");
		dir.mkdirs();
		YmlConfig config = new YmlConfig(new File(dir, account.getId() + ".yml"));
		account.save(config);
	}

	public List<Account> getAll() {
		return Collections.unmodifiableList(accounts);
	}

	public Optional<Account> getById(String id) {
		return accounts.stream().filter(a -> a.getId().equals(id)).findFirst();
	}

}
