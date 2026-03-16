package net.classiclauncher.launcher.ui.settings;

import java.awt.*;
import java.io.File;
import java.util.List;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import net.classiclauncher.launcher.jre.JavaDetector;
import net.classiclauncher.launcher.jre.JavaInstallation;
import net.classiclauncher.launcher.jre.JavaManager;
import net.classiclauncher.launcher.ui.StarIcon;

/**
 * Settings page for managing known Java runtime installations.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Table showing all known JREs (name, version, path, source, default flag).</li>
 * <li><b>Auto-detect</b> — scans the host OS for installed JVMs; runs on a background thread.</li>
 * <li><b>Add</b> — opens a file chooser; the user selects a {@code java}/{@code java.exe} binary.</li>
 * <li><b>Remove</b> — removes the selected installation (cannot remove the default if it is the only one).</li>
 * <li><b>Set as Default</b> — marks the selected installation as the default for the {@link JavaManager}.</li>
 * </ul>
 */
public class JavaSettingsPanel extends SettingsPage {

	private static final int COL_DEFAULT = 0;
	private static final int COL_NAME = 1;
	private static final int COL_VERSION = 2;
	private static final int COL_SOURCE = 3;
	private static final int COL_PATH = 4;

	private final JavaManager javaManager;
	private final DefaultTableModel tableModel;
	private final JTable table;

	private static final StarIcon STAR_ICON = new StarIcon(14, new Color(0xF5A623));

	public JavaSettingsPanel(JavaManager javaManager) {
		super("java", "Java", 20);
		this.javaManager = javaManager;

		// ── Table ─────────────────────────────────────────────────────────────
		tableModel = new DefaultTableModel(new String[]{"Default", "Name", "Version", "Source", "Path"}, 0) {

			@Override
			public boolean isCellEditable(int row, int col) {
				return false;
			}

			@Override
			public Class<?> getColumnClass(int col) {
				return col == COL_DEFAULT ? javax.swing.Icon.class : String.class;
			}

		};

		table = new JTable(tableModel);
		table.setFillsViewportHeight(true);
		table.setRowHeight(22);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// Render the Default column as a centred icon
		table.getColumnModel().getColumn(COL_DEFAULT).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {

			@Override
			public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus,
					int row, int column) {
				JLabel label = (JLabel) super.getTableCellRendererComponent(t, "", isSelected, hasFocus, row, column);
				label.setIcon(value instanceof javax.swing.Icon ? (javax.swing.Icon) value : null);
				label.setHorizontalAlignment(SwingConstants.CENTER);
				label.setText("");
				return label;
			}

		});

		// Column widths
		table.getColumnModel().getColumn(COL_DEFAULT).setMaxWidth(60);
		table.getColumnModel().getColumn(COL_VERSION).setPreferredWidth(80);
		table.getColumnModel().getColumn(COL_SOURCE).setPreferredWidth(90);

		JScrollPane tableScroll = new JScrollPane(table);
		tableScroll.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground") != null
				? UIManager.getColor("Separator.foreground")
				: Color.LIGHT_GRAY));

		// ── Buttons ──────────────────────────────────────────────────────────
		JButton addBtn = new JButton("Add...");
		JButton removeBtn = new JButton("Remove");
		JButton setDefaultBtn = new JButton("Set as Default");
		JButton detectBtn = new JButton("Auto-detect");

		addBtn.addActionListener(e -> handleAdd());
		removeBtn.addActionListener(e -> handleRemove());
		setDefaultBtn.addActionListener(e -> handleSetDefault());
		detectBtn.addActionListener(e -> handleAutoDetect(detectBtn));

		// Disable Remove when the built-in JRE row is selected
		table.getSelectionModel().addListSelectionListener(e -> {
			if (e.getValueIsAdjusting()) return;
			JavaInstallation sel = getInstallationAt(table.getSelectedRow());
			removeBtn.setEnabled(sel == null || !sel.isBuiltIn());
		});

		buildPage(new PageLayout().body(tableScroll).footerAction(addBtn).footerAction(removeBtn)
				.footerAction(setDefaultBtn).footerAction(detectBtn));

		refresh();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Button handlers
	// ─────────────────────────────────────────────────────────────────────────

	private void handleAdd() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Select Java Executable");
		chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

		File selected = chooser.getSelectedFile();
		if (!selected.isFile()) {
			setStatus("Selected path is not a file.");
			return;
		}

		setStatus("Querying version...");
		String version = JavaDetector.queryVersion(selected.getAbsolutePath());
		JavaInstallation inst = JavaInstallation.manual(selected.getAbsolutePath(), version);
		javaManager.add(inst);
		refresh();
		setStatus("Added: " + inst.getDisplayName());
	}

	private void handleRemove() {
		int row = table.getSelectedRow();
		if (row < 0) {
			setStatus("No installation selected.");
			return;
		}
		JavaInstallation inst = getInstallationAt(row);
		if (inst == null) return;
		javaManager.remove(inst.getId());
		refresh();
		setStatus("Removed.");
	}

	private void handleSetDefault() {
		int row = table.getSelectedRow();
		if (row < 0) {
			setStatus("No installation selected.");
			return;
		}
		JavaInstallation inst = getInstallationAt(row);
		if (inst == null) return;
		javaManager.setDefault(inst.getId());
		refresh();
		setStatus("Default set to: " + inst.getDisplayName());
	}

	private void handleAutoDetect(JButton detectBtn) {
		detectBtn.setEnabled(false);
		setStatus("Detecting...");
		new SwingWorker<Integer, Void>() {

			@Override
			protected Integer doInBackground() {
				return javaManager.autoDetect();
			}

			@Override
			protected void done() {
				try {
					int added = get();
					refresh();
					setStatus(added > 0 ? "Found " + added + " new installation(s)." : "No new installations found.");
				} catch (Exception e) {
					setStatus("Detection failed: " + e.getMessage());
				} finally {
					detectBtn.setEnabled(true);
				}
			}

		}.execute();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Helpers
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Reloads the table from the current JavaManager state.
	 */
	public void refresh() {
		tableModel.setRowCount(0);
		List<JavaInstallation> all = javaManager.getAll();
		for (JavaInstallation inst : all) {
			String source = inst.isBuiltIn() ? "Built-in" : (inst.isAutoDetected() ? "Auto-detected" : "Manual");
			tableModel.addRow(new Object[]{inst.isDefaultInstallation() ? STAR_ICON : null, inst.getDisplayName(),
					inst.getVersion().isEmpty() ? "\u2014" : inst.getVersion(), source, inst.getExecutablePath()});
		}
	}

	private JavaInstallation getInstallationAt(int row) {
		List<JavaInstallation> all = javaManager.getAll();
		return (row >= 0 && row < all.size()) ? all.get(row) : null;
	}

}
