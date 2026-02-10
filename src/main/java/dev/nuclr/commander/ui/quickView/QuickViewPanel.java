package dev.nuclr.commander.ui.quickView;

import java.awt.CardLayout;
import java.io.File;

import javax.swing.JPanel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.ui.editor.TextViewPanel;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Lazy
@Data
public class QuickViewPanel {

	private JPanel panel;

	private TextViewPanel textViewPanel;

	private ImageViewPanel imageViewPanel;

	private MusicViewPanel musicViewPanel;

	@Autowired
	private NoQuickViewAvailablePanel noQuickViewAvailablePanel;

	@PostConstruct
	public void init() {

		log.info("QuickViewPanel initialized");

		this.panel = new JPanel(new CardLayout());

		this.textViewPanel = new TextViewPanel();
		this.imageViewPanel = new ImageViewPanel();
		this.musicViewPanel = new MusicViewPanel();

		this.panel.add(imageViewPanel, "ImageViewPanel");
		this.panel.add(textViewPanel, "TextViewPanel");
		this.panel.add(musicViewPanel, "MusicViewPanel");
		this.panel.add(noQuickViewAvailablePanel, "NoQuickViewAvailablePanel");

	}

	public void show(File file) {

		stop();

		var cards = (CardLayout) panel.getLayout();

		if (imageViewPanel.isImage(file)) {
			imageViewPanel.setFile(file);
			cards.show(panel, "ImageViewPanel");
		} else if (musicViewPanel.isMusicFile(file)) {
			musicViewPanel.setFile(file);
			cards.show(panel, "MusicViewPanel");
		} else if (textViewPanel.isTextFile(file)) {
			textViewPanel.setFile(file);
			cards.show(panel, "TextViewPanel");
		} else {
			log.warn("No quick view available for file: {}", file.getAbsolutePath());
			noQuickViewAvailablePanel.setFile(file);
			cards.show(panel, "NoQuickViewAvailablePanel");
		}

	}

	public void stop() {

		// Stop music if any
		musicViewPanel.stopMusic();

		this.imageViewPanel.clear();

	}

}
