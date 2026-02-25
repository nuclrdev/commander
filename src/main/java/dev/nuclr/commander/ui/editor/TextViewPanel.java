package dev.nuclr.commander.ui.editor;

import java.awt.BorderLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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

	/**
	 * Incremented each time setFile() is called. The invokeLater closure
	 * captures its value and skips the Swing update if a newer call arrived
	 * while the I/O was in progress, preventing stale setText() calls from
	 * piling up on the EDT and ballooning the RUndoManager / token pool.
	 */
	private final AtomicLong generation = new AtomicLong(0);

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
		long myGen = generation.incrementAndGet();
		this.file = file;

		if (file.length() > 10 * 1024 * 1024) { // 10 MB limit
			log.warn("File is too large to display: {}", file.getAbsolutePath());
			showMessage(file.getName(), "File is too large to display.", myGen);
			return;
		}

		if (isBinary(file)) {
			log.debug("Binary file, skipping text render: {}", file.getAbsolutePath());
			showMessage(file.getName(), "Binary file — no viewer available.", myGen);
			return;
		}

		try {
			var content = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
			SwingUtilities.invokeLater(() -> {
				if (generation.get() != myGen) return;
				this.textArea.setText(file.getName(), content);
				this.textArea.setEditable(false);
			});
		} catch (IOException e) {
			log.error("Failed to read file: {}", file.getAbsolutePath(), e);
			showMessage(file.getName(), "Error reading file: " + e.getMessage(), myGen);
		}
	}

	private void showMessage(String filename, String message, long myGen) {
		SwingUtilities.invokeLater(() -> {
			if (generation.get() != myGen) return;
			this.textArea.setText(filename, message);
			this.textArea.setEditable(false);
		});
	}

	/**
	 * Reads the first 8 KB of the file and returns true if a null byte is found.
	 * Null bytes are not valid in any text encoding and reliably indicate binary content.
	 */
	private static boolean isBinary(File file) {
		int limit = (int) Math.min(file.length(), 8192);
		if (limit == 0) return false;
		byte[] buf = new byte[limit];
		try (var in = new FileInputStream(file)) {
			int read = in.read(buf);
			for (int i = 0; i < read; i++) {
				if (buf[i] == 0) return true;
			}
		} catch (IOException e) {
			// If we can't read it, let the main read attempt fail with a proper error
		}
		return false;
	}

	public void focus() {
		this.textArea.focus();
	}

	private static Set<String> SupportedExtensions = Set
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

	public static boolean isTextFile(File file) {
		String name = file.getName();
		String ext = FilenameUtils.getExtension(name).toLowerCase();
		// Dotfiles like ".gitignore" have no extension per FilenameUtils (dot at index 0),
		// so treat the part after the leading dot as the extension.
		if (ext.isEmpty() && name.startsWith(".") && name.length() > 1) {
			ext = name.substring(1).toLowerCase();
		}
		return SupportedExtensions.contains(ext);
	}

}
