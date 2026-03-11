package net.classiclauncher.launcher.alpha;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.*;
import javax.swing.border.Border;

public class LoginPanel extends JPanel {

	private final JTextField userField;
	private final JPasswordField passField;
	private final JCheckBox rememberCheckbox;

	public LoginPanel() {
		setPreferredSize(new Dimension(296, 168));
		setMinimumSize(new Dimension(296, 168));
		setMaximumSize(new Dimension(296, 168));
		setOpaque(true);
		setBackground(new Color(0x808080));

		Border blackBorder = BorderFactory.createLineBorder(Color.BLACK, 2);
		Border whiteBorder = BorderFactory.createLineBorder(Color.WHITE, 1);
		setBorder(BorderFactory.createCompoundBorder(blackBorder, whiteBorder));

		setLayout(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.NONE;
		gbc.insets = new Insets(5, 5, 5, 5);

		JLabel userLabel = new JLabel("Username:");
		userLabel.setForeground(Color.BLACK);
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.EAST;
		add(userLabel, gbc);

		userField = new JTextField();
		userField.setPreferredSize(new Dimension(164, 23));
		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		add(userField, gbc);

		JLabel passLabel = new JLabel("Password:");
		passLabel.setForeground(Color.BLACK);
		gbc.gridx = 0;
		gbc.gridy = 1;
		gbc.anchor = GridBagConstraints.EAST;
		add(passLabel, gbc);

		passField = new JPasswordField();
		passField.setPreferredSize(new Dimension(164, 23));
		passField.setEchoChar('*');
		gbc.gridx = 1;
		gbc.gridy = 1;
		gbc.anchor = GridBagConstraints.WEST;
		add(passField, gbc);

		rememberCheckbox = new JCheckBox("Remember password");
		rememberCheckbox.setOpaque(false);
		rememberCheckbox.setForeground(Color.BLACK);
		gbc.gridx = 0;
		gbc.gridy = 2;
		gbc.gridwidth = 2;
		gbc.anchor = GridBagConstraints.CENTER;
		add(rememberCheckbox, gbc);

		gbc.gridx = 0;
		gbc.gridy = 3;
		gbc.gridwidth = 2;
		gbc.anchor = GridBagConstraints.CENTER;
		add(createBottomPanel(), gbc);
	}

	private JPanel createBottomPanel() {
		JLabel needAccountLink = new JLabel("<HTML><U>Need account?</U></HTML>");
		needAccountLink.setForeground(Color.BLUE);
		needAccountLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
		needAccountLink.setFont(new Font("Arial", Font.PLAIN, 12));
		needAccountLink.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				JOptionPane.showMessageDialog(LoginPanel.this, "Redirecting to account creation...", "Need Account",
						JOptionPane.INFORMATION_MESSAGE);
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				needAccountLink.setForeground(Color.RED);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				needAccountLink.setForeground(Color.BLUE);
			}

		});

		JButton loginButton = new JButton("Login");
		loginButton.addActionListener(e -> handleLogin());

		JPanel bottomPanel = new JPanel(new GridBagLayout());
		bottomPanel.setOpaque(false);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(0, 0, 0, 0);

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.anchor = GridBagConstraints.WEST;
		bottomPanel.add(needAccountLink, gbc);

		gbc.gridx = 1;
		gbc.anchor = GridBagConstraints.EAST;
		bottomPanel.add(loginButton, gbc);

		return bottomPanel;
	}

	private void handleLogin() {
		// Alpha launcher login stores the username for display purposes.
		// Actual game launch is handled by the main launcher infrastructure via BottomBar.
		String username = userField.getText();
		if (username == null || username.trim().isEmpty()) {
			JOptionPane.showMessageDialog(this, "Please enter a username.", "Login", JOptionPane.WARNING_MESSAGE);
		}
	}

}
