package net.classiclauncher.launcher.ui.settings;

import java.awt.*;
import java.io.File;
import java.util.List;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import net.classiclauncher.launcher.jre.JavaDetector;
import net.classiclauncher.launcher.jre.JavaInstallation;
import net.classiclauncher.launcher.jre.JavaManager;

/**
 * Settings section panel for managing known Java runtime installations.
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
public class JavaSettingsPanel extends JPanel {

	private static final int COL_DEFAULT = 0;
	private static final int COL_NAME = 1;
	private static final int COL_VERSION = 2;
	private static final int COL_SOURCE = 3;
	private static final int COL_PATH = 4;

	private final JavaManager javaManager;
	private final DefaultTableModel tableModel;
	private final JTable table;
	private final JLabel statusLabel;

	public JavaSettingsPanel(JavaManager javaManager) {
		super(new BorderLayout(0, 6));
		this.javaManager = javaManager;

		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		// ── Title ─────────────────────────────────────────────────────────────
		JLabel title = new JLabel("Java Runtime Environments");
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		add(title, BorderLayout.NORTH);

		// ── Table ─────────────────────────────────────────────────────────────
		tableModel = new DefaultTableModel(new String[]{"Default", "Name", "Version", "Source", "Path"}, 0) {

			@Override
			public boolean isCellEditable(int row, int col) {
				return false;
			}

		};

		table = new JTable(tableModel);
		table.setFillsViewportHeight(true);
		table.setRowHeight(22);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		// Column widths
		table.getColumnModel().getColumn(COL_DEFAULT).setMaxWidth(60);
		table.getColumnModel().getColumn(COL_VERSION).setPreferredWidth(80);
		table.getColumnModel().getColumn(COL_SOURCE).setPreferredWidth(90);

		JScrollPane tableScroll = new JScrollPane(table);
		tableScroll.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Separator.foreground") != null
				? UIManager.getColor("Separator.foreground")
				: Color.LIGHT_GRAY));
		add(tableScroll, BorderLayout.CENTER);

		// ── Buttons + status ──────────────────────────────────────────────────
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

		statusLabel = new JLabel(" ");
		statusLabel.setFont(statusLabel.getFont().deriveFont(11f));

		JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		buttonRow.add(addBtn);
		buttonRow.add(removeBtn);
		buttonRow.add(setDefaultBtn);
		buttonRow.add(detectBtn);
		buttonRow.add(statusLabel);

		add(buttonRow, BorderLayout.SOUTH);

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
			status("Selected path is not a file.");
			return;
		}

		status("Querying version...");
		String version = JavaDetector.queryVersion(selected.getAbsolutePath());
		JavaInstallation inst = JavaInstallation.manual(selected.getAbsolutePath(), version);
		javaManager.add(inst);
		refresh();
		status("Added: " + inst.getDisplayName());
	}

	private void handleRemove() {
		int row = table.getSelectedRow();
		if (row < 0) {
			status("No installation selected.");
			return;
		}
		JavaInstallation inst = getInstallationAt(row);
		if (inst == null) return;
		javaManager.remove(inst.getId());
		refresh();
		status("Removed.");
	}

	private void handleSetDefault() {
		int row = table.getSelectedRow();
		if (row < 0) {
			status("No installation selected.");
			return;
		}
		JavaInstallation inst = getInstallationAt(row);
		if (inst == null) return;
		javaManager.setDefault(inst.getId());
		refresh();
		status("Default set to: " + inst.getDisplayName());
	}

	private void handleAutoDetect(JButton detectBtn) {
		detectBtn.setEnabled(false);
		status("Detecting...");
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
					status(added > 0 ? "Found " + added + " new installation(s)." : "No new installations found.");
				} catch (Exception e) {
					status("Detection failed: " + e.getMessage());
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
			tableModel.addRow(new Object[]{inst.isDefaultInstallation() ? "★" : "", inst.getDisplayName(),
					inst.getVersion().isEmpty() ? "—" : inst.getVersion(), source, inst.getExecutablePath()});
		}
	}

	private JavaInstallation getInstallationAt(int row) {
		List<JavaInstallation> all = javaManager.getAll();
		return (row >= 0 && row < all.size()) ? all.get(row) : null;
	}

	private void status(String message) {
		statusLabel.setText(message);
	}

}
