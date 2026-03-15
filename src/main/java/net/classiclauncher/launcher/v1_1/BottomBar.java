package net.classiclauncher.launcher.v1_1;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.swing.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.classiclauncher.launcher.GameLauncher;
import net.classiclauncher.launcher.LauncherContext;
import net.classiclauncher.launcher.account.Account;
import net.classiclauncher.launcher.game.Game;
import net.classiclauncher.launcher.jre.JavaInstallation;
import net.classiclauncher.launcher.launch.LaunchContext;
import net.classiclauncher.launcher.launch.LaunchProgress;
import net.classiclauncher.launcher.profile.Profile;
import net.classiclauncher.launcher.settings.Settings;
import net.classiclauncher.launcher.v1_1.tabs.LauncherLogTab;
import net.classiclauncher.launcher.v1_1.tabs.ProfileEditorTab;

/**
 * Bottom control strip for the V1_1 launcher frame.
 *
 * <p>
 * Layout (left → right):
 * <ul>
 * <li><b>WEST</b> — profile selector (label + combo) with New/Edit buttons below</li>
 * <li><b>CENTER</b> — Play button</li>
 * <li><b>EAST</b> — welcome label, "Ready to play" label, Switch User button</li>
 * </ul>
 *
 * <p>
 * A thin determinate {@link JProgressBar} is rendered as an overlay at the very top of the bar (spanning the full
 * width, 3 px tall) via a manual {@code doLayout()} override, so it never displaces the three-column layout beneath it.
 */
public class BottomBar extends JPanel {

	private static final Logger log = LogManager.getLogger(BottomBar.class);

	private final JFrame owner;
	private final ProfileEditorTab profileEditorTab;
	private final LauncherLogTab launcherLogTab;
	private final Runnable onSwitchUser;
	private final Runnable switchToLogTab;

	private final JComboBox<String> profileCombo;
	private final List<Profile> profileList = new ArrayList<>();
	private final JLabel welcomeLabel;
	private final JLabel readyLabel;

	private final JButton playBtn;
	private final JProgressBar progressBar;
	private JPanel mainContent;

	/**
	 * Suppresses ItemListener callbacks during bulk profile list refresh.
	 */
	private boolean suppressProfileComboEvents = false;

	public BottomBar(JFrame owner, ProfileEditorTab profileEditorTab, LauncherLogTab launcherLogTab,
			Runnable onSwitchUser, Runnable switchToLogTab) {
		this.owner = owner;
		this.profileEditorTab = profileEditorTab;
		this.launcherLogTab = launcherLogTab;
		this.onSwitchUser = onSwitchUser;
		this.switchToLogTab = switchToLogTab;

		setLayout(null); // doLayout() manually sizes children so the progress bar can overlay
		setPreferredSize(new Dimension(0, 60));
		setOpaque(true);

		Color separatorColor = UIManager.getColor("Separator.foreground");
		if (separatorColor == null) separatorColor = Color.LIGHT_GRAY;
		setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, separatorColor));

		// ── WEST: profile selector ────────────────────────────────────────────
		profileCombo = new JComboBox<>();
		profileCombo.setPreferredSize(new Dimension(180, 26));
		profileCombo.addItemListener(e -> {
			if (!suppressProfileComboEvents && e.getStateChange() == ItemEvent.SELECTED) {
				Profile selected = getSelectedProfile();
				if (selected != null) {
					Settings.getInstance().getLauncher().setSelectedProfileId(selected.getId());
				}
				refreshAccountInfo();
			}
		});

		JButton newProfileBtn = new JButton("New Profile");
		newProfileBtn.addActionListener(e -> openNewProfileDialog());

		JButton editProfileBtn = new JButton("Edit Profile");
		editProfileBtn.addActionListener(e -> openEditProfileDialog());

		JPanel profileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		profileRow.setOpaque(false);
		profileRow.add(new JLabel("Profile:"));
		profileRow.add(profileCombo);

		JPanel profileButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		profileButtonRow.setOpaque(false);
		profileButtonRow.add(newProfileBtn);
		profileButtonRow.add(editProfileBtn);

		JPanel westPanel = new JPanel();
		westPanel.setLayout(new BoxLayout(westPanel, BoxLayout.Y_AXIS));
		westPanel.setOpaque(false);
		westPanel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
		westPanel.add(Box.createVerticalGlue());
		westPanel.add(profileRow);
		westPanel.add(profileButtonRow);
		westPanel.add(Box.createVerticalGlue());

		// ── CENTER: play button ────────────────────────────────────────────────
		playBtn = new JButton("Play");
		playBtn.addActionListener(e -> handlePlay());

		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.setOpaque(false);
		centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
		centerPanel.add(playBtn, BorderLayout.CENTER);

		// ── EAST: account info ────────────────────────────────────────────────
		welcomeLabel = new JLabel();
		welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		welcomeLabel.setHorizontalAlignment(JLabel.CENTER);

		readyLabel = new JLabel();
		readyLabel.setFont(readyLabel.getFont().deriveFont(11f));
		readyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		readyLabel.setHorizontalAlignment(JLabel.CENTER);

		JButton switchUserBtn = new JButton("Switch User");
		switchUserBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
		switchUserBtn.addActionListener(e -> handleSwitchUser());

		JPanel eastPanel = new JPanel();
		eastPanel.setLayout(new BoxLayout(eastPanel, BoxLayout.Y_AXIS));
		eastPanel.setOpaque(false);
		eastPanel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
		eastPanel.add(Box.createVerticalGlue());
		eastPanel.add(welcomeLabel);
		eastPanel.add(readyLabel);
		eastPanel.add(switchUserBtn);
		eastPanel.add(Box.createVerticalGlue());

		// ── Main content (3-column grid) ───────────────────────────────────────
		mainContent = new JPanel(new GridLayout(1, 3));
		mainContent.setOpaque(false);
		mainContent.add(westPanel);
		mainContent.add(centerPanel);
		mainContent.add(eastPanel);

		// ── Progress bar (overlay at very top, full width, 3px) ──────────────
		progressBar = new JProgressBar(0, 100);
		progressBar.setStringPainted(false);
		progressBar.setVisible(false);

		// mainContent fills the bar; progressBar is a 3px overlay at the very top.
		// Z-order: index 0 = painted on top; add mainContent first then insert progressBar at 0.
		add(mainContent);
		add(progressBar, 0);
	}

	@Override
	public void doLayout() {
		Insets in = getInsets();
		int x = in.left;
		int y = in.top;
		int w = getWidth() - in.left - in.right;
		int h = getHeight() - in.top - in.bottom;
		if (mainContent != null) mainContent.setBounds(x, y, w, h);
		if (progressBar != null) progressBar.setBounds(x, y, w, 3);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Refresh helpers
	// ─────────────────────────────────────────────────────────────────────────

	public void refreshProfiles() {
		suppressProfileComboEvents = true;
		profileList.clear();
		Game resolvedGame = Game.resolve();
		String activeGameId = resolvedGame != null ? resolvedGame.getGameId() : null;
		for (Profile p : Settings.getInstance().getProfiles().getAll()) {
			// Show profile if it has no gameId (legacy) or if it matches the active game
			if (p.getGameId() == null || p.getGameId().equals(activeGameId)) {
				profileList.add(p);
			}
		}
		profileCombo.removeAllItems();
		for (Profile p : profileList) {
			profileCombo.addItem(p.getName());
		}
		// Restore the previously selected profile by ID
		String savedId = Settings.getInstance().getLauncher().getSelectedProfileId();
		int restoredIdx = 0;
		if (savedId != null) {
			for (int i = 0; i < profileList.size(); i++) {
				if (savedId.equals(profileList.get(i).getId())) {
					restoredIdx = i;
					break;
				}
			}
		}
		if (!profileList.isEmpty()) {
			profileCombo.setSelectedIndex(restoredIdx);
		}
		suppressProfileComboEvents = false;
	}

	public void refreshAccountInfo() {
		Settings settings = Settings.getInstance();
		String selectedId = settings.getAccount().getSelectedAccountId();
		Optional<Account> account = (selectedId != null)
				? settings.getAccounts().getById(selectedId)
				: Optional.empty();

		Profile profile = getSelectedProfile();
		String version = (profile != null && profile.getVersionId() != null) ? profile.getVersionId() : "Latest";

		Game resolvedGame = Game.resolve();
		String gameName = (resolvedGame != null && resolvedGame.getDisplayName() != null)
				? resolvedGame.getDisplayName()
				: LauncherContext.getInstance().getName();

		owner.setTitle(resolvedGame != null ? resolvedGame.getDisplayName() + " Launcher" : "Launcher");

		if (account.isPresent()) {
			welcomeLabel.setText("<html>Welcome, <b>" + account.get().getDisplayName() + "</b></html>");
			readyLabel.setText("Ready to play " + gameName + " " + version);
		} else {
			welcomeLabel.setText("No account selected");
			readyLabel.setText("Click Switch User to log in");
		}
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Button actions
	// ─────────────────────────────────────────────────────────────────────────

	private void openNewProfileDialog() {
		new ProfileEditorDialog(owner, null, savedProfile -> {
			Settings.getInstance().getLauncher().setSelectedProfileId(savedProfile.getId());
			refreshProfiles();
			refreshAccountInfo();
			profileEditorTab.refresh();
		}).setVisible(true);
	}

	private void openEditProfileDialog() {
		Profile selected = getSelectedProfile();
		if (selected == null) {
			JOptionPane.showMessageDialog(owner, "No profile selected.", "Edit Profile",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		new ProfileEditorDialog(owner, selected, savedProfile -> {
			refreshProfiles();
			refreshAccountInfo();
			profileEditorTab.refresh();
		}).setVisible(true);
	}

	private void handlePlay() {
		Profile profile = getSelectedProfile();
		if (profile == null) {
			JOptionPane.showMessageDialog(owner, "No profile selected.", "Play", JOptionPane.WARNING_MESSAGE);
			return;
		}

		Settings settings = Settings.getInstance();
		String accountId = (profile.getAccountId() != null)
				? profile.getAccountId()
				: settings.getAccount().getSelectedAccountId();

		Optional<Account> accountOpt = (accountId != null)
				? settings.getAccounts().getById(accountId)
				: Optional.empty();

		if (!accountOpt.isPresent()) {
			JOptionPane.showMessageDialog(owner, "No account selected. Please log in first.", "Play",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		Account account = accountOpt.get();
		Game game = Game.resolve();
		if (game == null) {
			JOptionPane.showMessageDialog(owner, "No game configured for this account.", "Play",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		// Resolve JRE: profile.javaExecutable → JavaManager lookup → null (use PATH)
		JavaInstallation jre = null;
		String profileJavaExe = profile.getJavaExecutable();
		if (profileJavaExe != null && !profileJavaExe.isEmpty()) {
			jre = settings.getJavaManager().getAll().stream().filter(j -> j.getExecutablePath().equals(profileJavaExe))
					.findFirst().orElse(null);
			if (jre == null) {
				// Executable path specified but not registered — wrap it directly
				jre = new JavaInstallation(profileJavaExe, profileJavaExe, profileJavaExe, "", false, false, false);
			}
		} else {
			jre = settings.getJavaManager().getDefault().orElse(null);
		}

		LaunchContext ctx = new LaunchContext(account, game, profile, jre);

		// Capture fields for use inside the anonymous LaunchProgress implementation
		final Logger outerLog = log;
		final JButton playBtnRef = this.playBtn;
		final JProgressBar barRef = this.progressBar;
		final LauncherLogTab logTabRef = this.launcherLogTab;

		LaunchProgress progress = new LaunchProgress() {

			private int completedFiles = 0;

			@Override
			public void log(String message) {
				logTabRef.appendLine(message);
				outerLog.info("[game] {}", message);
			}

			@Override
			public void setTotalFiles(int total) {
				SwingUtilities.invokeLater(() -> {
					barRef.setMaximum(Math.max(total, 1));
					barRef.setValue(0);
					barRef.setVisible(true);
				});
			}

			@Override
			public void fileCompleted() {
				completedFiles++;
				final int completed = completedFiles;
				SwingUtilities.invokeLater(() -> barRef.setValue(completed));
			}

			@Override
			public void fileProgress(String fileName, long bytes, long totalBytes) {
				// Detailed progress is visible in the log tab (auto-switched on launch)
			}

			@Override
			public void onLaunchComplete(boolean success) {
				SwingUtilities.invokeLater(() -> {
					playBtnRef.setEnabled(true);
					barRef.setVisible(false);
				});
			}

		};

		// Prepare UI for launch
		playBtn.setEnabled(false);
		progressBar.setValue(0);
		launcherLogTab.clear();
		if (switchToLogTab != null) switchToLogTab.run();

		new GameLauncher(owner).launchAsync(ctx, progress);
	}

	private void handleSwitchUser() {
		onSwitchUser.run();
	}

	private Profile getSelectedProfile() {
		int idx = profileCombo.getSelectedIndex();
		return (idx >= 0 && idx < profileList.size()) ? profileList.get(idx) : null;
	}

}
