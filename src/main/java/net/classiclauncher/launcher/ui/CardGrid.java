package net.classiclauncher.launcher.ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.*;
import javax.swing.border.Border;

/**
 * Generic FlowLayout grid of fixed-size, hover-able cards.
 *
 * <p>
 * Each card is a square panel ({@code cardSize × cardSize}) using {@link BoxLayout#Y_AXIS}, displaying an optional
 * icon, a bold title, and a smaller subtitle. Cards highlight with a blue border and pale-blue background on hover. An
 * optional row of action buttons is shown at the bottom of each card when the renderer returns a non-empty list.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * CardGrid<MyItem> grid = new CardGrid<>(80, 24, 8, new CardGrid.CardItemRenderer<MyItem>() {
 *
 * 	public Icon getIcon(MyItem item) {
 * 		return myIcon;
 * 	}
 *
 * 	public String getTitle(MyItem item) {
 * 		return item.getName();
 * 	}
 *
 * 	public String getSubtitle(MyItem item) {
 * 		return item.getDescription();
 * 	}
 *
 * }, item -> System.out.println("clicked: " + item));
 *
 * grid.setItems(myList);
 * }</pre>
 *
 * @param <T>
 *            the data type rendered in each card
 */
public class CardGrid<T> extends JPanel {

	// ── Constants ─────────────────────────────────────────────────────────────

	private static final Color COLOR_DEFAULT_BG = Color.WHITE;
	private static final Color COLOR_HOVER_BG = new Color(0xF0F6FF);
	private static final Color COLOR_BORDER = new Color(0xDDDDDD);
	private static final Color COLOR_HOVER_BORDER = new Color(0x4A90D9);
	private static final Color COLOR_TITLE = new Color(0x333333);
	private static final Color COLOR_SUBTITLE = new Color(0x555555);

	// ── Configuration ─────────────────────────────────────────────────────────

	private final int cardWidth;
	private final int cardHeight;
	private final int iconSize;
	private final CardItemRenderer<T> renderer;
	private final Consumer<T> onClick;

	// ── Constructor ───────────────────────────────────────────────────────────

	/**
	 * @param cardWidth
	 *            width of each card in pixels
	 * @param cardHeight
	 *            height of each card in pixels
	 * @param iconSize
	 *            size of the icon rendered at the top of each card in pixels
	 * @param gap
	 *            horizontal and vertical gap between cards in pixels
	 * @param renderer
	 *            supplies icon, title, subtitle and optional action buttons for each item
	 * @param onClick
	 *            called (on the EDT) when a card is clicked; the item is the argument
	 */
	public CardGrid(int cardWidth, int cardHeight, int iconSize, int gap, CardItemRenderer<T> renderer,
			Consumer<T> onClick) {
		this.cardWidth = cardWidth;
		this.cardHeight = cardHeight;
		this.iconSize = iconSize;
		this.renderer = renderer;
		this.onClick = onClick;
		setLayout(new FlowLayout(FlowLayout.LEFT, gap, gap));
		setBackground(Color.WHITE);
		setOpaque(true);
	}

	/**
	 * @param cardSize
	 *            size (width = height) of each card in pixels
	 * @param iconSize
	 *            size of the icon rendered at the top of each card in pixels
	 * @param gap
	 *            horizontal and vertical gap between cards in pixels
	 * @param renderer
	 *            supplies icon, title, subtitle and optional action buttons for each item
	 * @param onClick
	 *            called (on the EDT) when a card is clicked; the item is the argument
	 */
	public CardGrid(int cardSize, int iconSize, int gap, CardItemRenderer<T> renderer, Consumer<T> onClick) {
		this(cardSize, cardSize, iconSize, gap, renderer, onClick);
	}

	// ── Public API ────────────────────────────────────────────────────────────

	/**
	 * Replaces the grid contents with cards built from {@code items}. Revalidates and repaints the component.
	 *
	 * @param items
	 *            the items to display; must not be {@code null}
	 */
	public void setItems(List<T> items) {
		removeAll();
		if (items != null) {
			for (T item : items) {
				add(buildItemCard(item));
			}
		}
		revalidate();
		repaint();
	}

	// ── Card building ─────────────────────────────────────────────────────────

	private JPanel buildItemCard(T item) {
		Border defaultBorder = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(COLOR_BORDER, 1),
				BorderFactory.createEmptyBorder(5, 5, 5, 5));
		Border hoverBorder = BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(COLOR_HOVER_BORDER, 2),
				BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setPreferredSize(new Dimension(cardWidth, cardHeight));
		card.setMaximumSize(new Dimension(cardWidth, cardHeight));
		card.setBackground(COLOR_DEFAULT_BG);
		card.setOpaque(true);
		card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		card.setBorder(defaultBorder);

		// Icon
		Icon icon = renderer.getIcon(item);
		if (icon != null) {
			card.add(Box.createVerticalGlue());
			JLabel iconLabel = new JLabel(icon);
			iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			card.add(iconLabel);
			card.add(Box.createVerticalStrut(4));
		} else {
			card.add(Box.createVerticalGlue());
		}

		// Title
		String title = renderer.getTitle(item);
		if (title != null && !title.isEmpty()) {
			JLabel titleLabel = new JLabel("<html><div style='text-align: center;'>" + title + "</div></html>");
			titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 9f));
			titleLabel.setForeground(COLOR_TITLE);
			titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
			titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			card.add(titleLabel);
		}

		// Subtitle
		String subtitle = renderer.getSubtitle(item);
		if (subtitle != null && !subtitle.isEmpty()) {
			JLabel subtitleLabel = new JLabel(subtitle);
			subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 8f));
			subtitleLabel.setForeground(COLOR_SUBTITLE);
			subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
			subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
			card.add(subtitleLabel);
		}

		// Action buttons
		List<JButton> buttons = renderer.getActionButtons(item);
		if (!buttons.isEmpty()) {
			JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 2));
			buttonPanel.setOpaque(false);
			for (JButton button : buttons) {
				button.setFont(button.getFont().deriveFont(Font.PLAIN, 8f));
				buttonPanel.add(button);
			}
			card.add(Box.createVerticalStrut(2));
			card.add(buttonPanel);
		}

		card.add(Box.createVerticalGlue());

		// Hover and click behaviour
		card.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseEntered(MouseEvent e) {
				card.setBackground(COLOR_HOVER_BG);
				card.setBorder(hoverBorder);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				card.setBackground(COLOR_DEFAULT_BG);
				card.setBorder(defaultBorder);
			}

			@Override
			public void mouseClicked(MouseEvent e) {
				if (onClick != null) onClick.accept(item);
			}

		});

		return card;
	}

	// ── Renderer interface ────────────────────────────────────────────────────

	/**
	 * Provides the visual content for each item displayed in a {@link CardGrid}.
	 *
	 * @param <T>
	 *            the item type
	 */
	public interface CardItemRenderer<T> {

		/**
		 * Returns the icon to render at the top of the card, or {@code null} for no icon.
		 */
		Icon getIcon(T item);

		/**
		 * Returns the bold title text shown below the icon.
		 */
		String getTitle(T item);

		/**
		 * Returns the smaller subtitle text shown below the title.
		 */
		String getSubtitle(T item);

		/**
		 * Returns action buttons rendered at the bottom of the card. Override to supply Enable/Disable, Update, Remove,
		 * etc. The default implementation returns an empty list (no buttons).
		 */
		default List<JButton> getActionButtons(T item) {
			return Collections.emptyList();
		}

	}

}
