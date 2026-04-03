package dev.nuclr.commander.service;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.event.ListViewFileOpen;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public final class OpenFileWithDefaultProgram implements ApplicationListener<ListViewFileOpen> {

	public void open(File file) {
		if (file == null) {
			log.warn("File is null, nothing to open");
			return;
		}
		if (!file.exists()) {
			log.warn("File does not exist: {}", file.getAbsolutePath());
			return;
		}

		if (Desktop.isDesktopSupported()) {
			Desktop desktop = Desktop.getDesktop();
			if (desktop.isSupported(Desktop.Action.OPEN)) {
				try {
					desktop.open(file);
					return;
				} catch (IOException e) {
					log.warn("Desktop.open failed, falling back to OS command", e);
				}
			}
		}

		try {
			openWithOsCommand(file);
		} catch (Exception e) {
			log.error("Failed to open file: {}", file.getAbsolutePath(), e);
		}
	}

	private void openWithOsCommand(File file) throws IOException {
		String os = System.getProperty("os.name").toLowerCase();
		String path = file.getAbsolutePath();

		ProcessBuilder pb;
		if (os.contains("win")) {
			pb = new ProcessBuilder("cmd", "/c", "start", "", path);
		} else if (os.contains("mac")) {
			pb = new ProcessBuilder("open", path);
		} else {
			pb = new ProcessBuilder("xdg-open", path);
		}
		pb.redirectErrorStream(true);
		pb.start();
	}

	@Override
	public void onApplicationEvent(ListViewFileOpen event) {
		Path path = event.getPath();
		if (path == null) return;

		// Only local filesystem files can be opened with the OS default program.
		// For remote/archive paths, skip silently (future: copy to temp, then open).
		if (!path.getFileSystem().equals(FileSystems.getDefault())) {
			log.info("Cannot open non-local path with OS program: {}", path);
			return;
		}

		open(path.toFile());
	}
}
