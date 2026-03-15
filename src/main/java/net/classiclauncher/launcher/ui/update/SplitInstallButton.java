package net.classiclauncher.launcher.ui.update;

import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.*;

import net.classiclauncher.launcher.update.ReleaseInfo;

/**
 * A split button that installs the latest release by default, with a dropdown to install any specific version.
 *
 * <p>
 * Layout: a main {@link JButton} labelled "Install X.X.X" (triggers the latest) on the WEST side, and a narrow 22px "▾"
 * arrow button on the EAST side that opens a {@link JPopupMenu} listing every available version. The arrow button is
 * disabled (and hidden visually) when there is only one version to install.
 */
public class SplitInstallButton extends JPanel {

	private final JButton mainButton;
	private final JButton arrowButton;

	/**
	 * @param releases
	 *            all available releases, sorted oldest→newest; must not be empty
	 * @param installCallback
	 *            called with the selected {@link ReleaseInfo} when the user clicks either button/menu item
	 */
	public SplitInstallButton(List<ReleaseInfo> releases, Consumer<ReleaseInfo> installCallback) {
		super(new BorderLayout(0, 0));
		if (releases == null || releases.isEmpty()) throw new IllegalArgumentException("releases must not be empty");

		ReleaseInfo latest = releases.get(releases.size() - 1);
		String latestLabel = versionLabel(latest);

		mainButton = new JButton("Install " + latestLabel);
		mainButton.addActionListener(e -> installCallback.accept(latest));

		arrowButton = new JButton("\u25BE"); // ▾
		arrowButton.setMargin(new Insets(0, 3, 0, 3));
		arrowButton.setPreferredSize(new Dimension(22, mainButton.getPreferredSize().height));
		arrowButton.setEnabled(releases.size() > 1);
		arrowButton.setToolTipText("Select version…");

		if (releases.size() > 1) {
			JPopupMenu menu = buildMenu(releases, installCallback);
			arrowButton.addActionListener(e -> menu.show(arrowButton, 0, arrowButton.getHeight()));
		}

		add(mainButton, BorderLayout.CENTER);
		add(arrowButton, BorderLayout.EAST);
	}

	private JPopupMenu buildMenu(List<ReleaseInfo> releases, Consumer<ReleaseInfo> installCallback) {
		JPopupMenu menu = new JPopupMenu();
		// Show newest first in the dropdown
		for (int i = releases.size() - 1; i >= 0; i--) {
			final ReleaseInfo release = releases.get(i);
			String label = "Install " + versionLabel(release);
			JMenuItem item = new JMenuItem(label);
			item.addActionListener(e -> installCallback.accept(release));
			menu.add(item);
		}
		return menu;
	}

	private static String versionLabel(ReleaseInfo release) {
		String tag = release.getTagName();
		return tag.startsWith("v") ? tag.substring(1) : tag;
	}

}
