package net.classiclauncher.launcher.ui.settings;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

/**
 * Abstract base class for settings pages displayed inside a {@link SettingsPanel}.
 *
 * <p>
 * Each page has a unique {@code id}, a {@code sidebarLabel} shown in the left-hand navigation list, and a
 * {@code priority} that determines display order (lower values appear first).
 *
 * <p>
 * Subclasses call {@link #buildPage(PageLayout)} at the end of their constructor to assemble the standard three-zone
 * layout: header (title + optional actions), body (fills remaining space), and footer (action buttons + status label).
 *
 * <p>
 * Example:
 *
 * <pre>{@code
 * public class MySettingsPage extends SettingsPage {
 *
 * 	public MySettingsPage() {
 * 		super("my-page", "My Page", 50);
 * 		JPanel body = new JPanel();
 * 		// ... populate body ...
 * 		JButton applyBtn = new JButton("Apply");
 * 		buildPage(new PageLayout().title("My Page Settings").body(body).footerAction(applyBtn));
 * 	}
 *
 * }
 * }</pre>
 */
public abstract class SettingsPage extends JPanel {

	private final String id;
	private final String sidebarLabel;
	private final int priority;
	private JLabel statusLabel;

	/**
	 * @param id
	 *            unique identifier for this page (used for lookup and card-layout key)
	 * @param sidebarLabel
	 *            text displayed in the sidebar navigation list
	 * @param priority
	 *            display order — lower values appear first; pages with equal priority preserve insertion order
	 */
	protected SettingsPage(String id, String sidebarLabel, int priority) {
		this.id = id;
		this.sidebarLabel = sidebarLabel;
		this.priority = priority;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
	}

	/**
	 * Returns the unique page identifier.
	 */
	public String getId() {
		return id;
	}

	/**
	 * Returns the label shown in the sidebar navigation list.
	 */
	public String getSidebarLabel() {
		return sidebarLabel;
	}

	/**
	 * Returns the display-order priority. Lower values appear first in the sidebar.
	 */
	public int getPriority() {
		return priority;
	}

	/**
	 * Assembles the page layout from the provided {@link PageLayout} specification. Call this at the end of the
	 * subclass constructor after all components have been created.
	 *
	 * <p>
	 * Builds a three-zone layout:
	 * <ul>
	 * <li><b>Header (NORTH)</b> — title label (bold 14f) at the left, optional action components at the right.</li>
	 * <li><b>Body (CENTER)</b> — the main content component, fills all remaining space.</li>
	 * <li><b>Footer (SOUTH)</b> — action buttons left-aligned, status label right-aligned. Omitted when there are no
	 * actions and {@link PageLayout#noStatus()} was set.</li>
	 * </ul>
	 */
	protected void buildPage(PageLayout layout) {
		// ── Header (title bar + separator) ───────────────────────────────────
		JPanel headerBar = new JPanel(new BorderLayout());
		headerBar.setOpaque(false);

		String titleText = layout.title != null ? layout.title : sidebarLabel;
		JLabel titleLabel = new JLabel(titleText);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
		titleLabel.setVerticalAlignment(SwingConstants.BOTTOM);
		headerBar.add(titleLabel, BorderLayout.CENTER);

		if (!layout.headerActions.isEmpty()) {
			JPanel actionsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			actionsPanel.setOpaque(false);
			for (JComponent action : layout.headerActions) {
				actionsPanel.add(action);
			}
			headerBar.add(actionsPanel, BorderLayout.EAST);
		}

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.add(headerBar, BorderLayout.CENTER);
		header.add(new JSeparator(), BorderLayout.SOUTH);
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

		add(header, BorderLayout.NORTH);

		// ── Body ─────────────────────────────────────────────────────────────
		if (layout.body != null) {
			add(layout.body, BorderLayout.CENTER);
		}

		// ── Footer ───────────────────────────────────────────────────────────
		boolean hasActions = !layout.footerActions.isEmpty();
		boolean hasStatus = !layout.suppressStatus;

		if (hasActions || hasStatus) {
			JPanel footer = new JPanel(new BorderLayout());
			footer.setOpaque(false);
			footer.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

			JPanel footerContent = new JPanel(new BorderLayout());
			footerContent.setOpaque(false);

			if (hasActions) {
				JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
				buttonsPanel.setOpaque(false);
				for (JComponent action : layout.footerActions) {
					buttonsPanel.add(action);
				}
				footerContent.add(buttonsPanel, BorderLayout.WEST);
			}

			if (hasStatus) {
				statusLabel = new JLabel(" ");
				statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
				footerContent.add(statusLabel, BorderLayout.EAST);
			}

			footer.add(new JSeparator(), BorderLayout.NORTH);
			footer.add(footerContent, BorderLayout.CENTER);

			add(footer, BorderLayout.SOUTH);
		}
	}

	/**
	 * Updates the footer status label text. Does nothing if the page was built with {@link PageLayout#noStatus()}.
	 *
	 * @param text
	 *            the status message to display, or {@code null} / blank to clear
	 */
	protected void setStatus(String text) {
		if (statusLabel != null) {
			statusLabel.setText(text != null ? text : " ");
		}
	}

	/**
	 * Called by {@link SettingsPanel} when this page becomes the active (visible) page. Subclasses may override to
	 * refresh data or start background tasks.
	 */
	public void onPageShown() {
		// default no-op
	}

	/**
	 * Called by {@link SettingsPanel} when this page is no longer the active page. Subclasses may override to cancel
	 * background tasks or release resources.
	 */
	public void onPageHidden() {
		// default no-op
	}

	// ── PageLayout builder ──────────────────────────────────────────────────

	/**
	 * Builder for the three-zone page layout. Chain methods to configure the header, body, and footer, then pass the
	 * result to {@link #buildPage(PageLayout)}.
	 */
	protected static class PageLayout {

		String title;
		final List<JComponent> headerActions = new ArrayList<>();
		JComponent body;
		final List<JComponent> footerActions = new ArrayList<>();
		boolean suppressStatus;

		/**
		 * Sets the title text displayed in the header. If not set, the page's {@code sidebarLabel} is used.
		 */
		public PageLayout title(String title) {
			this.title = title;
			return this;
		}

		/**
		 * Adds a component (typically a button) to the right side of the header.
		 */
		public PageLayout headerAction(JComponent component) {
			headerActions.add(component);
			return this;
		}

		/**
		 * Sets the main body component that fills the center of the page.
		 */
		public PageLayout body(JComponent component) {
			this.body = component;
			return this;
		}

		/**
		 * Adds an action component (typically a button) to the left side of the footer.
		 */
		public PageLayout footerAction(JComponent component) {
			footerActions.add(component);
			return this;
		}

		/**
		 * Suppresses the footer status label. When combined with no footer actions, the footer is omitted entirely.
		 */
		public PageLayout noStatus() {
			this.suppressStatus = true;
			return this;
		}

	}

}
