package net.classiclauncher.launcher.ui.settings;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import net.classiclauncher.launcher.settings.Settings;

/**
 * Reusable settings panel with a left-sidebar navigation and a right content area.
 *
 * <p>
 * Pages are registered via {@link #addPage(SettingsPage)} and displayed in priority order (lower priority values appear
 * first). Pages with equal priority preserve insertion order.
 *
 * <p>
 * The sidebar uses a {@link JList} (system selection colours) to navigate between pages displayed via a
 * {@link CardLayout}. Lifecycle hooks ({@link SettingsPage#onPageShown()} / {@link SettingsPage#onPageHidden()}) are
 * called on selection changes.
 *
 * <p>
 * Use the factory method {@link #createDefault(Settings)} to construct a pre-configured panel with the built-in pages
 * and any extension-registered pages.
 */
public class SettingsPanel extends JPanel {

	private final DefaultListModel<SectionEntry> sidebarModel = new DefaultListModel<>();
	private final JList<SectionEntry> sidebarList;
	private final JPanel contentPanel;
	private final CardLayout cardLayout;
	private final List<SettingsPage> pages = new ArrayList<>();
	private SettingsPage currentPage;

	public SettingsPanel() {
		setLayout(new BorderLayout());

		// ── Sidebar ───────────────────────────────────────────────────────────
		sidebarList = new JList<>(sidebarModel);
		sidebarList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		sidebarList.setFixedCellWidth(140);
		sidebarList.setFixedCellHeight(32);
		sidebarList.setCellRenderer(new SidebarRenderer());
		sidebarList.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));

		JScrollPane sidebarScroll = new JScrollPane(sidebarList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		Color separatorColor = UIManager.getColor("Separator.foreground");
		if (separatorColor == null) separatorColor = Color.LIGHT_GRAY;
		sidebarScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, separatorColor));

		// ── Content area ──────────────────────────────────────────────────────
		cardLayout = new CardLayout();
		contentPanel = new JPanel(cardLayout);

		sidebarList.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				SectionEntry entry = sidebarList.getSelectedValue();
				if (entry != null) {
					SettingsPage newPage = entry.page;
					if (currentPage != null && currentPage != newPage) {
						currentPage.onPageHidden();
					}
					cardLayout.show(contentPanel, entry.page.getId());
					currentPage = newPage;
					currentPage.onPageShown();
				}
			}
		});

		add(sidebarScroll, BorderLayout.WEST);
		add(contentPanel, BorderLayout.CENTER);
	}

	/**
	 * Adds a page to the panel, inserting it in priority order. Pages with equal priority preserve insertion order. The
	 * first page added (or the lowest-priority page) becomes the initially visible one.
	 *
	 * @param page
	 *            the settings page to add
	 */
	public void addPage(SettingsPage page) {
		int insertIndex = 0;
		for (int i = 0; i < pages.size(); i++) {
			if (pages.get(i).getPriority() <= page.getPriority()) {
				insertIndex = i + 1;
			} else {
				break;
			}
		}

		pages.add(insertIndex, page);
		contentPanel.add(page, page.getId());
		sidebarModel.insertElementAt(new SectionEntry(page), insertIndex);

		if (sidebarList.getSelectedIndex() < 0) {
			sidebarList.setSelectedIndex(0);
			cardLayout.show(contentPanel, page.getId());
			currentPage = page;
			page.onPageShown();
		}
	}

	/**
	 * Removes the page with the given ID. If the removed page was currently selected, the first remaining page is
	 * selected instead.
	 *
	 * @param id
	 *            the page identifier
	 */
	public void removePage(String id) {
		for (int i = 0; i < pages.size(); i++) {
			if (pages.get(i).getId().equals(id)) {
				SettingsPage removed = pages.remove(i);
				contentPanel.remove(removed);
				sidebarModel.removeElementAt(i);

				if (currentPage == removed) {
					currentPage = null;
					if (!pages.isEmpty()) {
						sidebarList.setSelectedIndex(0);
					}
				}
				return;
			}
		}
	}

	/**
	 * Returns the page with the given ID, or {@code null} if not found.
	 *
	 * @param id
	 *            the page identifier
	 * @return the page, or {@code null}
	 */
	public SettingsPage getPage(String id) {
		for (SettingsPage page : pages) {
			if (page.getId().equals(id)) return page;
		}
		return null;
	}

	/**
	 * Programmatically selects the page with the given ID. Does nothing if the ID is not found.
	 *
	 * @param id
	 *            the page identifier
	 */
	public void selectPage(String id) {
		for (int i = 0; i < pages.size(); i++) {
			if (pages.get(i).getId().equals(id)) {
				sidebarList.setSelectedIndex(i);
				return;
			}
		}
	}

	// ── Factory methods ──────────────────────────────────────────────────────

	/**
	 * Creates a settings panel with all four built-in pages (Launcher, Java, Extensions, Updates) plus any
	 * extension-registered pages from {@link Settings#getSettingsPages()}.
	 *
	 * @param settings
	 *            the application settings instance
	 * @return a fully configured settings panel
	 */
	public static SettingsPanel createDefault(Settings settings) {
		SettingsPanel panel = new SettingsPanel();
		panel.addPage(new LauncherSettingsPanel(settings.getLauncher()));
		panel.addPage(new JavaSettingsPanel(settings.getJavaManager()));
		panel.addPage(new ExtensionSettingsPanel(settings.getExtensions()));
		panel.addPage(new UpdateSettingsPanel(settings.getLauncher(), settings.getReleaseSource()));
		for (SettingsPage page : settings.getSettingsPages()) {
			panel.addPage(page);
		}
		return panel;
	}

	// ── Internals ─────────────────────────────────────────────────────────────

	private static final class SectionEntry {

		final SettingsPage page;

		SectionEntry(SettingsPage page) {
			this.page = page;
		}

		@Override
		public String toString() {
			return page.getSidebarLabel();
		}

	}

	/**
	 * Renders sidebar items with left-aligned text and a generous left indent.
	 */
	private static final class SidebarRenderer extends DefaultListCellRenderer {

		@Override
		public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
				boolean cellHasFocus) {
			JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
			label.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 4));
			label.setHorizontalAlignment(SwingConstants.LEFT);
			return label;
		}

	}

}
