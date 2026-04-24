package dev.nuclr.commander.service;

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

		try {
			openWithOsCommand(file);
		} catch (Exception e) {
			log.error("Failed to open file: {}", file.getAbsolutePath(), e);
		}
	}

	private void openWithOsCommand(File file) throws IOException {
		String os = System.getProperty("os.name").toLowerCase();
		String path = file.getAbsolutePath();
		File workDir = file.getParentFile();

		ProcessBuilder pb;
		if (os.contains("win")) {
			// Use 'start' with /D to set the working directory for the launched process.
			// This ensures programs that write files relative to CWD land in their own folder.
			pb = new ProcessBuilder("cmd", "/c", "start", "/D", workDir.getAbsolutePath(), "", path);
		} else if (os.contains("mac")) {
			pb = new ProcessBuilder("open", path);
		} else {
			pb = new ProcessBuilder("xdg-open", path);
		}
		pb.directory(workDir);
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
