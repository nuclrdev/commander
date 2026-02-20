package dev.nuclr.commander.ui.editor;

import java.awt.BorderLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JPanel;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class EditorScreen {

	private JPanel panel;

	private Editor textArea;

	private Path path;

	public EditorScreen(Path path) {
		this.path = path;

		this.panel = new JPanel(new BorderLayout());
		this.textArea = new Editor();

		String filename = path.getFileName() != null
				? path.getFileName().toString()
				: path.toString();

		try {
			var content = Files.readString(path, StandardCharsets.UTF_8);
			this.textArea.setText(filename, content);
		} catch (IOException e) {
			log.error("Failed to read file: {}", path, e);
			this.textArea.setText(filename, "Error reading file: " + e.getMessage());
			this.textArea.setEditable(false);
		}

		this.panel.add(textArea.getPanel(), BorderLayout.CENTER);
	}

	public void dispose() {
		this.panel.removeAll();
	}

	public void focus() {
		this.textArea.focus();
	}
}
