package dev.nuclr.commander.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.nuclr.plugin.PluginPathResource;

@Service
public class PanelTransferService {

	public void copy(List<PluginPathResource> sources, Path destinationDirectory) throws IOException {
		transfer(sources, destinationDirectory, false);
	}

	public void move(List<PluginPathResource> sources, Path destinationDirectory) throws IOException {
		transfer(sources, destinationDirectory, true);
	}

	private void transfer(List<PluginPathResource> sources, Path destinationDirectory, boolean deleteSource) throws IOException {
		if (sources == null || sources.isEmpty()) {
			return;
		}
		if (destinationDirectory == null || !Files.isDirectory(destinationDirectory)) {
			throw new IOException("Destination directory is not available");
		}

		for (PluginPathResource source : sources) {
			if (source == null || source.getPath() == null) {
				continue;
			}

			Path sourcePath = source.getPath();
			Path targetPath = destinationDirectory.resolve(targetName(sourcePath));
			if (isSamePath(sourcePath, targetPath)) {
				continue;
			}
			if (Files.isDirectory(sourcePath) && targetPath.normalize().startsWith(sourcePath.normalize())) {
				throw new IOException("Cannot copy or move a directory into itself: " + sourcePath);
			}

			copyPath(source, sourcePath, targetPath);
			if (deleteSource) {
				deleteRecursively(sourcePath);
			}
		}
	}

	private void copyPath(PluginPathResource source, Path sourcePath, Path targetPath) throws IOException {
		if (Files.isDirectory(sourcePath)) {
			copyDirectory(sourcePath, targetPath);
			return;
		}

		Path parent = targetPath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (InputStream input = source.openStream()) {
			Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception ex) {
			throw ex instanceof IOException io ? io : new IOException("Failed to copy " + sourcePath, ex);
		}
	}

	private void copyDirectory(Path sourceDirectory, Path targetDirectory) throws IOException {
		Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Path relative = sourceDirectory.relativize(dir);
				Files.createDirectories(targetDirectory.resolve(relative));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path relative = sourceDirectory.relativize(file);
				Path targetFile = targetDirectory.resolve(relative);
				try (InputStream input = Files.newInputStream(file)) {
					Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private void deleteRecursively(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.deleteIfExists(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				Files.deleteIfExists(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	private boolean isSamePath(Path left, Path right) {
		try {
			return Files.exists(left) && Files.exists(right) && Files.isSameFile(left, right);
		} catch (IOException ex) {
			return left.normalize().equals(right.normalize());
		}
	}

	private String targetName(Path path) {
		Path fileName = path.getFileName();
		return fileName != null ? fileName.toString() : path.toString();
	}
}
