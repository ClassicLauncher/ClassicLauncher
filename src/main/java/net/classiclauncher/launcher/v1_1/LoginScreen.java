package net.classiclauncher.launcher.v1_1;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.UUID;

import javax.swing.*;

import dev.utano.formatter.DefaultFormatter;
import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.account.Account;
import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.account.Accounts;
import net.classiclauncher.launcher.account.AuthMethod;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.profile.LauncherVisibility;
import net.classiclauncher.launcher.profile.Profile;
import net.classiclauncher.launcher.profile.Profiles;
import net.classiclauncher.launcher.settings.AccountSettings;
import net.classiclauncher.launcher.settings.LauncherStyle;
import net.classiclauncher.launcher.settings.Settings;
import net.classiclauncher.launcher.ui.AccountComboBox;
import net.classiclauncher.launcher.ui.BackgroundPanel;
import net.classiclauncher.launcher.ui.BackgroundRenderer;
import net.classiclauncher.launcher.ui.GameSelectorWidget;

/**
 * Full-screen login panel displayed when no account is selected at startup, or when the user clicks "Switch User" from
 * the main view.
 *
 * <p>
 * Background: resolved dynamically from the active provider's {@link AccountProvider#getBackgroundRenderer} (falls back
 * to solid {@code #1E1E1E}).
 *
 * <p>
 * Content: a centred white 340-px card containing the provider selector, credential fields, and (in switch-mode)
 * controls for switching to an existing account.
 */
public class LoginScreen extends BackgroundPanel {

	// ── Dependencies ─────────────────────────────────────────────────────────
	private final JFrame frame;
	private final boolean switchMode;
	private final Runnable onLoginComplete;
	private final AccountSettings accountSettings;
	private final Accounts accounts;
	private final Profiles profiles;
	private final List<AccountProvider> allProviders;

	// ── Mutable state ─────────────────────────────────────────────────────────
	private AccountProvider selectedProvider;
	private String currentErrorMessage;

	// ── Component references ──────────────────────────────────────────────────
	private JPanel loginBox;
	private GameSelectorWidget gameLogo;
	private JPanel usernamePanel;
	private JTextField usernameField;
	private JPasswordField passwordField;
	private JPanel passwordPanel;
	private JLabel errorLabel;
	private AccountComboBox existingAccountsCombo;

	public LoginScreen(JFrame frame, boolean switchMode, Runnable onLoginComplete) {
		this.frame = frame;
		this.switchMode = switchMode;
		this.onLoginComplete = onLoginComplete;

		Settings settings = Settings.getInstance();
		this.accountSettings = settings.getAccount();
		this.accounts = settings.getAccounts();
		this.profiles = settings.getProfiles();
		this.allProviders = accounts.getProviders();
		this.selectedProvider = resolveInitialProvider();

		setLayout(new GridBagLayout());
		setOpaque(true);

		loginBox = buildLoginBox();
		add(loginBox, new GridBagConstraints());

		updateBackgroundRenderer();
	}

	/**
	 * Resolves the background renderer from the current provider and game, then applies it. Falls back to the solid
	 * {@code #1E1E1E} fill when no renderer is available.
	 */
	private void updateBackgroundRenderer() {
		BackgroundRenderer bg = null;
		if (selectedProvider != null) {
			Game game = resolveCurrentGame();
			bg = selectedProvider.getBackgroundRenderer(game, LauncherStyle.V1_1);
			selectedProvider.onGameSelected(game, LauncherStyle.V1_1);
		}
		setRenderer(bg);
	}

	/**
	 * Resolves the initial provider from the currently selected account. Falls back to the provider that owns the
	 * launcher's default game, then to any provider with games, and finally to the first registered provider.
	 */
	private AccountProvider resolveInitialProvider() {
		String selectedId = accountSettings.getSelectedAccountId();
		if (selectedId != null) {
			Account account = accounts.getById(selectedId).orElse(null);
			if (account != null) {
				AccountProvider provider = accounts.getProvider(account.getType()).orElse(null);
				if (provider != null) {
					return provider;
				}
			}
		}

		// Prefer the provider that owns the launcher's default game
		Game defaultGame = LauncherContext.getInstance().getDefaultGame();
		if (defaultGame != null) {
			for (AccountProvider p : allProviders) {
				if (p.getGames().contains(defaultGame)) {
					return p;
				}
			}
		}

		// Fall back to any provider that has games
		for (AccountProvider p : allProviders) {
			if (!p.getGames().isEmpty()) {
				return p;
			}
		}

		return allProviders.isEmpty() ? null : allProviders.get(0);
	}

	private Game resolveCurrentGame() {
		Game game = gameLogo.getSelectedGame();
		if (game == null && selectedProvider != null && !selectedProvider.getGames().isEmpty()) {
			// Prefer the user's default game if this provider supports it
			Game defaultGame = LauncherContext.getInstance().getDefaultGame();
			if (defaultGame != null && selectedProvider.getGames().contains(defaultGame)) {
				game = defaultGame;
			} else {
				game = selectedProvider.getGames().get(0);
			}
		}
		return game;
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Login box construction
	// ─────────────────────────────────────────────────────────────────────────

	private JPanel buildLoginBox() {
		JPanel box = new JPanel() {

			@Override
			public Dimension getPreferredSize() {
				Dimension d = super.getPreferredSize();
				return new Dimension(420, d.height);
			}

			@Override
			public Dimension getMaximumSize() {
				return new Dimension(420, Integer.MAX_VALUE);
			}

		};
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBackground(Color.WHITE);
		box.setOpaque(true);
		// No inner horizontal padding — logo fills edge-to-edge up to the line border.
		// All content below the logo lives inside a padded sub-panel.
		box.setBorder(BorderFactory.createLineBorder(new Color(0xCCCCCC), 1));

		List<Account> existingAccounts = accounts.getAll();

		box.add(Box.createVerticalStrut(8));

		// 1. Game logo / selector widget — full-width, no horizontal padding
		gameLogo = new GameSelectorWidget(170, 40, (provider, game) -> {
			selectedProvider = provider;
			updateProviderUI();
		});
		gameLogo.setActiveProvider(selectedProvider);
		gameLogo.setAlignmentX(Component.LEFT_ALIGNMENT);
		box.add(gameLogo);

		// 2. Padded content panel — contains everything below the logo
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setOpaque(false);
		content.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.setBorder(BorderFactory.createEmptyBorder(16, 8, 1, 8));

		// 3. Existing account switcher (switch mode + accounts > 0)
		if (switchMode && !existingAccounts.isEmpty()) {
			content.add(buildExistingAccountsRow(existingAccounts));
			content.add(Box.createVerticalStrut(12));
		}

		// 4. Username field (only shown for FORM-based providers)
		JLabel usernameLabel = new JLabel("Email Address or Username:");
		usernameLabel.setFont(usernameLabel.getFont().deriveFont(Font.BOLD));
		usernameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		usernameField = new JTextField();
		usernameField.setAlignmentX(Component.LEFT_ALIGNMENT);
		usernameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, usernameField.getPreferredSize().height));

		usernamePanel = new JPanel();
		usernamePanel.setLayout(new BoxLayout(usernamePanel, BoxLayout.Y_AXIS));
		usernamePanel.setOpaque(false);
		usernamePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		usernamePanel.add(usernameLabel);
		usernamePanel.add(Box.createVerticalStrut(4));
		usernamePanel.add(usernameField);
		usernamePanel.add(Box.createVerticalStrut(8));
		usernamePanel.setVisible(selectedProvider == null || selectedProvider.getAuthMethod() == AuthMethod.FORM);
		content.add(usernamePanel);

		// 5. Password field (hidden when provider does not require a password)
		JLabel passwordLabel = new JLabel("Password:");
		passwordLabel.setFont(usernameLabel.getFont().deriveFont(Font.BOLD));
		passwordLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		passwordField = new JPasswordField();
		passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
		passwordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, passwordField.getPreferredSize().height));

		passwordPanel = new JPanel();
		passwordPanel.setLayout(new BoxLayout(passwordPanel, BoxLayout.Y_AXIS));
		passwordPanel.setOpaque(false);
		passwordPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		passwordPanel.add(passwordLabel);
		passwordPanel.add(Box.createVerticalStrut(4));
		passwordPanel.add(passwordField);
		passwordPanel.add(Box.createVerticalStrut(8));
		passwordPanel.setVisible(selectedProvider != null && selectedProvider.requiresPassword());
		content.add(passwordPanel);

		// 6. Error label (hidden initially; wraps long messages, click-to-copy)
		errorLabel = new JLabel();
		errorLabel.setForeground(new Color(0xCC0000));
		errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		errorLabel.setVisible(false);
		errorLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		errorLabel.setToolTipText("Click to copy");
		errorLabel.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				if (currentErrorMessage != null && !currentErrorMessage.isEmpty()) {
					Toolkit.getDefaultToolkit().getSystemClipboard()
							.setContents(new StringSelection(currentErrorMessage), null);
				}
			}

		});
		content.add(errorLabel);
		content.add(Box.createVerticalStrut(8));

		// 7. Login / register buttons
		content.add(buildLoginButtonRow());

		box.add(content);
		return box;
	}

	/**
	 * Builds the "Switch to existing account" section shown in switch mode.
	 */
	private JPanel buildExistingAccountsRow(List<Account> existingAccounts) {
		existingAccountsCombo = new AccountComboBox(allProviders);
		for (Account acc : existingAccounts) {
			existingAccountsCombo.addItem(acc);
		}

		// Pre-select the currently active account
		String selectedId = accountSettings.getSelectedAccountId();
		if (selectedId != null) {
			for (int i = 0; i < existingAccountsCombo.getItemCount(); i++) {
				Account item = existingAccountsCombo.getItemAt(i);
				if (item != null && selectedId.equals(item.getId())) {
					existingAccountsCombo.setSelectedIndex(i);
					break;
				}
			}
		}

		// Update background and provider when the user picks a different existing account
		existingAccountsCombo.addItemListener(e -> {
			if (e.getStateChange() != java.awt.event.ItemEvent.SELECTED) return;
			Account selected = (Account) existingAccountsCombo.getSelectedItem();
			if (selected == null) return;
			AccountProvider provider = accounts.getProvider(selected.getType()).orElse(null);
			if (provider != null && provider != selectedProvider) {
				selectedProvider = provider;
				gameLogo.setActiveProvider(selectedProvider);
				updateProviderUI();
			}
		});

		JButton playBtn = new JButton("Play");
		playBtn.addActionListener(e -> {
			Account selected = (Account) existingAccountsCombo.getSelectedItem();
			if (selected == null) return;
			accountSettings.setSelectedAccountId(selected.getId());
			AccountProvider provider = accounts.getProvider(selected.getType()).orElse(null);
			if (provider != null && provider.getGames().size() > 1) {
				// Skip chooser if a game is already selected and this provider supports it
				Game defaultGame = LauncherContext.getInstance().getDefaultGame();
				if (defaultGame != null && provider.getGames().contains(defaultGame)) {
					onLoginComplete.run();
				} else {
					GameSelectorWidget.chooseGame(this, provider, game -> {
						if (game != null) {
							LauncherContext.getInstance().setDefaultGame(game);
						}
						onLoginComplete.run();
					});
				}
			} else {
				if (provider != null && !provider.getGames().isEmpty()) {
					LauncherContext.getInstance().setDefaultGame(provider.getGames().get(0));
				}
				onLoginComplete.run();
			}
		});

		int loggedInAccounts = existingAccounts.size();
		JLabel switchLabel = new JLabel("<html>" + DefaultFormatter.format(
				"You've already logged in as % different user%.<br>"
						+ "You may use one of these accounts and skip authentication.",
				loggedInAccounts, loggedInAccounts != 1 ? 's' : "") + "</html>");
		switchLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel existingUserLabel = new JLabel("Existing User:");
		existingUserLabel.setFont(existingUserLabel.getFont().deriveFont(Font.BOLD));
		existingUserLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel comboRow = new JPanel(new BorderLayout(2, 0));
		comboRow.setOpaque(false);
		comboRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		comboRow.add(existingAccountsCombo, BorderLayout.CENTER);
		comboRow.add(playBtn, BorderLayout.EAST);

		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setOpaque(false);
		section.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(switchLabel);
		section.add(Box.createVerticalStrut(4));
		section.add(existingUserLabel);
		section.add(Box.createVerticalStrut(4));
		section.add(comboRow);

		JLabel alternativeLabel = new JLabel("<html>Alternatively, log in with a new account below:</html>");
		alternativeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		section.add(alternativeLabel);

		return section;
	}

	/**
	 * Builds the Log In / Register button row — full-width, 50/50 split, 8 px gap.
	 */
	private JPanel buildLoginButtonRow() {
		JButton registerBtn = new JButton("Register");
		registerBtn.addActionListener(e -> handleRegister());

		JButton loginBtn = new JButton("Log In");
		loginBtn.addActionListener(e -> handleLogin());

		// GridLayout gives both buttons equal width; hgap=8 is the inter-button gap.
		JPanel row = new JPanel(new GridLayout(1, 2, 8, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
		row.add(registerBtn);
		row.add(loginBtn);
		return row;
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Login logic
	// ─────────────────────────────────────────────────────────────────────────

	private void handleLogin() {
		if (selectedProvider == null) {
			showError("No account provider selected.");
			return;
		}
		if (selectedProvider.getAuthMethod() == AuthMethod.FORM) {
			String username = usernameField.getText().trim();
			if (username.isEmpty()) {
				showError("Username cannot be empty.");
				return;
			}
			char[] password = passwordField.getPassword();
			try {
				Account account = selectedProvider.createFromForm(username, password);
				finishLogin(account);
			} catch (Exception ex) {
				showError("Login failed: " + ex.getMessage());
			}
		} else {
			selectedProvider.startBrowserAuth(account -> SwingUtilities.invokeLater(() -> finishLogin(account)),
					error -> SwingUtilities.invokeLater(() -> showError(error)));
		}
	}

	/**
	 * Register: for browser providers this opens the same auth flow; for form providers it creates the account.
	 */
	private void handleRegister() {
		handleLogin();
	}

	private void finishLogin(Account account) {
		accounts.add(account);
		accountSettings.setSelectedAccountId(account.getId());
		promptGameThenComplete(account);
	}

	/**
	 * If the provider for this account supports more than one game, opens a game chooser before proceeding. Otherwise
	 * proceeds immediately with the provider's sole game (or the launcher default).
	 */
	private void promptGameThenComplete(Account account) {
		AccountProvider provider = accounts.getProvider(account.getType()).orElse(selectedProvider);
		if (provider != null && provider.getGames().size() > 1) {
			// Skip chooser if a game is already selected and this provider supports it
			Game defaultGame = LauncherContext.getInstance().getDefaultGame();
			if (defaultGame != null && provider.getGames().contains(defaultGame)) {
				createDefaultProfileIfNeeded(account, defaultGame);
				onLoginComplete.run();
			} else {
				GameSelectorWidget.chooseGame(this, provider, game -> {
					if (game != null) {
						LauncherContext.getInstance().setDefaultGame(game);
					}
					createDefaultProfileIfNeeded(account, game);
					onLoginComplete.run();
				});
			}
		} else {
			Game game = resolveCurrentGame();
			if (game != null) {
				LauncherContext.getInstance().setDefaultGame(game);
			}
			createDefaultProfileIfNeeded(account, game);
			onLoginComplete.run();
		}
	}

	/**
	 * Creates a default profile named after the account if no profiles exist yet for this game. This gives first-time
	 * users a playable configuration without requiring manual setup.
	 */
	private void createDefaultProfileIfNeeded(Account account, Game game) {
		String gid = game != null ? game.getGameId() : null;
		// Check if a profile already exists for this game
		for (Profile p : profiles.getAll()) {
			if (gid == null || gid.equals(p.getGameId())) return;
		}
		Profile profile = Profile.builder().id(UUID.randomUUID().toString()).name(account.getDisplayName()).gameId(gid)
				.versionId(null).accountId(account.getId()).autoCrashReport(true)
				.launcherVisibility(LauncherVisibility.CLOSE_LAUNCHER).enableSnapshots(false).enableBetaVersions(false)
				.enableAlphaVersions(false).build();
		profiles.add(profile);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// UI helpers
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Updates the logo, credential field visibility, and revalidates the login box after a provider change.
	 */
	private void updateProviderUI() {
		boolean isForm = selectedProvider != null && selectedProvider.getAuthMethod() == AuthMethod.FORM;
		usernamePanel.setVisible(isForm);
		passwordPanel.setVisible(isForm && selectedProvider.requiresPassword());
		updateBackgroundRenderer();
		loginBox.revalidate();
		loginBox.repaint();
	}

	private void showError(String message) {
		System.err.println("[LoginScreen] Auth error: " + message);
		currentErrorMessage = message;
		// Use HTML so the JLabel wraps long messages within the card width.
		// 388px = 420px card − 2×8px content padding − 2×8px box padding.
		errorLabel.setText("<html><div style='width:388px'>" + escapeHtml(message) + "</div></html>");
		errorLabel.setVisible(true);
		loginBox.revalidate();
		loginBox.repaint();
	}

	private static String escapeHtml(String text) {
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
				.replace("\n", "<br>");
	}

}
