package net.classiclauncher.launcher.alpha;

import java.awt.*;

import javax.swing.*;

import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.settings.LauncherStyle;
import net.classiclauncher.launcher.ui.BackgroundPanel;
import net.classiclauncher.launcher.ui.BackgroundRenderer;
import net.classiclauncher.launcher.ui.GameSelectorWidget;
import net.classiclauncher.launcher.ui.font.LauncherFontDefaults;

public class LauncherAlpha {

	/**
	 * The currently selected provider (may be null if Settings is not initialised).
	 */
	private AccountProvider selectedProvider;
	private BackgroundPanel backgroundPanel;

	private static void applySystemLookAndFeel() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception ignored) {
			// Fall back to default L&F silently
		}
	}

	public void show() {
		createAndShowGUI();
	}

	private void createAndShowGUI() {
		applySystemLookAndFeel();
		LauncherFontDefaults.forStyle(LauncherStyle.ALPHA).applyToSwing();

		JFrame frame = new JFrame("Launcher - Alpha");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(853, 480);
		frame.setMinimumSize(new Dimension(853, 480));
		frame.setLocationRelativeTo(null);

		backgroundPanel = new BackgroundPanel();
		backgroundPanel.setLayout(new GridBagLayout());

		// Logo / game selector spanning the full width above the login panel
		GameSelectorWidget gameLogo = new GameSelectorWidget(853, 100, (provider, game) -> {
			selectedProvider = provider;
			updateBackground();
		});
		GridBagConstraints logoGbc = new GridBagConstraints();
		logoGbc.gridx = 0;
		logoGbc.gridy = 0;
		logoGbc.fill = GridBagConstraints.HORIZONTAL;
		logoGbc.weightx = 1.0;
		backgroundPanel.add(gameLogo, logoGbc);

		GridBagConstraints loginGbc = new GridBagConstraints();
		loginGbc.gridx = 0;
		loginGbc.gridy = 1;
		backgroundPanel.add(new LoginPanel(), loginGbc);

		frame.setContentPane(backgroundPanel);
		frame.setVisible(true);

		updateBackground();
	}

	/**
	 * Resolves the background renderer from the current provider and applies it.
	 */
	private void updateBackground() {
		BackgroundRenderer renderer = null;
		if (selectedProvider != null) {
			Game game = selectedProvider.getPrimaryGame();
			renderer = selectedProvider.getBackgroundRenderer(game, LauncherStyle.ALPHA);
			selectedProvider.onGameSelected(game, LauncherStyle.ALPHA);
		}
		backgroundPanel.setRenderer(renderer);
	}

}
