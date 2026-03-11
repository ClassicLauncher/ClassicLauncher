package net.classiclauncher.launcher.ui;

import java.awt.*;

import javax.swing.*;

/**
 * A JPanel that delegates background painting to a swappable {@link BackgroundRenderer}.
 *
 * <p>
 * When no renderer is set (or the renderer is {@code null}), the panel fills with its background colour
 * ({@code #1E1E1E} by default), providing a neutral fallback that works both when extensions are not loaded and during
 * provider/game transitions.
 *
 * <p>
 * Call {@link #setRenderer(BackgroundRenderer)} to swap the background dynamically (e.g. when the user switches
 * provider or game). The panel automatically repaints.
 */
public class BackgroundPanel extends JPanel {

	private BackgroundRenderer renderer;

	public BackgroundPanel() {
		setOpaque(true);
		setBackground(new Color(0x1E1E1E));
	}

	/**
	 * Replaces the current background renderer and triggers a repaint.
	 *
	 * @param renderer
	 *            the new renderer, or {@code null} to revert to the solid fallback
	 */
	public void setRenderer(BackgroundRenderer renderer) {
		this.renderer = renderer;
		repaint();
	}

	/**
	 * Returns the current background renderer, or {@code null} if using the solid fallback.
	 */
	public BackgroundRenderer getRenderer() {
		return renderer;
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g); // fills with background colour (#1E1E1E)
		if (renderer != null) {
			Graphics2D g2 = (Graphics2D) g.create();
			try {
				renderer.paint(g2, getWidth(), getHeight());
			} finally {
				g2.dispose();
			}
		}
	}

}
