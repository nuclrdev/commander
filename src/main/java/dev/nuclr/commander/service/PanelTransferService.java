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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Service;

import dev.nuclr.plugin.PluginPathResource;

@Service
public class PanelTransferService {

	public void copy(List<PluginPathResource> sources, Path destinationDirectory) throws IOException {
		transfer(sources, new TransferOptions(destinationDirectory, ConflictResolution.OVERWRITE, null), false);
	}

	public void move(List<PluginPathResource> sources, Path destinationDirectory) throws IOException {
		transfer(sources, new TransferOptions(destinationDirectory, ConflictResolution.OVERWRITE, null), true);
	}

	public void copy(List<PluginPathResource> sources, TransferOptions options) throws IOException {
		transfer(sources, options, false);
	}

	public void move(List<PluginPathResource> sources, TransferOptions options) throws IOException {
		transfer(sources, options, true);
	}

	private void transfer(List<PluginPathResource> sources, TransferOptions options, boolean deleteSource) throws IOException {
		if (sources == null || sources.isEmpty()) {
			return;
		}
		Path destinationDirectory = options != null ? options.destinationDirectory() : null;
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
			if (Files.isDirectory(sourcePath) && isNestedWithin(sourcePath, targetPath)) {
				throw new IOException("Cannot copy or move a directory into itself: " + sourcePath);
			}

			Path resolvedTargetPath = resolveRootTargetPath(sourcePath, targetPath, options);
			if (resolvedTargetPath == null) {
				continue;
			}
			boolean copied = copyPath(source, sourcePath, resolvedTargetPath, options);
			if (deleteSource && copied) {
				deleteRecursively(sourcePath);
			}
		}
	}

	private boolean copyPath(PluginPathResource source, Path sourcePath, Path targetPath, TransferOptions options) throws IOException {
		if (Files.isDirectory(sourcePath)) {
			return copyDirectory(sourcePath, targetPath, options);
		}
		if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
			throw new IOException("Cannot overwrite directory with file: " + targetPath);
		}

		Path parent = targetPath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (InputStream input = source.openStream()) {
			Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING);
			return true;
		} catch (Exception ex) {
			throw ex instanceof IOException io ? io : new IOException("Failed to copy " + sourcePath, ex);
		}
	}

	private boolean copyDirectory(Path sourceDirectory, Path targetDirectory, TransferOptions options) throws IOException {
		AtomicBoolean copiedEverything = new AtomicBoolean(true);
		Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Path relative = sourceDirectory.relativize(dir);
				Path resolvedDirectory = resolveInTargetFileSystem(targetDirectory, relative);
				if (Files.exists(resolvedDirectory) && !Files.isDirectory(resolvedDirectory)) {
					throw new IOException("Cannot overwrite file with directory: " + resolvedDirectory);
				}
				Files.createDirectories(resolvedDirectory);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path relative = sourceDirectory.relativize(file);
				Path targetFile = resolveInTargetFileSystem(targetDirectory, relative);
				Path resolvedTargetFile = resolveFileTargetPath(file, targetFile, options);
				if (resolvedTargetFile == null) {
					copiedEverything.set(false);
					return FileVisitResult.CONTINUE;
				}
				try (InputStream input = Files.newInputStream(file)) {
					Files.copy(input, resolvedTargetFile, StandardCopyOption.REPLACE_EXISTING);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return copiedEverything.get();
	}

	private Path resolveInTargetFileSystem(Path targetDirectory, Path relativePath) {
		Path resolved = targetDirectory;
		for (Path segment : relativePath) {
			resolved = resolved.resolve(segment.toString());
		}
		return resolved;
	}

	private Path resolveRootTargetPath(Path sourcePath, Path targetPath, TransferOptions options) throws IOException {
		if (!Files.exists(targetPath)) {
			return targetPath;
		}
		if (Files.isDirectory(sourcePath) && Files.isDirectory(targetPath)) {
			ConflictResolution resolution = resolveConflict(sourcePath, targetPath, true, options);
			return switch (resolution) {
				case OVERWRITE -> targetPath;
				case SKIP -> null;
				case RENAME -> createRenamedTarget(targetPath);
				case ASK -> throw new IOException("Destination already exists: " + targetPath);
			};
		}
		return resolveFileTargetPath(sourcePath, targetPath, options);
	}

	private Path resolveFileTargetPath(Path sourcePath, Path targetPath, TransferOptions options) throws IOException {
		if (!Files.exists(targetPath)) {
			return targetPath;
		}
		if (Files.isDirectory(targetPath)) {
			throw new IOException("Cannot overwrite directory with file: " + targetPath);
		}
		ConflictResolution resolution = resolveConflict(sourcePath, targetPath, false, options);
		return switch (resolution) {
			case OVERWRITE -> targetPath;
			case SKIP -> null;
			case RENAME -> createRenamedTarget(targetPath);
			case ASK -> throw new IOException("Destination already exists: " + targetPath);
		};
	}

	private ConflictResolution resolveConflict(Path sourcePath, Path targetPath, boolean directory, TransferOptions options) throws IOException {
		ConflictResolution configured = options != null && options.conflictResolution() != null
				? options.conflictResolution()
				: ConflictResolution.OVERWRITE;
		if (configured != ConflictResolution.ASK) {
			return configured;
		}
		if (options != null && options.conflictResolver() != null) {
			ConflictResolution resolved = options.conflictResolver().resolve(sourcePath, targetPath, directory);
			return resolved != null ? resolved : ConflictResolution.SKIP;
		}
		return ConflictResolution.ASK;
	}

	private Path createRenamedTarget(Path targetPath) throws IOException {
		Path parent = targetPath.getParent();
		String fileName = targetPath.getFileName() != null ? targetPath.getFileName().toString() : "copy";
		String baseName = fileName;
		String extension = "";
		if (!Files.isDirectory(targetPath)) {
			int dot = fileName.lastIndexOf('.');
			if (dot > 0) {
				baseName = fileName.substring(0, dot);
				extension = fileName.substring(dot);
			}
		}
		for (int counter = 2; counter < 10_000; counter++) {
			String candidateName = baseName + " (" + counter + ")" + extension;
			Path candidate = parent != null ? parent.resolve(candidateName) : targetPath.getFileSystem().getPath(candidateName);
			if (!Files.exists(candidate)) {
				return candidate;
			}
		}
		throw new IOException("Cannot find a free target name for: " + targetPath);
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

	private boolean isNestedWithin(Path sourcePath, Path targetPath) {
		if (!Objects.equals(sourcePath.getFileSystem(), targetPath.getFileSystem())) {
			return false;
		}
		return targetPath.normalize().startsWith(sourcePath.normalize());
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

	public enum ConflictResolution {
		ASK,
		OVERWRITE,
		SKIP,
		RENAME
	}

	public record TransferOptions(
			Path destinationDirectory,
			ConflictResolution conflictResolution,
			ConflictResolver conflictResolver) {
	}

	@FunctionalInterface
	public interface ConflictResolver {
		ConflictResolution resolve(Path sourcePath, Path targetPath, boolean directory) throws IOException;
	}
}
