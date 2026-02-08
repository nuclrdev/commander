package dev.nuclr.commander.ui.quickView;

import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class ImageViewPanel extends JPanel {

	private BufferedImage image;

	@Override
	protected void paintComponent(java.awt.Graphics g) {
		super.paintComponent(g);
		if (image != null) {
			int x = (getWidth() - image.getWidth()) / 2;
			int y = (getHeight() - image.getHeight()) / 2;
			g.drawImage(image, x, y, this);
		}
	}

}
