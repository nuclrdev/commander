package dev.nuclr.commander.ui.editor;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.swing.JPanel;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;

import dev.nuclr.commander.common.FileUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class EditorScreen {

	private JPanel panel;

	private Editor textArea;

	private File file;

	public EditorScreen(File file) {

		this.file = file;

		this.panel = new JPanel(new BorderLayout());

		this.textArea = new Editor();

		try {
			var content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
			this.textArea.setText(file, content);
		} catch (Exception e) {
			log.error("Failed to read file: {}", file.getAbsolutePath(), e);
			this.textArea.setText(file, "Error reading file: " + e.getMessage());
			this.textArea.setEditable(false);
		}

		var scrollPane = textArea.getPanel();

		this.panel.add(scrollPane, BorderLayout.CENTER);
	}

	public void dispose() {
		this.panel.removeAll();
	}

	public void focus() {
		this.textArea.focus();
	}

}
