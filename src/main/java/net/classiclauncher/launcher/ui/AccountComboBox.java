package net.classiclauncher.launcher.ui;

import java.awt.*;
import java.util.List;

import javax.swing.*;

import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.account.Account;
import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.account.Accounts;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.settings.LauncherStyle;
import net.classiclauncher.launcher.settings.Settings;

/**
 * A {@link JComboBox} for {@link Account} objects that renders each item with:
 * <ul>
 * <li>A 16×16 game icon (SVG preferred; falls back to the provider's own icon)</li>
 * <li>The account's display name</li>
 * <li>An em-dash followed by the game's display name (resolved from the owning provider)</li>
 * <li>The provider's display name in parentheses</li>
 * </ul>
 *
 * <p>
 * The same renderer is used for both the collapsed trigger and the open dropdown, so icon and label are consistent in
 * both states.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * AccountComboBox combo = new AccountComboBox(accounts.getProviders());
 * for (Account acc : accounts.getAll())
 * 	combo.addItem(acc);
 * }</pre>
 *
 * @param providers
 *            the full list of registered providers, used to resolve the game and icon for each account entry — pass
 *            {@link Accounts#getProviders()}
 */
public class AccountComboBox extends JComboBox<Account> {

	public AccountComboBox(List<AccountProvider> providers) {
		setRenderer(new DefaultListCellRenderer() {

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (!(value instanceof Account)) {
					return this;
				}
				Account account = (Account) value;

				// Resolve the provider that owns this account type
				AccountProvider provider = null;
				for (AccountProvider p : providers) {
					if (p.getTypeId().equals(account.getType())) {
						provider = p;
						break;
					}
				}

				// Resolve the game: provider → launcher-wide default → null
				Game game = provider != null ? provider.getPrimaryGame() : null;
				if (game == null) {
					game = LauncherContext.getInstance().getDefaultGame();
				}

				// Label: "AccountName — GameName (ProviderName)"
				StringBuilder text = new StringBuilder(account.getDisplayName());
				if (game != null) {
					text.append(" — ").append(game.getDisplayName());
				}
				if (provider != null) {
					text.append(" (").append(provider.getDisplayName()).append(")");
				}
				setText(text.toString());

				// 16×16 game icon; fall back to provider icon, then no icon
				LauncherStyle style;
				try {
					style = Settings.getInstance().getLauncher().getStyle();
				} catch (Exception ex) {
					style = LauncherStyle.ALPHA;
				}
				String iconPath = game != null
						? ResourceLoader.resolveGameIconPath(game.getGameId(),
								provider != null ? provider.getTypeId() : null, style)
						: null;
				System.out.println("[AccountComboBox] account=" + account.getDisplayName() + " game="
						+ (game != null ? game.getGameId() : "null") + " resolvedIconPath=" + iconPath);
				if (iconPath == null && provider != null) {
					iconPath = provider.getIconResourcePath();
					System.out.println("[AccountComboBox]   falling back to provider icon: " + iconPath);
				}
				setIcon(ResourceLoader.loadIcon(iconPath, 16, 16));
				return this;
			}

		});
	}

}
