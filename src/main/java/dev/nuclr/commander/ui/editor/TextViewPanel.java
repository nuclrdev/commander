package dev.nuclr.commander.ui.editor;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import javax.swing.JPanel;

import dev.nuclr.commander.common.FileUtils;
import dev.nuclr.commander.common.FilenameUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class TextViewPanel extends JPanel {

	private Editor textArea;

	private File file;

	public TextViewPanel() {

		super(new BorderLayout());

		this.textArea = new Editor();

		var scrollPane = textArea.getPanel();

		this.add(scrollPane, BorderLayout.CENTER);
	}

	public void setFile(File file) {

		this.file = file;
		
		if (file.length() > 10 * 1024 * 1024) { // 10 MB limit
			log.warn("File is too large to display: {}", file.getAbsolutePath());
			this.textArea.setText(file, "File is too large to display.");
			this.textArea.setEditable(false);
			return;
		}
		
		try {

			var content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
			this.textArea.setText(file, content);
			this.textArea.setEditable(false);

		} catch (IOException e) {
			log.error("Failed to read file: {}", file.getAbsolutePath(), e);
			this.textArea.setText(file, "Error reading file: " + e.getMessage());
			this.textArea.setEditable(false);
		}

	}

	public void focus() {
		this.textArea.focus();
	}
	
	private Set<String> supportedExtensions = Set
			.of(
					"txt",
					"md",
					"log",
					"csv",
					"json",
					"xml",
					"html",
					"css",
					"classpath",
					"project",
					"gitignore",
					"jsp",
					"js",
					"java",
					"py",
					"c",
					"cpp",
					"h",
					"hpp",
					"sh",
					"bat",
					"pref",
					"ps1",
					"yaml",
					"yml",
					"ini",
					"conf",
					"cfg",
					"properties");

	public boolean isTextFile(File file) {
		var ext = FilenameUtils.getExtension(file.getName()).toLowerCase();
		return supportedExtensions.contains(ext);
	}

}
