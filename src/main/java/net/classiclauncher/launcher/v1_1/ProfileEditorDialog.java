package net.classiclauncher.launcher.v1_1;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.border.TitledBorder;

import net.classiclauncher.launcher.account.AccountProvider;
import net.classiclauncher.launcher.api.GameApi;
import net.classiclauncher.launcher.game.ExecutableType;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.game.VersionFilterOption;
import net.classiclauncher.launcher.jre.JavaInstallation;
import net.classiclauncher.launcher.jre.JavaManager;
import net.classiclauncher.launcher.profile.LauncherVisibility;
import net.classiclauncher.launcher.profile.Profile;
import net.classiclauncher.launcher.profile.Profiles;
import net.classiclauncher.launcher.settings.Settings;
import net.classiclauncher.launcher.version.Version;

/**
 * Modal dialog for creating or editing a game profile.
 *
 * <p>
 * Visible sections adapt to the currently resolved {@link Game}:
 * <ul>
 * <li>Java Settings are hidden when {@link ExecutableType} is not {@link ExecutableType#JAR}.</li>
 * <li>Game Directory, Resolution, and Auto Crash Report follow the game's capability flags.</li>
 * <li>Version filter checkboxes are driven by {@link Game#getVersionFilters()} when present, falling back to built-in
 * Minecraft-style defaults.</li>
 * </ul>
 *
 * <p>
 * The Java executable field is a combo that offers managed JREs from {@link JavaManager} plus a "Custom..." option that
 * reveals a free-text path input.
 *
 * <p>
 * Pass {@code null} as the profile to open in new-profile mode.
 */
public class ProfileEditorDialog extends JDialog {

	// ── Sentinels for the executable combo ───────────────────────────────────
	private static final String EXEC_SYSTEM_DEFAULT = "System Default";
	private static final String EXEC_CUSTOM = "Custom...";

	private static final String[] VISIBILITY_LABELS = {"Close launcher when game starts", "Keep the launcher open",
			"Hide launcher and reopen when game closes"};

	/**
	 * Built-in version filters used when the game does not specify its own.
	 */
	private static final List<VersionFilterOption> DEFAULT_VERSION_FILTERS = Arrays.asList(
			VersionFilterOption.builder().typeId("snapshot")
					.label("Enable experimental development versions (\"snapshots\")").build(),
			VersionFilterOption.builder().typeId("old_beta").label("Allow use of old Beta versions (From 2010-2011)")
					.build(),
			VersionFilterOption.builder().typeId("old_alpha").label("Allow use of old Alpha versions (From 2010)")
					.build());

	private final Profile existingProfile;
	private final Consumer<Profile> onSave;
	private final Game game;
	private final List<Version> allVersions = new ArrayList<>();

	// Profile Info fields
	private JTextField profileNameField;
	private JCheckBox gameDirCheck;
	private JTextField gameDirField;
	private JCheckBox resolutionCheck;
	private JTextField widthField;
	private JTextField heightField;
	private JCheckBox autoCrashCheck;
	private JCheckBox launcherVisibilityCheck;
	private JComboBox<String> visibilityCombo;

	// Version Selection fields — parallel to activeFilters
	private List<VersionFilterOption> activeFilters;
	private List<JCheckBox> filterCheckboxes;
	private JComboBox<String> versionDropdown;

	/**
	 * Version ID to pre-select on the next {@link #refreshVersionDropdown} call. Set by {@link #populateFields} so the
	 * saved version is restored after the dropdown is first populated.
	 */
	private String pendingVersionId = null;

	// Java Settings fields
	private JComboBox<Object> executableCombo;
	private JPanel customExePanel;
	private JTextField executableField;
	private JCheckBox jvmArgsCheck;
	private JTextField jvmArgsField;

	public ProfileEditorDialog(Frame owner, Profile profile, Consumer<Profile> onSave) {
		super(owner, profile == null ? "New Profile" : "Edit Profile — " + profile.getName(), true);
		this.existingProfile = profile;
		this.onSave = onSave;
		this.game = Game.resolve();

		setLayout(new BorderLayout());
		setMinimumSize(new Dimension(560, 0));

		if (game != null) {
			Optional<AccountProvider> provider = resolveProvider();
			GameApi api = provider.isPresent() ? provider.get().getApiForGame(game) : game.createApi();
			allVersions.addAll(api.getAvailableVersions());
		}

		JPanel form = buildForm();
		JScrollPane formScroll = new JScrollPane(form);
		formScroll.setBorder(BorderFactory.createEmptyBorder());
		add(formScroll, BorderLayout.CENTER);
		add(buildButtonRow(), BorderLayout.SOUTH);

		if (existingProfile != null) {
			populateFields(existingProfile);
		}

		refreshVersionDropdown();
		pack();
		setLocationRelativeTo(owner);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Form construction
	// ─────────────────────────────────────────────────────────────────────────

	private JPanel buildForm() {
		JPanel form = new JPanel();
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		boolean showGameDir = game == null || game.isGameDirSupported();
		boolean showResolution = game == null || game.isResolutionSupported();
		boolean showAutoCrash = game == null || game.isAutoCrashReportSupported();
		boolean showVersionSection = game == null || game.isVersionSelectionEnabled();
		boolean showJavaSection = game == null || game.getExecutableType() == ExecutableType.JAR;

		form.add(constrainHeight(buildProfileInfoSection(showGameDir, showResolution, showAutoCrash)));
		if (showVersionSection) {
			form.add(Box.createVerticalStrut(8));
			form.add(constrainHeight(buildVersionSection()));
		}
		if (showJavaSection) {
			form.add(Box.createVerticalStrut(8));
			form.add(constrainHeight(buildJavaSection()));
		}

		form.add(Box.createVerticalGlue());

		return form;
	}

	/**
	 * Constrains a section panel so that {@link BoxLayout} does not stretch it beyond its preferred height. The max
	 * height is recalculated on every layout pass so that dynamically shown/hidden children (e.g. the custom executable
	 * field) are accounted for.
	 */
	private static JPanel constrainHeight(JPanel section) {
		JPanel wrapper = new JPanel(new BorderLayout()) {

			@Override
			public Dimension getMaximumSize() {
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}

		};
		wrapper.setOpaque(false);
		wrapper.add(section, BorderLayout.NORTH);
		return wrapper;
	}

	private JPanel buildProfileInfoSection(boolean showGameDir, boolean showResolution, boolean showAutoCrash) {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Profile Info",
				TitledBorder.LEFT, TitledBorder.TOP));

		GridBagConstraints gbc = defaultGbc();
		int row = 0;

		// Profile Name (always visible)
		addLabel(panel, "Profile Name:", gbc, 0, row);
		profileNameField = new JTextField();
		addWide(panel, profileNameField, gbc, 1, row++);

		// Game Directory
		if (showGameDir) {
			gameDirCheck = new JCheckBox("Game Directory:");
			gbc.gridx = 0;
			gbc.gridy = row;
			gbc.fill = GridBagConstraints.NONE;
			panel.add(gameDirCheck, gbc);
			gameDirField = new JTextField();
			gameDirField.setEnabled(false);
			addWide(panel, gameDirField, gbc, 1, row++);
			gameDirCheck.addActionListener(e -> gameDirField.setEnabled(gameDirCheck.isSelected()));
		}

		// Resolution
		if (showResolution) {
			resolutionCheck = new JCheckBox("Resolution:");
			gbc.gridx = 0;
			gbc.gridy = row;
			gbc.fill = GridBagConstraints.NONE;
			panel.add(resolutionCheck, gbc);
			JPanel resPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
			widthField = new JTextField(4);
			widthField.setEnabled(false);
			heightField = new JTextField(4);
			heightField.setEnabled(false);
			resPanel.add(widthField);
			resPanel.add(new JLabel(" x "));
			resPanel.add(heightField);
			gbc.gridx = 1;
			gbc.gridy = row;
			gbc.fill = GridBagConstraints.HORIZONTAL;
			panel.add(resPanel, gbc);
			row++;
			resolutionCheck.addActionListener(e -> {
				widthField.setEnabled(resolutionCheck.isSelected());
				heightField.setEnabled(resolutionCheck.isSelected());
			});
		}

		// Auto crash report
		if (showAutoCrash) {
			autoCrashCheck = new JCheckBox("Automatically ask for assistance with fixing crashes");
			autoCrashCheck.setSelected(true);
			gbc.gridx = 0;
			gbc.gridy = row;
			gbc.gridwidth = 2;
			gbc.fill = GridBagConstraints.NONE;
			panel.add(autoCrashCheck, gbc);
			gbc.gridwidth = 1;
			row++;
		}

		// Launcher Visibility (always visible)
		launcherVisibilityCheck = new JCheckBox("Launcher Visibility:");
		gbc.gridx = 0;
		gbc.gridy = row;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(launcherVisibilityCheck, gbc);
		visibilityCombo = new JComboBox<>(VISIBILITY_LABELS);
		visibilityCombo.setEnabled(false);
		gbc.gridx = 1;
		gbc.gridy = row;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(visibilityCombo, gbc);
		launcherVisibilityCheck
				.addActionListener(e -> visibilityCombo.setEnabled(launcherVisibilityCheck.isSelected()));

		return panel;
	}

	private JPanel buildVersionSection() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Version Selection",
				TitledBorder.LEFT, TitledBorder.TOP));

		// Determine which filters to show
		activeFilters = (game == null) ? DEFAULT_VERSION_FILTERS : game.getVersionFilters();
		filterCheckboxes = new ArrayList<>();

		GridBagConstraints gbc = defaultGbc();
		int row = 0;
		for (VersionFilterOption filter : activeFilters) {
			JCheckBox box = new JCheckBox(filter.getLabel());
			box.addActionListener(e -> refreshVersionDropdown());
			gbc.gridx = 0;
			gbc.gridy = row;
			gbc.gridwidth = 2;
			panel.add(box, gbc);
			filterCheckboxes.add(box);
			row++;
		}
		gbc.gridwidth = 1;

		// Version dropdown
		addLabel(panel, "Use version:", gbc, 0, row);
		versionDropdown = new JComboBox<>();
		gbc.gridx = 1;
		gbc.gridy = row;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		panel.add(versionDropdown, gbc);

		return panel;
	}

	private JPanel buildJavaSection() {
		JPanel panel = new JPanel(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Java Settings (Advanced)",
				TitledBorder.LEFT, TitledBorder.TOP));

		GridBagConstraints gbc = defaultGbc();

		// ── Row 0: Java Executable (combo + optional custom field) ────────────
		addLabel(panel, "Executable:", gbc, 0, 0);

		executableCombo = buildExecutableCombo();
		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		panel.add(executableCombo, gbc);
		gbc.weightx = 0.0;

		// Custom path row (hidden until "Custom..." is selected)
		executableField = new JTextField();
		JButton revertBtn = new JButton("↩");
		revertBtn.setToolTipText("Revert to managed JRE list");
		revertBtn.setMargin(new Insets(2, 6, 2, 6));
		revertBtn.addActionListener(e -> {
			executableCombo.setSelectedItem(EXEC_SYSTEM_DEFAULT);
			executableField.setText("");
			customExePanel.setVisible(false);
			panel.revalidate();
			panel.repaint();
		});

		customExePanel = new JPanel(new BorderLayout(4, 0));
		customExePanel.add(executableField, BorderLayout.CENTER);
		customExePanel.add(revertBtn, BorderLayout.EAST);
		customExePanel.setVisible(false);

		gbc.gridx = 1;
		gbc.gridy = 1;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		panel.add(customExePanel, gbc);
		gbc.weightx = 0.0;

		// ── Row 2: JVM Arguments ──────────────────────────────────────────────
		jvmArgsCheck = new JCheckBox("JVM Arguments:");
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(jvmArgsCheck, gbc);
		jvmArgsField = new JTextField();
		jvmArgsField.setEnabled(false);
		addWide(panel, jvmArgsField, gbc, 1, 2);
		jvmArgsCheck.addActionListener(e -> jvmArgsField.setEnabled(jvmArgsCheck.isSelected()));

		return panel;
	}

	private JComboBox<Object> buildExecutableCombo() {
		JComboBox<Object> combo = new JComboBox<>();
		combo.addItem(EXEC_SYSTEM_DEFAULT);
		for (JavaInstallation inst : Settings.getInstance().getJavaManager().getAll()) {
			combo.addItem(inst);
		}
		combo.addItem(EXEC_CUSTOM);

		combo.setRenderer(new DefaultListCellRenderer() {

			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
					boolean cellHasFocus) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof JavaInstallation) {
					JavaInstallation inst = (JavaInstallation) value;
					setText(inst.getDisplayName());
					if (inst.isDefaultInstallation()) setFont(getFont().deriveFont(Font.BOLD));
				}
				return this;
			}

		});

		combo.addItemListener(e -> {
			if (e.getStateChange() == ItemEvent.SELECTED) {
				boolean isCustom = EXEC_CUSTOM.equals(e.getItem());
				customExePanel.setVisible(isCustom);
				if (customExePanel.getParent() != null) {
					customExePanel.getParent().revalidate();
					customExePanel.getParent().repaint();
				}
			}
		});

		return combo;
	}

	private JPanel buildButtonRow() {
		JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		JButton cancelBtn = new JButton("Cancel");
		cancelBtn.addActionListener(e -> dispose());
		JButton saveBtn = new JButton("Save Profile");
		saveBtn.addActionListener(e -> handleSave());
		row.add(cancelBtn);
		row.add(saveBtn);
		return row;
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Population & save logic
	// ─────────────────────────────────────────────────────────────────────────

	private void populateFields(Profile profile) {
		profileNameField.setText(profile.getName());

		if (gameDirCheck != null && profile.getGameDirectory() != null) {
			gameDirCheck.setSelected(true);
			gameDirField.setEnabled(true);
			gameDirField.setText(profile.getGameDirectory());
		}

		if (resolutionCheck != null && profile.getResolutionWidth() != null && profile.getResolutionHeight() != null) {
			resolutionCheck.setSelected(true);
			widthField.setEnabled(true);
			heightField.setEnabled(true);
			widthField.setText(profile.getResolutionWidth().toString());
			heightField.setText(profile.getResolutionHeight().toString());
		}

		if (autoCrashCheck != null) {
			autoCrashCheck.setSelected(profile.isAutoCrashReport());
		}

		LauncherVisibility vis = profile.getLauncherVisibility();
		if (vis != LauncherVisibility.CLOSE_LAUNCHER) {
			launcherVisibilityCheck.setSelected(true);
			visibilityCombo.setEnabled(true);
		}
		visibilityCombo.setSelectedIndex(visibilityIndexFor(vis));

		// Version filter checkboxes — map first 3 active filters to profile fields
		boolean[] profileFilterValues = {profile.isEnableSnapshots(), profile.isEnableBetaVersions(),
				profile.isEnableAlphaVersions()};
		for (int i = 0; i < filterCheckboxes.size() && i < profileFilterValues.length; i++) {
			filterCheckboxes.get(i).setSelected(profileFilterValues[i]);
		}

		// Java executable combo (only present for JAR-type games)
		if (executableCombo != null) {
			String exe = profile.getJavaExecutable();
			if (exe == null) {
				executableCombo.setSelectedItem(EXEC_SYSTEM_DEFAULT);
			} else {
				boolean matched = false;
				for (int i = 0; i < executableCombo.getItemCount(); i++) {
					Object item = executableCombo.getItemAt(i);
					if (item instanceof JavaInstallation
							&& ((JavaInstallation) item).getExecutablePath().equalsIgnoreCase(exe)) {
						executableCombo.setSelectedIndex(i);
						matched = true;
						break;
					}
				}
				if (!matched) {
					executableCombo.setSelectedItem(EXEC_CUSTOM);
					executableField.setText(exe);
					customExePanel.setVisible(true);
				}
			}
		}

		if (jvmArgsCheck != null && profile.getJvmArguments() != null) {
			jvmArgsCheck.setSelected(true);
			jvmArgsField.setEnabled(true);
			jvmArgsField.setText(profile.getJvmArguments());
		}

		pendingVersionId = profile.getVersionId();
	}

	private void refreshVersionDropdown() {
		String currentSelection = (String) versionDropdown.getSelectedItem();

		// Build the set of hidden typeIds based on unchecked filter boxes
		List<String> hiddenTypeIds = new ArrayList<>();
		for (int i = 0; i < filterCheckboxes.size() && i < activeFilters.size(); i++) {
			if (!filterCheckboxes.get(i).isSelected()) {
				hiddenTypeIds.add(activeFilters.get(i).getTypeId());
			}
		}

		List<Version> filtered = allVersions.stream().filter(v -> {
			if (v.getType() == null) return true;
			String typeId = v.getType().getId();
			if (typeId == null) return true;
			return hiddenTypeIds.stream().noneMatch(typeId::equalsIgnoreCase);
		}).collect(Collectors.toList());

		versionDropdown.removeAllItems();
		versionDropdown.addItem("Latest version");
		for (Version v : filtered) {
			versionDropdown.addItem(v.getVersion());
		}

		String toSelect = (currentSelection != null) ? currentSelection : pendingVersionId;
		pendingVersionId = null;
		if (toSelect != null) {
			for (int i = 0; i < versionDropdown.getItemCount(); i++) {
				if (toSelect.equals(versionDropdown.getItemAt(i))) {
					versionDropdown.setSelectedIndex(i);
					break;
				}
			}
		}
	}

	private void handleSave() {
		String name = profileNameField.getText().trim();
		if (name.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Profile name cannot be empty.", "Validation Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}

		String gameDir = null;
		if (gameDirCheck != null && gameDirCheck.isSelected() && !gameDirField.getText().trim().isEmpty()) {
			gameDir = gameDirField.getText().trim();
		}

		Integer resWidth = null, resHeight = null;
		if (resolutionCheck != null && resolutionCheck.isSelected()) {
			try {
				resWidth = Integer.parseInt(widthField.getText().trim());
				resHeight = Integer.parseInt(heightField.getText().trim());
			} catch (NumberFormatException e) {
				JOptionPane.showMessageDialog(this, "Resolution must be numeric.", "Validation Error",
						JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		LauncherVisibility visibility = launcherVisibilityCheck.isSelected()
				? visibilityFromIndex(visibilityCombo.getSelectedIndex())
				: LauncherVisibility.CLOSE_LAUNCHER;

		String selectedVersion = (String) versionDropdown.getSelectedItem();
		String versionId = ("Latest version".equals(selectedVersion) || selectedVersion == null)
				? null
				: selectedVersion;

		// Version filter states — map back to the 3 profile fields
		boolean snap = filterCheckboxes.size() > 0 && filterCheckboxes.get(0).isSelected();
		boolean beta = filterCheckboxes.size() > 1 && filterCheckboxes.get(1).isSelected();
		boolean alpha = filterCheckboxes.size() > 2 && filterCheckboxes.get(2).isSelected();

		// Java executable
		String javaExe = null;
		if (executableCombo != null) {
			Object sel = executableCombo.getSelectedItem();
			if (sel instanceof JavaInstallation) {
				javaExe = ((JavaInstallation) sel).getExecutablePath();
			} else if (EXEC_CUSTOM.equals(sel)) {
				String custom = executableField.getText().trim();
				javaExe = custom.isEmpty() ? null : custom;
			}
			// EXEC_SYSTEM_DEFAULT or null → javaExe stays null
		}

		String jvmArgs = (jvmArgsCheck != null && jvmArgsCheck.isSelected() && !jvmArgsField.getText().trim().isEmpty())
				? jvmArgsField.getText().trim()
				: null;

		boolean autoCrash = autoCrashCheck == null || autoCrashCheck.isSelected();
		String profileId = (existingProfile != null) ? existingProfile.getId() : UUID.randomUUID().toString();
		String accountId = (existingProfile != null) ? existingProfile.getAccountId() : null;
		String gameId = (existingProfile != null)
				? existingProfile.getGameId()
				: (game != null ? game.getGameId() : null);

		Profile built = Profile.builder().id(profileId).name(name).gameId(gameId).versionId(versionId)
				.accountId(accountId).gameDirectory(gameDir).resolutionWidth(resWidth).resolutionHeight(resHeight)
				.autoCrashReport(autoCrash).launcherVisibility(visibility).enableSnapshots(snap)
				.enableBetaVersions(beta).enableAlphaVersions(alpha).javaExecutable(javaExe).jvmArguments(jvmArgs)
				.build();

		Profiles profiles = Settings.getInstance().getProfiles();
		if (existingProfile == null) {
			profiles.add(built);
		} else {
			profiles.update(built);
		}

		onSave.accept(built);
		dispose();
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Helpers
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * Resolves the {@link AccountProvider} for the currently selected account. Returns an empty {@link Optional} when
	 * no account is selected or settings are unavailable.
	 */
	private Optional<AccountProvider> resolveProvider() {
		try {
			String id = Settings.getInstance().getAccount().getSelectedAccountId();
			if (id == null) return Optional.empty();
			return Settings.getInstance().getAccounts().getById(id)
					.flatMap(a -> Settings.getInstance().getAccounts().getProvider(a.getType()));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	private GridBagConstraints defaultGbc() {
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 6, 4, 6);
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.anchor = GridBagConstraints.WEST;
		return gbc;
	}

	private void addLabel(JPanel panel, String text, GridBagConstraints gbc, int x, int y) {
		gbc.gridx = x;
		gbc.gridy = y;
		gbc.fill = GridBagConstraints.NONE;
		panel.add(new JLabel(text), gbc);
	}

	private void addWide(JPanel panel, JComponent comp, GridBagConstraints gbc, int x, int y) {
		gbc.gridx = x;
		gbc.gridy = y;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		gbc.weightx = 1.0;
		panel.add(comp, gbc);
		gbc.weightx = 0.0;
	}

	private int visibilityIndexFor(LauncherVisibility vis) {
		switch (vis) {
			case KEEP_OPEN :
				return 1;
			case HIDE_LAUNCHER :
				return 2;
			default :
				return 0;
		}
	}

	private LauncherVisibility visibilityFromIndex(int idx) {
		switch (idx) {
			case 1 :
				return LauncherVisibility.KEEP_OPEN;
			case 2 :
				return LauncherVisibility.HIDE_LAUNCHER;
			default :
				return LauncherVisibility.CLOSE_LAUNCHER;
		}
	}

}
