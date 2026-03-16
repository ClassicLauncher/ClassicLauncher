package net.classiclauncher.launcher.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

import javax.swing.*;

import dev.utano.formatter.DefaultFormatter;
import dev.utano.formatter.PlaceHolder;
import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.settings.LauncherStyle;
import net.classiclauncher.launcher.settings.Settings;
import net.classiclauncher.launcher.ui.settings.SettingsPanel;

/**
 * A reusable widget that displays the current game's logo (or its display name as a fallback) and opens a two-step
 * provider → game picker popover when clicked.
 *
 * <h3>Selection flow</h3>
 * <ol>
 * <li><b>Provider step</b> — all registered account providers are shown as cards using each provider's icon
 * ({@link AccountProvider#getIconResourcePath()}) and display name. If only one provider is registered this step is
 * skipped.</li>
 * <li><b>Game step</b> — all games supported by the selected provider are shown as cards using the game icon resolved
 * by {@link ResourceLoader#resolveGameIconPath} (with a provider-specific override when available). If the provider
 * supports only one game (or zero games) this step is also skipped and the callback is fired immediately.</li>
 * </ol>
 *
 * <p>
 * The popover is sized to show exactly {@value #CARDS_PER_ROW} cards per row; additional items wrap to new rows
 * automatically (responsive grid).
 *
 * <p>
 * A small gear (⚙) button in the top-right corner of the widget opens the full settings dialog, giving the user access
 * to launcher settings, Java configuration, and extensions without needing an account first.
 *
 * <p>
 * The callback receives both the selected {@link AccountProvider} and {@link Game} (which may be {@code null} for
 * game-agnostic providers).
 *
 * <p>
 * Construction:
 *
 * <pre>{@code
 * // Fixed size
 * new GameSelectorWidget(340, 80, (provider, game) -> { ... });
 *
 * // Fluid (fills parent width)
 * new GameSelectorWidget((provider, game) -> { ... });
 * }</pre>
 *
 * <p>
 * Call {@link #setActiveProvider(AccountProvider)} to inform the widget which provider is currently highlighted in the
 * login form; this is used to resolve the logo image before any account has been saved.
 */
public class GameSelectorWidget extends JPanel {

	// ── Card / grid size constants ─────────────────────────────────────────────
	private static final int CARD_WIDTH = 92;
	private static final int CARD_HEIGHT = 72;
	private static final int ICON_SIZE = 24;
	private static final int CARD_GAP = 6;
	private static final int CARDS_PER_ROW = 3;

	/**
	 * Popover inner width that fits exactly {@value #CARDS_PER_ROW} cards with FlowLayout gaps, plus the popover border
	 * insets (1px line + 6px empty on each side = 14px horizontal).
	 *
	 * <p>
	 * Formula: {@code CARD_GAP + CARDS_PER_ROW * (CARD_SIZE + CARD_GAP) + 14}
	 */
	private static final int POPOVER_WIDTH = CARD_GAP + CARDS_PER_ROW * (CARD_WIDTH + CARD_GAP) + 14;

	// ── State ─────────────────────────────────────────────────────────────────
	private final BiConsumer<AccountProvider, Game> onSelected;
	private final boolean fluid;

	/**
	 * Provider currently highlighted in the login form (used for logo resolution).
	 */
	private AccountProvider activeProvider;

	/**
	 * Game explicitly chosen by the user in the two-step picker. {@code null} until a game is selected;
	 * {@link #resolveActiveGame()} falls back to the active provider's primary game when null.
	 */
	private Game selectedGame;

	/**
	 * Reference to the currently open popover overlay, or {@code null} if closed.
	 */
	private JPanel activePopover;

	// ── Constructors ─────────────────────────────────────────────────────────

	/**
	 * Creates a fluid widget whose preferred size matches its parent's width.
	 *
	 * @param onSelected
	 *            callback fired (on the EDT) after the user picks a provider and game
	 */
	public GameSelectorWidget(BiConsumer<AccountProvider, Game> onSelected) {
		this.onSelected = onSelected;
		this.fluid = true;
		init();
	}

	/**
	 * Creates a fixed-size widget.
	 *
	 * @param width
	 *            preferred width in pixels
	 * @param height
	 *            preferred height in pixels
	 * @param onSelected
	 *            callback fired (on the EDT) after the user picks a provider and game
	 */
	public GameSelectorWidget(int width, int height, BiConsumer<AccountProvider, Game> onSelected) {
		this.onSelected = onSelected;
		this.fluid = false;
		setPreferredSize(new Dimension(width, height));
		init();
	}

	// ── Public API ────────────────────────────────────────────────────────────

	/**
	 * Sets the provider that is currently active in the login form. The widget uses this to resolve the game logo
	 * before any account is persisted. Resets the explicit game selection back to the provider's primary game. Triggers
	 * a repaint.
	 */
	public void setActiveProvider(AccountProvider provider) {
		this.activeProvider = provider;
		// Preserve the user's game selection if the new provider supports it;
		// otherwise fall back to the default game if supported, then null.
		if (selectedGame != null && provider != null && !provider.getGames().contains(selectedGame)) {
			selectedGame = null;
		}
		if (selectedGame == null && provider != null) {
			Game defaultGame = LauncherContext.getInstance().getDefaultGame();
			if (defaultGame != null && provider.getGames().contains(defaultGame)) {
				selectedGame = defaultGame;
			}
		}
		repaint();
	}

	/**
	 * Returns the game most recently chosen by the user via the two-step picker, or {@code null} if no explicit
	 * selection has been made.
	 */
	public Game getSelectedGame() {
		return selectedGame;
	}

	// ── Init ──────────────────────────────────────────────────────────────────

	private void init() {
		setOpaque(false);

		addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				if (activePopover != null) {
					closePopover();
				} else {
					showSelectionPopover();
				}
			}

		});
	}

	/**
	 * Called after the widget is attached to a container so that Settings is reliably available.
	 */
	@Override
	public void addNotify() {
		super.addNotify();
		updateCursor();
	}

	private void updateCursor() {
		setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	/**
	 * Returns {@code true} when there is something meaningful to pick in the popover: either more than one provider, or
	 * a single provider with more than one game.
	 */
	private boolean isInteractive() {
		try {
			List<AccountProvider> providers = Settings.getInstance().getAccounts().getProviders();
			int withGames = 0;
			for (AccountProvider p : providers) {
				if (!p.getGames().isEmpty()) withGames++;
			}
			if (withGames == 0) return false;
			if (withGames > 1) return true;
			for (AccountProvider p : providers) {
				if (!p.getGames().isEmpty()) return p.getGames().size() > 1;
			}
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	// ── Size ─────────────────────────────────────────────────────────────────

	@Override
	public Dimension getPreferredSize() {
		if (fluid) {
			Container parent = getParent();
			if (parent != null) {
				Insets pi = parent.getInsets();
				int w = parent.getWidth() - pi.left - pi.right;
				int h = getHeight() > 0 ? getHeight() : 80;
				return new Dimension(Math.max(w, 10), h);
			}
		}
		return super.getPreferredSize();
	}

	// ── Painting ─────────────────────────────────────────────────────────────

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g.create();
		try {
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

			int w = getWidth();
			int h = getHeight();
			Game game = resolveActiveGame();
			String logoPath = game != null
					? ResourceLoader.resolveGameLogoPath(game.getGameId(),
							activeProvider != null ? activeProvider.getTypeId() : null, currentStyle())
					: null;

			if (logoPath != null) {
				paintLogo(g2, logoPath, w, h, game);
			} else {
				paintFallbackText(g2, game, w, h);
			}
		} finally {
			g2.dispose();
		}
	}

	private void paintLogo(Graphics2D g2, String logoPath, int w, int h, Game game) {
		InputStream stream = ResourceLoader.openStream(logoPath);
		if (stream == null) {
			paintFallbackText(g2, game, w, h);
			return;
		}
		try (InputStream autoClose = stream) {
			BufferedImage img;
			if (logoPath.toLowerCase().endsWith(".svg")) {
				img = ResourceLoader.renderSvg(autoClose, w, h);
			} else {
				img = javax.imageio.ImageIO.read(autoClose);
			}
			if (img == null) {
				paintFallbackText(g2, game, w, h);
				return;
			}
			// Letterbox: scale to fit while maintaining aspect ratio, centred
			double scaleX = (double) w / img.getWidth();
			double scaleY = (double) h / img.getHeight();
			double scale = Math.min(scaleX, scaleY);
			int dw = (int) (img.getWidth() * scale);
			int dh = (int) (img.getHeight() * scale);
			int dx = (w - dw) / 2;
			int dy = (h - dh) / 2;
			g2.drawImage(img, dx, dy, dw, dh, null);
		} catch (Exception e) {
			paintFallbackText(g2, game, w, h);
		}
	}

	private void paintFallbackText(Graphics2D g2, Game game, int w, int h) {
		String text = game != null ? game.getDisplayName() : LauncherContext.getInstance().getName();
		g2.setColor(Color.WHITE);
		Font font = g2.getFont().deriveFont(Font.BOLD, Math.min(h * 0.45f, 24f));
		g2.setFont(font);
		FontMetrics fm = g2.getFontMetrics();
		int tx = (w - fm.stringWidth(text)) / 2;
		int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
		g2.drawString(text, tx, ty);
	}

	// ── Game / provider resolution ────────────────────────────────────────────

	/**
	 * Resolves the game to display in priority order: 1. The explicitly selected game from the two-step picker. 2. The
	 * active provider's primary game. 3. {@link Game#resolve()} (selected account's provider game, or launcher
	 * default). 4. {@code null}.
	 */
	private Game resolveActiveGame() {
		if (selectedGame != null) return selectedGame;
		if (activeProvider != null) {
			Game g = activeProvider.getPrimaryGame();
			if (g != null) return g;
		}
		return Game.resolve();
	}

	/**
	 * Returns the active {@link LauncherStyle}, defaulting to {@code ALPHA} if Settings is unavailable.
	 */
	private static LauncherStyle currentStyle() {
		try {
			return Settings.getInstance().getLauncher().getStyle();
		} catch (Exception e) {
			return LauncherStyle.ALPHA;
		}
	}

	// ── Settings dialog ───────────────────────────────────────────────────────

	private void openSettingsDialog() {
		Window ancestor = SwingUtilities.getWindowAncestor(this);
		Frame frame = (ancestor instanceof Frame) ? (Frame) ancestor : null;
		JDialog dialog = new JDialog(frame, "Settings", true);
		dialog.setLayout(new BorderLayout());

		Settings settings = Settings.getInstance();
		SettingsPanel settingsPanel = SettingsPanel.createDefault(settings);

		dialog.setContentPane(settingsPanel);
		dialog.setSize(640, 440);
		dialog.setMinimumSize(new Dimension(640, 440));
		dialog.setLocationRelativeTo(frame);
		dialog.setVisible(true);
	}

	// ── Two-step popover ──────────────────────────────────────────────────────

	private void showSelectionPopover() {
		List<AccountProvider> providers;
		try {
			List<AccountProvider> all = Settings.getInstance().getAccounts().getProviders();
			providers = new ArrayList<>();
			for (AccountProvider p : all) {
				if (!p.getGames().isEmpty()) providers.add(p);
			}
		} catch (Exception e) {
			return;
		}
		if (providers.isEmpty()) return;

		JRootPane rootPane = (JRootPane) SwingUtilities.getAncestorOfClass(JRootPane.class, this);
		if (rootPane == null) return;

		JLayeredPane layeredPane = rootPane.getLayeredPane();
		Rectangle layerBounds = layeredPane.getBounds();
		Point widgetBottom = SwingUtilities.convertPoint(this, 0, getHeight(), layeredPane);

		int overlayTop = widgetBottom.y;
		int overlayHeight = Math.max(0, layerBounds.height - overlayTop);
		// Centre the popover horizontally in the window
		int cardX = Math.max(0, (layerBounds.width - POPOVER_WIDTH) / 2);

		// Transparent overlay — pure event catcher
		JPanel overlay = new JPanel(null);
		overlay.setBounds(0, overlayTop, layerBounds.width, overlayHeight);
		overlay.setOpaque(false);

		// ── Gear button — created once; floats at top-right of popover ─────────
		IconButton gearBtn = new IconButton("\u2699", "Settings").withSize(24, 16);
		gearBtn.addActionListener(e -> {
			closePopover();
			openSettingsDialog();
		});

		// ── Content panel — swapped between provider and game phases ───────────
		JPanel contentPanel = new JPanel(new BorderLayout(0, 0));
		contentPanel.setOpaque(false);
		contentPanel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

		// ── Popover container — null layout so gear floats independently ───────
		JPanel popoverPanel = new JPanel(null) {

			@Override
			public void doLayout() {
				Insets in = getInsets();
				int w = getWidth();
				int h = getHeight();
				contentPanel.setBounds(in.left, in.top, w - in.left - in.right, h - in.top - in.bottom);
				Dimension gs = gearBtn.getPreferredSize();
				gearBtn.setBounds(w - in.right - gs.width - 4, in.top + 4, gs.width, gs.height);
			}

			@Override
			public Dimension getPreferredSize() {
				Dimension d = contentPanel.getPreferredSize();
				Insets in = getInsets();
				return new Dimension(d.width + in.left + in.right, d.height + in.top + in.bottom);
			}

		};
		popoverPanel.setBackground(Color.WHITE);
		popoverPanel.setOpaque(true);
		popoverPanel.setBorder(BorderFactory.createLineBorder(new Color(0xCCCCCC), 1));
		popoverPanel.add(contentPanel);
		popoverPanel.add(gearBtn, 0); // z-order 0 = painted on top of contentPanel

		// Use a single-element array to allow the lambda to refer to itself
		Runnable[] showProviderPhase = new Runnable[1];

		showProviderPhase[0] = () -> {
			contentPanel.removeAll();

			if (providers.size() == 1) {
				// Skip provider step: directly enter game phase for the sole provider
				showGamePhase(providers.get(0), contentPanel, popoverPanel, overlay, layeredPane, cardX, null);
				return;
			}

			CardGrid<AccountProvider> providerGrid = buildProviderGrid(providers, provider -> {
				List<Game> games = provider.getGames();
				if (games.size() <= 1) {
					Game game = games.isEmpty() ? null : games.get(0);
					closePopover();
					fireSelected(provider, game);
				} else {
					showGamePhase(provider, contentPanel, popoverPanel, overlay, layeredPane, cardX,
							showProviderPhase[0]);
				}
			});

			contentPanel.add(providerGrid, BorderLayout.CENTER);
			resizeAndPositionPopover(popoverPanel, overlay, layeredPane, cardX);
		};

		// Clicks outside the popover close it
		overlay.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				if (!popoverPanel.getBounds().contains(e.getPoint())) {
					closePopover();
				}
			}

		});

		activePopover = overlay;
		layeredPane.add(overlay, JLayeredPane.POPUP_LAYER);

		// Trigger first phase
		showProviderPhase[0].run();
	}

	/**
	 * Replaces the content panel with the game-selection grid for {@code provider}.
	 *
	 * @param backAction
	 *            action to invoke when the user clicks "← Back", or {@code null} if there is no provider step to return
	 *            to (single-provider case)
	 */
	private void showGamePhase(AccountProvider provider, JPanel contentPanel, JPanel popoverPanel, JPanel overlay,
			JLayeredPane layeredPane, int cardX, Runnable backAction) {
		contentPanel.removeAll();

		if (backAction != null) {
			JButton backBtn = new JButton("\u2190 Back");
			backBtn.setFont(backBtn.getFont().deriveFont(Font.PLAIN, 11f));
			backBtn.setBorderPainted(false);
			backBtn.setContentAreaFilled(false);
			backBtn.setFocusPainted(false);
			backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			backBtn.setHorizontalAlignment(SwingConstants.LEFT);
			backBtn.addActionListener(e -> backAction.run());
			contentPanel.add(backBtn, BorderLayout.NORTH);
		}

		List<Game> games = provider.getGames();
		CardGrid<Game> gameGrid = buildGameGrid(games, provider, game -> {
			closePopover();
			fireSelected(provider, game);
		});
		contentPanel.add(gameGrid, BorderLayout.CENTER);
		resizeAndPositionPopover(popoverPanel, overlay, layeredPane, cardX);
	}

	/**
	 * Updates size and position of the popover inside the overlay after content changes.
	 */
	private static void resizeAndPositionPopover(JPanel popoverPanel, JPanel overlay, JLayeredPane layeredPane,
			int cardX) {
		popoverPanel.revalidate();
		Dimension pref = popoverPanel.getPreferredSize();
		int height = Math.max(pref.height, 40);
		popoverPanel.setBounds(cardX, 0, POPOVER_WIDTH, height);
		if (overlay.getComponentCount() == 0 || overlay.getComponent(0) != popoverPanel) {
			overlay.add(popoverPanel);
		}
		overlay.revalidate();
		layeredPane.repaint();
	}

	private void closePopover() {
		if (activePopover == null) return;
		JRootPane rootPane = (JRootPane) SwingUtilities.getAncestorOfClass(JRootPane.class, this);
		if (rootPane != null) {
			rootPane.getLayeredPane().remove(activePopover);
			rootPane.getLayeredPane().repaint();
		}
		activePopover = null;
	}

	/**
	 * Fires the selection callback and updates internal widget state.
	 */
	private void fireSelected(AccountProvider provider, Game game) {
		activeProvider = provider;
		selectedGame = game;
		if (game != null) {
			LauncherContext.getInstance().setDefaultGame(game);
		}
		repaint();
		updateWindowTitle(game);
		onSelected.accept(provider, game);
	}

	/**
	 * Updates the ancestor window title to reflect the selected game, or "Launcher" if none.
	 */
	private void updateWindowTitle(Game game) {
		Window w = SwingUtilities.getWindowAncestor(this);
		if (w instanceof Frame) {
			((Frame) w).setTitle(game != null ? game.getDisplayName() + " Launcher" : "Launcher");
		}
	}

	// ── Programmatic game chooser ────────────────────────────────────────────

	/**
	 * Opens a modal game-chooser dialog for the given provider. Blocks until the user picks a game, then invokes
	 * {@code onChosen}. If the provider has only one game (or zero), the callback is fired immediately with no dialog.
	 *
	 * <p>
	 * This is intended for use from {@code LoginScreen} when a provider has multiple games: after login or when playing
	 * with an existing account, the user must pick which game to launch.
	 *
	 * @param parent
	 *            the parent component for dialog positioning
	 * @param provider
	 *            the account provider whose games to choose from
	 * @param onChosen
	 *            callback invoked on the EDT with the selected game
	 */
	public static void chooseGame(Component parent, AccountProvider provider,
			java.util.function.Consumer<Game> onChosen) {
		List<Game> games = provider.getGames();
		if (games.size() <= 1) {
			onChosen.accept(games.isEmpty() ? null : games.get(0));
			return;
		}

		Window ancestor = SwingUtilities.getWindowAncestor(parent);
		Frame frame = (ancestor instanceof Frame) ? (Frame) ancestor : null;
		JDialog dialog = new JDialog(frame, "Select Game", true);
		dialog.setLayout(new BorderLayout());
		dialog.setUndecorated(true);

		JPanel panel = new JPanel(new BorderLayout(0, 0));
		panel.setBackground(Color.WHITE);
		panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xCCCCCC), 1),
				BorderFactory.createEmptyBorder(12, 12, 12, 12)));

		JLabel title = new JLabel("Select a game:");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
		panel.add(title, BorderLayout.NORTH);

		CardGrid<Game> grid = new CardGrid<>(CARD_WIDTH, CARD_HEIGHT, ICON_SIZE, CARD_GAP,
				new CardGrid.CardItemRenderer<Game>() {

					@Override
					public Icon getIcon(Game game) {
						String iconPath = ResourceLoader.resolveGameIconPath(game.getGameId(), provider.getTypeId(),
								currentStyle());
						if (iconPath == null) {
							iconPath = ResourceLoader.resolveGameIconPath(game.getGameId(), currentStyle());
						}
						return ResourceLoader.loadIcon(iconPath, ICON_SIZE, ICON_SIZE);
					}

					@Override
					public String getTitle(Game game) {
						return Optional.ofNullable(game.getDisplayName()).orElse(game.getGameId());
					}

					@Override
					public String getSubtitle(Game game) {
						return null;
					}

				}, game -> {
					dialog.dispose();
					onChosen.accept(game);
				});
		grid.setItems(games);
		panel.add(grid, BorderLayout.CENTER);

		dialog.setContentPane(panel);
		dialog.pack();
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	// ── Grid builders ─────────────────────────────────────────────────────────

	private CardGrid<AccountProvider> buildProviderGrid(List<AccountProvider> providers,
			java.util.function.Consumer<AccountProvider> onClick) {
		System.out.println(
				"[GameSelectorWidget.buildProviderGrid] Building grid for " + providers.size() + " provider(s)");
		for (AccountProvider p : providers) {
			System.out.println("[GameSelectorWidget.buildProviderGrid]   provider=" + p.getDisplayName() + " typeId="
					+ p.getTypeId() + " games=" + p.getGames().size() + " iconPath=" + p.getIconResourcePath());
		}
		CardGrid<AccountProvider> grid = new CardGrid<>(CARD_WIDTH, CARD_HEIGHT, ICON_SIZE, CARD_GAP,
				new CardGrid.CardItemRenderer<AccountProvider>() {

					@Override
					public Icon getIcon(AccountProvider provider) {
						if (provider.getGames().size() == 1) {
							System.out.println("[GameSelectorWidget.buildProviderGrid.getIcon] "
									+ provider.getDisplayName() + " has 1 game, using getIconFromGame");
							return getIconFromGame(provider.getGames().get(0), provider);
						}
						String path = provider.getIconResourcePath();
						System.out.println("[GameSelectorWidget.buildProviderGrid.getIcon] " + provider.getDisplayName()
								+ " has " + provider.getGames().size() + " games, using iconResourcePath=" + path);
						return ResourceLoader.loadIcon(path, ICON_SIZE, ICON_SIZE);
					}

					@Override
					public String getTitle(AccountProvider provider) {
						if (provider.getGames().size() == 1) {
							return provider.getGames().get(0).getDisplayName();
						}
						return provider.getDisplayName();
					}

					@Override
					public String getSubtitle(AccountProvider provider) {
						if (provider.getGames().size() == 1) {
							return provider.getDisplayName();
						}
						return DefaultFormatter.format("{game_count} games",
								new PlaceHolder("game_count", provider.getGames().size()));
					}

				}, onClick);
		grid.setItems(providers);
		return grid;
	}

	private CardGrid<Game> buildGameGrid(List<Game> games, AccountProvider provider,
			java.util.function.Consumer<Game> onClick) {
		System.out.println("[GameSelectorWidget.buildGameGrid] Building grid for " + games.size() + " game(s)"
				+ " provider=" + provider.getDisplayName());
		for (Game g : games) {
			System.out.println(
					"[GameSelectorWidget.buildGameGrid]   game=" + g.getDisplayName() + " id=" + g.getGameId());
		}
		CardGrid<Game> grid = new CardGrid<>(CARD_WIDTH, CARD_HEIGHT, ICON_SIZE, CARD_GAP,
				new CardGrid.CardItemRenderer<Game>() {

					@Override
					public Icon getIcon(Game game) {
						System.out.println("[GameSelectorWidget.buildGameGrid.getIcon] game=" + game.getDisplayName()
								+ " id=" + game.getGameId());
						return getIconFromGame(game, provider);
					}

					@Override
					public String getTitle(Game game) {
						return Optional.ofNullable(game.getDisplayName()).orElse(game.getGameId());
					}

					@Override
					public String getSubtitle(Game game) {
						return null;
					}

				}, onClick);
		grid.setItems(games);
		return grid;
	}

	public Icon getIconFromGame(Game game, AccountProvider provider) {
		String iconPath = ResourceLoader.resolveGameIconPath(game.getGameId(), provider.getTypeId(), currentStyle());
		System.out.println("[GameSelectorWidget.getIconFromGame] game=" + game.getGameId() + " provider="
				+ provider.getTypeId() + " iconPath=" + iconPath);
		if (iconPath == null) {
			iconPath = ResourceLoader.resolveGameIconPath(game.getGameId(), currentStyle());
			System.out.println("[GameSelectorWidget.getIconFromGame]   fallback (no provider) iconPath=" + iconPath);
		}
		return ResourceLoader.loadIcon(iconPath, ICON_SIZE, ICON_SIZE);
	}

}
