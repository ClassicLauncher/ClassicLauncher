package net.classiclauncher.launcher.ui.settings;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.*;

/**
 * Reusable settings panel with a left-sidebar navigation and a right content area.
 *
 * <p>
 * Sections are registered via {@link #addSection(String, JPanel)}. The sidebar uses a {@link JList} (system selection
 * colours) to navigate between sections displayed via a {@link CardLayout}.
 *
 * <p>
 * Usage:
 *
 * <pre>{@code
 * SettingsPanel settings = new SettingsPanel();
 * settings.addSection("Java", new JavaSettingsPanel(javaManager));
 * settings.addSection("Network", new NetworkSettingsPanel());
 * }</pre>
 * <p>
 * Can be embedded in a tab ({@link JTabbedPane}) for the V1_1 launcher, or wrapped in a {@link JDialog} for the Alpha
 * launcher — the panel itself has no frame dependency.
 */
public class SettingsPanel extends JPanel {

	private final DefaultListModel<SectionEntry> sidebarModel = new DefaultListModel<>();
	private final JList<SectionEntry> sidebarList;
	private final JPanel contentPanel;
	private final CardLayout cardLayout;
	private final Map<String, JPanel> sections = new LinkedHashMap<>();

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
					cardLayout.show(contentPanel, entry.id);
				}
			}
		});

		add(sidebarScroll, BorderLayout.WEST);
		add(contentPanel, BorderLayout.CENTER);
	}

	/**
	 * Registers a section and adds it to the sidebar. The first section added becomes the initially visible one.
	 *
	 * @param label
	 *            text shown in the sidebar navigation list
	 * @param content
	 *            the panel to display when this section is active
	 */
	public void addSection(String label, JPanel content) {
		String id = label.toLowerCase().replace(' ', '-');
		sections.put(id, content);
		contentPanel.add(content, id);
		sidebarModel.addElement(new SectionEntry(id, label));
		if (sidebarList.getSelectedIndex() < 0) {
			sidebarList.setSelectedIndex(0);
			cardLayout.show(contentPanel, id);
		}
	}

	/**
	 * Programmatically selects the section with the given label (case-sensitive). Does nothing if the label is not
	 * found.
	 */
	public void selectSection(String label) {
		for (int i = 0; i < sidebarModel.size(); i++) {
			if (sidebarModel.get(i).label.equals(label)) {
				sidebarList.setSelectedIndex(i);
				return;
			}
		}
	}

	// ── Internals ─────────────────────────────────────────────────────────────

	private static final class SectionEntry {

		final String id;
		final String label;

		SectionEntry(String id, String label) {
			this.id = id;
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
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
