package net.classiclauncher.launcher.v1_1.tabs;

import java.awt.*;
import java.util.List;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import net.classiclauncher.launcher.profile.Profile;
import net.classiclauncher.launcher.settings.Settings;

/**
 * Tab that shows all profiles in a read-only table (Name, Version).
 */
public class ProfileEditorTab extends JPanel {

	private final DefaultTableModel tableModel;

	public ProfileEditorTab() {
		super(new BorderLayout());

		tableModel = new DefaultTableModel(new String[]{"Name", "Version"}, 0) {

			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}

		};

		JTable table = new JTable(tableModel);
		table.setFillsViewportHeight(true);
		table.setBackground(new Color(0x2B2B2B));
		table.setForeground(new Color(0xC8C8C8));
		table.setGridColor(new Color(0x3C3C3C));
		table.getTableHeader().setBackground(new Color(0x3C3C3C));
		table.getTableHeader().setForeground(new Color(0xC8C8C8));
		table.setRowHeight(24);

		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.getViewport().setBackground(new Color(0x2B2B2B));
		add(scrollPane, BorderLayout.CENTER);

		refresh();
	}

	/**
	 * Reloads table rows from the current profile list.
	 */
	public void refresh() {
		tableModel.setRowCount(0);
		List<Profile> profiles = Settings.getInstance().getProfiles().getAll();
		for (Profile profile : profiles) {
			String versionDisplay = profile.getVersionId() != null ? profile.getVersionId() : "Latest version";
			tableModel.addRow(new Object[]{profile.getName(), versionDisplay});
		}
	}

}
