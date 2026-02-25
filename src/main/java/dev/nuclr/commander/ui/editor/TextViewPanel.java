package dev.nuclr.commander.ui.editor;

import java.awt.BorderLayout;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
		setFile(file.toPath());
		this.file = file;
	}

	/** Path-based overload — works with any NIO.2 filesystem, including ZIP archives. */
	public void setFile(Path path) {
		long myGen = generation.incrementAndGet();
		Path fn = path.getFileName();
		String name = fn != null ? fn.toString() : path.toString();

		long size;
		try {
			size = Files.size(path);
		} catch (Exception e) {
			size = 0L;
		}

		if (size > 10 * 1024 * 1024) { // 10 MB limit
			log.warn("File is too large to display: {}", path);
			showMessage(name, "File is too large to display.", myGen);
			return;
		}

		if (isBinary(path)) {
			log.debug("Binary file, skipping text render: {}", path);
			showMessage(name, "Binary file — no viewer available.", myGen);
			return;
		}

		try {
			String content = Files.readString(path, StandardCharsets.UTF_8);
			SwingUtilities.invokeLater(() -> {
				if (generation.get() != myGen) return;
				textArea.setText(name, content);
				textArea.setEditable(false);
			});
		} catch (IOException e) {
			log.error("Failed to read file: {}", path, e);
			showMessage(name, "Error reading file: " + e.getMessage(), myGen);
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
	 * Reads the first 8 KB and returns true if a null byte is found.
	 * Null bytes are not valid in any text encoding and reliably indicate binary content.
	 */
	private static boolean isBinary(Path path) {
		byte[] buf = new byte[8192];
		try (var in = Files.newInputStream(path)) {
			int read = in.read(buf);
			for (int i = 0; i < read; i++) {
				if (buf[i] == 0) return true;
			}
		} catch (IOException e) {
			// If we can't read it, let the main read attempt fail with a proper error
		}
		return false;
	}

	private static boolean isBinary(File file) {
		return isBinary(file.toPath());
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

	public static boolean isTextFile(Path path) {
		Path fn = path.getFileName();
		String name = fn != null ? fn.toString() : "";
		String ext = FilenameUtils.getExtension(name).toLowerCase();
		// Dotfiles like ".gitignore" have no extension per FilenameUtils (dot at index 0),
		// so treat the part after the leading dot as the extension.
		if (ext.isEmpty() && name.startsWith(".") && name.length() > 1) {
			ext = name.substring(1).toLowerCase();
		}
		return SupportedExtensions.contains(ext);
	}

	public static boolean isTextFile(File file) {
		return isTextFile(file.toPath());
	}

}
