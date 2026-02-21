package dev.nuclr.commander.ui.editor;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.springframework.stereotype.Component;

import dev.nuclr.commander.common.FileUtils;
import dev.nuclr.commander.common.FilenameUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@Component
public class TextViewPanel extends JPanel {

	private Editor textArea;

	private File file;

	public TextViewPanel() {

		super(new BorderLayout());

		this.textArea = new Editor();

		var scrollPane = textArea.getPanel();

		this.add(scrollPane, BorderLayout.CENTER);
	}

	/**
	 * Loads the file content. Safe to call from any thread — all Swing
	 * updates are dispatched to the EDT via {@link SwingUtilities#invokeLater}.
	 */
	public void setFile(File file) {
		this.file = file;

		if (file.length() > 10 * 1024 * 1024) { // 10 MB limit
			log.warn("File is too large to display: {}", file.getAbsolutePath());
			SwingUtilities.invokeLater(() -> {
				this.textArea.setText(file.getName(), "File is too large to display.");
				this.textArea.setEditable(false);
			});
			return;
		}

		try {
			var content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
			SwingUtilities.invokeLater(() -> {
				this.textArea.setText(file.getName(), content);
				this.textArea.setEditable(false);
			});
		} catch (IOException e) {
			log.error("Failed to read file: {}", file.getAbsolutePath(), e);
			final String msg = "Error reading file: " + e.getMessage();
			SwingUtilities.invokeLater(() -> {
				this.textArea.setText(file.getName(), msg);
				this.textArea.setEditable(false);
			});
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
					".gitignore",
					"jsp",
					"js",
					"java",
					"py",
					"c",
					"cpp",
					"h",
					"hpp",
					"prefs",
					"meta",
					"gitattributes",
					"factorypath",
					"sh",
					"bat",
					"pref",
					"ps1",
					"yaml",
					"yml",
					"ini",
					"conf",
					"cfg",
					"cmd",
					"properties");

	public boolean isTextFile(File file) {
		String name = file.getName();
		String ext = FilenameUtils.getExtension(name).toLowerCase();
		// Dotfiles like ".gitignore" have no extension per FilenameUtils (dot at index 0),
		// so treat the part after the leading dot as the extension.
		if (ext.isEmpty() && name.startsWith(".") && name.length() > 1) {
			ext = name.substring(1).toLowerCase();
		}
		return supportedExtensions.contains(ext);
	}

}
