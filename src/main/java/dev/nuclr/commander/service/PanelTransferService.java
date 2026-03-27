package dev.nuclr.commander.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

	private static final int COPY_BUFFER_SIZE = 64 * 1024;

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

		ProgressTracker tracker = new ProgressTracker(options, countFiles(sources), countBytes(sources));
		tracker.report(null, destinationDirectory);

		for (PluginPathResource source : sources) {
			if (source == null || source.getPath() == null) {
				continue;
			}
			checkCancelled(options);

			Path sourcePath = source.getPath();
			Path targetPath = destinationDirectory.resolve(targetName(sourcePath));
			if (isSamePath(sourcePath, targetPath)) {
				tracker.skipPath(sourcePath, targetPath);
				continue;
			}
			if (Files.isDirectory(sourcePath) && isNestedWithin(sourcePath, targetPath)) {
				throw new IOException("Cannot copy or move a directory into itself: " + sourcePath);
			}

			Path resolvedTargetPath = resolveRootTargetPath(sourcePath, targetPath, options);
			if (resolvedTargetPath == null) {
				tracker.skipPath(sourcePath, targetPath);
				continue;
			}
			boolean copied = copyPath(source, sourcePath, resolvedTargetPath, options, tracker);
			if (deleteSource && copied) {
				deleteRecursively(sourcePath);
			}
		}
	}

	private boolean copyPath(
			PluginPathResource source,
			Path sourcePath,
			Path targetPath,
			TransferOptions options,
			ProgressTracker tracker) throws IOException {
		if (Files.isDirectory(sourcePath)) {
			return copyDirectory(sourcePath, targetPath, options, tracker);
		}
		if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
			throw new IOException("Cannot overwrite directory with file: " + targetPath);
		}

		Path parent = targetPath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (InputStream input = source.openStream()) {
			copyStream(input, targetPath, sourcePath, options, tracker);
			return true;
		} catch (Exception ex) {
			throw ex instanceof IOException io ? io : new IOException("Failed to copy " + sourcePath, ex);
		}
	}

	private boolean copyDirectory(Path sourceDirectory, Path targetDirectory, TransferOptions options, ProgressTracker tracker) throws IOException {
		AtomicBoolean copiedEverything = new AtomicBoolean(true);
		Files.walkFileTree(sourceDirectory, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				checkCancelled(options);
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
				checkCancelled(options);
				Path relative = sourceDirectory.relativize(file);
				Path targetFile = resolveInTargetFileSystem(targetDirectory, relative);
				Path resolvedTargetFile = resolveFileTargetPath(file, targetFile, options);
				if (resolvedTargetFile == null) {
					copiedEverything.set(false);
					tracker.skipFile(file, targetFile);
					return FileVisitResult.CONTINUE;
				}
				try (InputStream input = Files.newInputStream(file)) {
					copyStream(input, resolvedTargetFile, file, options, tracker);
				}
				return FileVisitResult.CONTINUE;
			}
		});
		return copiedEverything.get();
	}

	private void copyStream(
			InputStream input,
			Path targetPath,
			Path sourcePath,
			TransferOptions options,
			ProgressTracker tracker) throws IOException {
		checkCancelled(options);
		tracker.report(sourcePath, targetPath);
		try (OutputStream output = Files.newOutputStream(targetPath)) {
			byte[] buffer = new byte[COPY_BUFFER_SIZE];
			int read;
			while ((read = input.read(buffer)) >= 0) {
				checkCancelled(options);
				if (read == 0) {
					continue;
				}
				output.write(buffer, 0, read);
				tracker.bytesTransferred(read, sourcePath, targetPath);
			}
		}
		tracker.fileCompleted(sourcePath, targetPath);
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

	private long countFiles(List<PluginPathResource> sources) throws IOException {
		long total = 0L;
		for (PluginPathResource source : sources) {
			if (source == null || source.getPath() == null) {
				continue;
			}
			Path path = source.getPath();
			if (Files.isDirectory(path)) {
				try (var stream = Files.walk(path)) {
					total += stream.filter(Files::isRegularFile).count();
				}
			} else {
				total++;
			}
		}
		return total;
	}

	private long countBytes(List<PluginPathResource> sources) throws IOException {
		long total = 0L;
		for (PluginPathResource source : sources) {
			if (source == null || source.getPath() == null) {
				continue;
			}
			Path path = source.getPath();
			if (Files.isDirectory(path)) {
				try (var stream = Files.walk(path)) {
					total += stream
							.filter(Files::isRegularFile)
							.mapToLong(this::safeSize)
							.sum();
				}
				continue;
			}
			total += safeSize(path, source.getSizeBytes());
		}
		return total;
	}

	private long safeSize(Path path) {
		return safeSize(path, 0L);
	}

	private long safeSize(Path path, long fallback) {
		try {
			return Files.size(path);
		} catch (IOException ex) {
			return Math.max(0L, fallback);
		}
	}

	private void checkCancelled(TransferOptions options) throws IOException {
		if (options != null && options.cancellationToken() != null && options.cancellationToken().isCancelled()) {
			throw new IOException("Transfer cancelled");
		}
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
			ConflictResolver conflictResolver,
			ProgressListener progressListener,
			CancellationToken cancellationToken) {

		public TransferOptions(Path destinationDirectory, ConflictResolution conflictResolution, ConflictResolver conflictResolver) {
			this(destinationDirectory, conflictResolution, conflictResolver, null, null);
		}
	}

	@FunctionalInterface
	public interface ConflictResolver {
		ConflictResolution resolve(Path sourcePath, Path targetPath, boolean directory) throws IOException;
	}

	@FunctionalInterface
	public interface ProgressListener {
		void onProgress(TransferProgress progress);
	}

	@FunctionalInterface
	public interface CancellationToken {
		boolean isCancelled();
	}

	public record TransferProgress(
			long totalFiles,
			long completedFiles,
			long totalBytes,
			long transferredBytes,
			Path currentSource,
			Path currentTarget) {
	}

	private static final class ProgressTracker {
		private final TransferOptions options;
		private final long totalFiles;
		private final long totalBytes;
		private long completedFiles;
		private long transferredBytes;

		private ProgressTracker(TransferOptions options, long totalFiles, long totalBytes) {
			this.options = options;
			this.totalFiles = totalFiles;
			this.totalBytes = totalBytes;
		}

		private void bytesTransferred(long bytes, Path currentSource, Path currentTarget) {
			transferredBytes += bytes;
			report(currentSource, currentTarget);
		}

		private void fileCompleted(Path currentSource, Path currentTarget) {
			completedFiles++;
			report(currentSource, currentTarget);
		}

		private void skipPath(Path sourcePath, Path targetPath) throws IOException {
			if (sourcePath != null && Files.isDirectory(sourcePath)) {
				try (var stream = Files.walk(sourcePath)) {
					long skippedFiles = stream.filter(Files::isRegularFile).count();
					completedFiles += skippedFiles;
				}
				try (var stream = Files.walk(sourcePath)) {
					transferredBytes += stream
							.filter(Files::isRegularFile)
							.mapToLong(ProgressTracker::safeStaticSize)
							.sum();
				}
			} else {
				completedFiles++;
				transferredBytes += Math.max(0L, safeStaticSize(sourcePath));
			}
			report(sourcePath, targetPath);
		}

		private void skipFile(Path sourcePath, Path targetPath) {
			completedFiles++;
			transferredBytes += Math.max(0L, safeStaticSize(sourcePath));
			report(sourcePath, targetPath);
		}

		private void report(Path currentSource, Path currentTarget) {
			if (options == null || options.progressListener() == null) {
				return;
			}
			options.progressListener().onProgress(new TransferProgress(
					totalFiles,
					Math.min(completedFiles, totalFiles),
					totalBytes,
					totalBytes > 0L ? Math.min(transferredBytes, totalBytes) : transferredBytes,
					currentSource,
					currentTarget));
		}

		private static long safeStaticSize(Path path) {
			if (path == null) {
				return 0L;
			}
			try {
				return Files.isRegularFile(path) ? Files.size(path) : 0L;
			} catch (IOException ex) {
				return 0L;
			}
		}
	}
}
