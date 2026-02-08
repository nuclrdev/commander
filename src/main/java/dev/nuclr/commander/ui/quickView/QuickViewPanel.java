package dev.nuclr.commander.ui.quickView;

import java.awt.CardLayout;
import java.io.File;
import java.util.Set;

import javax.swing.JPanel;

import org.apache.commons.io.FilenameUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.ui.editor.TextViewPanel;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@Component
@Lazy
public class QuickViewPanel {

	private JPanel panel;

	@PostConstruct
	public void init() {

		log.info("QuickViewPanel initialized");

		this.panel = new JPanel(new CardLayout());

		this.panel.add(new ImageViewPanel(), "ImageViewPanel");
		this.panel.add(new TextViewPanel(), "TextViewPanel");

	}

	public void show(File file) {
		
		var cards = (CardLayout) panel.getLayout();
		
		if (isImage(file)) {
			cards.show(panel, "ImageViewPanel");
		} else {
			cards.show(panel, "TextViewPanel");
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
							"svg"
					});
	
	private boolean isImage(File file) {
		var extension = FilenameUtils.getExtension(file.getName()).toLowerCase();
		return IMAGE_EXTENSIONS.contains(extension);
	}
	
	
	
}
