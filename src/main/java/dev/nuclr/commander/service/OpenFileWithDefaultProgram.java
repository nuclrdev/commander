package dev.nuclr.commander.service;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.event.ListViewFileOpen;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public final class OpenFileWithDefaultProgram implements ApplicationListener<ListViewFileOpen> {

	@PostConstruct
	public void init() {
		
	}

	public void open(File file) {
		if (file == null) {
			log.warn("File is null, nothing to open");
			return;
		}

		if (!file.exists()) {
			log.warn("File does not exist: {}", file.getAbsolutePath());
			return;
		}

		// Preferred way: java.awt.Desktop
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

		// OS-specific fallback
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
			// Windows
			pb = new ProcessBuilder("cmd", "/c", "start", "", path);

		} else if (os.contains("mac")) {
			// macOS
			pb = new ProcessBuilder("open", path);

		} else {
			// Linux / Unix
			pb = new ProcessBuilder("xdg-open", path);
		}

		pb.redirectErrorStream(true);
		pb.start();
	}

	@Override
	public void onApplicationEvent(ListViewFileOpen event) {
		open(event.getFile());		
	}
}
