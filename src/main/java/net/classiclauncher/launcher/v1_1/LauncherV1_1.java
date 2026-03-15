package net.classiclauncher.launcher.v1_1;

import java.awt.*;
import java.util.Optional;

import javax.swing.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.account.Account;
import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.settings.LauncherStyle;
import net.classiclauncher.launcher.settings.Settings;
import net.classiclauncher.launcher.ui.font.LauncherFontDefaults;
import net.classiclauncher.launcher.ui.settings.ExtensionSettingsPanel;
import net.classiclauncher.launcher.ui.settings.JavaSettingsPanel;
import net.classiclauncher.launcher.ui.settings.LauncherSettingsPanel;
import net.classiclauncher.launcher.ui.settings.SettingsPanel;
import net.classiclauncher.launcher.ui.update.UpdateSettingsPanel;
import net.classiclauncher.launcher.v1_1.tabs.LauncherLogTab;
import net.classiclauncher.launcher.v1_1.tabs.ProfileEditorTab;
import net.classiclauncher.launcher.v1_1.tabs.UpdateNotesTab;

/**
 * 2013-style tabbed launcher UI (Minecraft Launcher 1.6.x aesthetic).
 *
 * <p>
 * Tabs: Update Notes · Launcher Log · Profile Editor · Settings
 *
 * <p>
 * Startup flow:
 * <ol>
 * <li>Frame created and shown.</li>
 * <li>No selected account → login screen (dark tiled background).</li>
 * <li>After login → main tabbed view (plain system-colour panel, no tiled background).</li>
 * <li>"Switch User" → login screen in switch mode.</li>
 * </ol>
 */
public class LauncherV1_1 {

	private static final Logger log = LogManager.getLogger(LauncherV1_1.class);

	private JFrame frame;
	private JPanel mainPanel;
	private BottomBar bottomBar;
	private ProfileEditorTab profileEditorTab;

	public void show() {
		SwingUtilities.invokeLater(this::createAndShowGUI);
	}

	private void createAndShowGUI() {
		applySystemLookAndFeel();
		LauncherFontDefaults.forStyle(LauncherStyle.V1_1).applyToSwing();

		frame = new JFrame("Launcher");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(885, 541);
		frame.setMinimumSize(new Dimension(885, 541));
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);

		String selectedAccountId = Settings.getInstance().getAccount().getSelectedAccountId();
		if (selectedAccountId == null) {
			showLoginScreen(false);
		} else {
			showMainView();
		}
	}

	private void showMainView() {
		buildMainView();
		frame.setContentPane(mainPanel);
		frame.revalidate();
		frame.repaint();
		bottomBar.refreshProfiles();
		bottomBar.refreshAccountInfo();
		refreshSelectedAccountIdentity();
		fireGameSelectedForActiveAccount();
	}

	/**
	 * Fires the {@link AccountProvider#onGameSelected} hook for the currently selected account's provider and game.
	 * Called when the main view is shown (on startup with an existing account, or after login).
	 */
	private void fireGameSelectedForActiveAccount() {
		Settings settings = Settings.getInstance();
		String accountId = settings.getAccount().getSelectedAccountId();
		if (accountId == null) return;

		Optional<Account> optAccount = settings.getAccounts().getById(accountId);
		if (!optAccount.isPresent()) return;

		Optional<AccountProvider> optProvider = settings.getAccounts().getProvider(optAccount.get().getType());
		if (!optProvider.isPresent()) return;

		AccountProvider provider = optProvider.get();
		Game game = provider.getPrimaryGame();
		if (game == null) {
			game = LauncherContext.getInstance().getDefaultGame();
		}
		provider.onGameSelected(game, LauncherStyle.V1_1);
	}

	private void buildMainView() {
		Settings settings = Settings.getInstance();

		UpdateNotesTab updateNotesTab = UpdateNotesTab.create(resolveUpdateNotesUrl());
		LauncherLogTab launcherLogTab = new LauncherLogTab();
		profileEditorTab = new ProfileEditorTab();

		SettingsPanel settingsPanel = new SettingsPanel();
		settingsPanel.addSection("Launcher", new LauncherSettingsPanel(settings.getLauncher()));
		settingsPanel.addSection("Java", new JavaSettingsPanel(settings.getJavaManager()));
		settingsPanel.addSection("Extensions", new ExtensionSettingsPanel(settings.getExtensions()));
		settingsPanel.addSection("Updates",
				new UpdateSettingsPanel(settings.getLauncher(), settings.getReleaseSource()));

		JTabbedPane tabbedPane = new JTabbedPane();
		// Remove the content-area border/insets that Swing (including macOS Aqua) adds around
		// the tabbed pane's content region so tabs stretch edge-to-edge.
		tabbedPane.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {

			@Override
			protected Insets getContentBorderInsets(int tabPlacement) {
				return new Insets(0, 0, 0, 0);
			}

			@Override
			protected Insets getTabAreaInsets(int tabPlacement) {
				return new Insets(3, 2, 0, 2);
			}

		});
		tabbedPane.addTab("Update Notes", updateNotesTab);
		tabbedPane.addTab("Launcher Log", launcherLogTab);
		tabbedPane.addTab("Profile Editor", profileEditorTab);
		tabbedPane.addTab("Settings", settingsPanel);

		bottomBar = new BottomBar(frame, profileEditorTab, launcherLogTab, () -> showLoginScreen(true),
				() -> tabbedPane.setSelectedComponent(launcherLogTab));

		mainPanel = new JPanel(new BorderLayout());
		mainPanel.add(tabbedPane, BorderLayout.CENTER);
		mainPanel.add(bottomBar, BorderLayout.SOUTH);
	}

	/**
	 * Silently refreshes the selected account's identity (username, UUID, tokens) in the background via its provider's
	 * {@link AccountProvider#refreshIdentityAsync} hook.
	 *
	 * <p>
	 * On success: saves the updated account to disk and refreshes the bottom bar. On failure: logs to stderr only — the
	 * cached username from disk continues to be shown. Either way the UI is never blocked.
	 */
	private void refreshSelectedAccountIdentity() {
		Settings settings = Settings.getInstance();
		String id = settings.getAccount().getSelectedAccountId();
		if (id == null) return;

		Optional<Account> optAccount = settings.getAccounts().getById(id);
		if (!optAccount.isPresent()) return;
		Account account = optAccount.get();

		Optional<AccountProvider> optProvider = settings.getAccounts().getProvider(account.getType());
		if (!optProvider.isPresent()) return;

		optProvider.get().refreshIdentityAsync(account, updated -> {
			settings.getAccounts().save(updated);
			SwingUtilities.invokeLater(() -> bottomBar.refreshAccountInfo());
		}, error -> log.warn("[LauncherV1_1] Background identity refresh failed: {}", error));
	}

	/**
	 * Resolves the update-notes URL: selected account's provider URL takes precedence, falling back to
	 * {@code settings.yml}.
	 */
	private String resolveUpdateNotesUrl() {
		Settings settings = Settings.getInstance();
		String accountId = settings.getAccount().getSelectedAccountId();
		if (accountId != null) {
			Optional<Account> acc = settings.getAccounts().getById(accountId);
			if (acc.isPresent()) {
				Optional<AccountProvider> prov = settings.getAccounts().getProvider(acc.get().getType());
				if (prov.isPresent() && prov.get().getUpdateNotesUrl() != null) {
					return prov.get().getUpdateNotesUrl();
				}
			}
		}
		return settings.getLauncher().getUpdateNotesUrl();
	}

	private void showLoginScreen(boolean switchMode) {
		LoginScreen loginScreen = new LoginScreen(frame, switchMode, this::showMainView);
		frame.setContentPane(loginScreen);
		frame.revalidate();
		frame.repaint();
	}

	private static void applySystemLookAndFeel() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {
			// Fall back to default L&F silently
		}
	}

}
