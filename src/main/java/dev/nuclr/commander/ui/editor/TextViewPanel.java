package dev.nuclr.commander.ui.editor;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.swing.JPanel;

import org.apache.commons.io.FileUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class TextViewPanel extends JPanel {

	private Editor textArea;

	private File file;

	public TextViewPanel() {

		super(new BorderLayout());

		this.textArea = new Editor(file);

		var scrollPane = textArea.getPanel();

		this.add(scrollPane, BorderLayout.CENTER);
	}

	public void setFile(File file) {

		this.file = file;

		try {

			var content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
			this.textArea.setText(content);
			this.textArea.setEditable(false);

		} catch (IOException e) {
			log.error("Failed to read file: {}", file.getAbsolutePath(), e);
			this.textArea.setText("Error reading file: " + e.getMessage());
			this.textArea.setEditable(false);
		}

	}

	public void focus() {
		this.textArea.focus();
	}

}
