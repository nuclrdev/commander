package dev.nuclr.commander.ui.quickView;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

import org.apache.commons.io.FilenameUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class ImageViewPanel extends JPanel {

	private BufferedImage image;

	public void setFile(File file) {
		try {
			this.image = ImageIO.read(file);
			repaint();
		} catch (Exception e) {
			log.error("Failed to read image file: {}", file.getAbsolutePath(), e);
			this.image = null;
			repaint();
		}
	}

	static final Set<String> IMAGE_EXTENSIONS = Set
			.of(
					new String[] {
							"jpg",
							"jpeg",
							"png",
							"gif",
							"bmp",
							"webp",
							"svg",
					});

	public boolean isImage(File file) {
		var extension = FilenameUtils.getExtension(file.getName()).toLowerCase();
		return IMAGE_EXTENSIONS.contains(extension);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);

		g.setColor(Color.black);
		g.fillRect(0, 0, getWidth(), getHeight());

		if (image == null) {
			return;
		}

		final int panelW = getWidth();
		final int panelH = getHeight();

		if (panelW <= 0 || panelH <= 0) {
			return;
		}

		final int imgW = image.getWidth();
		final int imgH = image.getHeight();

		if (imgW <= 0 || imgH <= 0) {
			return;
		}

		// Fit inside panel (contain) while preserving aspect ratio
		final double fitScale = Math
				.min(
						(double) panelW / imgW,
						(double) panelH / imgH);

		// Never upscale
		final double scale = Math.min(1.0, fitScale);

		final int drawW = (int) Math.round(imgW * scale);
		final int drawH = (int) Math.round(imgH * scale);

		// Center
		final int x = (panelW - drawW) / 2;
		final int y = (panelH - drawH) / 2;

		Graphics2D g2 = (Graphics2D) g.create();
		try {

			g2
					.setRenderingHint(
							RenderingHints.KEY_INTERPOLATION,
							scale < 1.0
									? RenderingHints.VALUE_INTERPOLATION_BILINEAR
									: RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

			g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g2.drawImage(image, x, y, drawW, drawH, null);
		} finally {
			g2.dispose();
		}
	}

	public void clear() {
		this.image = null;
		repaint();
	}
}
