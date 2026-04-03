package dev.nuclr.commander.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
		List<Path> destinationDirectories = destinationDirectories(options);
		for (Path destinationDirectory : destinationDirectories) {
			if (destinationDirectory == null || !Files.isDirectory(destinationDirectory)) {
				throw new IOException("Destination directory is not available");
			}
		}

		ProgressTracker tracker = new ProgressTracker(options, countFiles(sources, options), countBytes(sources, options));
		tracker.report(null, destinationDirectories.get(0));

		for (int destinationIndex = 0; destinationIndex < destinationDirectories.size(); destinationIndex++) {
			Path destinationDirectory = destinationDirectories.get(destinationIndex);
			for (PluginPathResource source : sources) {
				if (source == null || source.getPath() == null) {
					continue;
				}
				checkCancelled(options);

				Path sourcePath = source.getPath();
				Path targetPath = destinationDirectory.resolve(targetName(sourcePath));
				if (isSamePath(sourcePath, targetPath)) {
					tracker.skipPath(sourcePath, targetPath, options);
					continue;
				}
				if (Files.isDirectory(sourcePath) && isNestedWithin(sourcePath, targetPath)) {
					throw new IOException("Cannot copy or move a directory into itself: " + sourcePath);
				}

				Path resolvedTargetPath = resolveRootTargetPath(sourcePath, targetPath, options);
				if (resolvedTargetPath == null) {
					tracker.skipPath(sourcePath, targetPath, options);
					continue;
				}
				boolean copied = copyPath(source, sourcePath, resolvedTargetPath, options, tracker);
				if (deleteSource && copied && destinationIndex == destinationDirectories.size() - 1) {
					deleteRecursively(sourcePath);
				}
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
		if (Files.isSymbolicLink(sourcePath) && !followSymbolicLinks(options)) {
			copySymbolicLink(sourcePath, targetPath, options, tracker);
			return true;
		}
		if (Files.exists(targetPath) && Files.isDirectory(targetPath)) {
			throw new IOException("Cannot overwrite directory with file: " + targetPath);
		}

		Path parent = targetPath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (InputStream input = Files.newInputStream(sourcePath)) {
			copyStream(input, targetPath, sourcePath, options, tracker);
			return true;
		} catch (Exception ex) {
			throw ex instanceof IOException io ? io : new IOException("Failed to copy " + sourcePath, ex);
		}
	}

	private boolean copyDirectory(Path sourceDirectory, Path targetDirectory, TransferOptions options, ProgressTracker tracker) throws IOException {
		AtomicBoolean copiedEverything = new AtomicBoolean(true);
		Set<FileVisitOption> visitOptions = followSymbolicLinks(options) ? Set.of(FileVisitOption.FOLLOW_LINKS) : Set.of();
		Files.walkFileTree(sourceDirectory, visitOptions, Integer.MAX_VALUE, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				checkCancelled(options);
				if (!shouldIncludeDirectory(sourceDirectory, dir, options)) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				Path relative = sourceDirectory.relativize(dir);
				Path resolvedDirectory = resolveInTargetFileSystem(targetDirectory, relative);
				if (Files.exists(resolvedDirectory) && !Files.isDirectory(resolvedDirectory)) {
					throw new IOException("Cannot overwrite file with directory: " + resolvedDirectory);
				}
				Files.createDirectories(resolvedDirectory);
				if (preserveTimestamps(options) || accessPolicy(options) == AccessPolicy.COPY) {
					applyAttributes(dir, resolvedDirectory, options);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				checkCancelled(options);
				if (!shouldInclude(sourceDirectory, file, options)) {
					tracker.skipFile(file, targetDirectory.resolve(sourceDirectory.relativize(file)), options);
					return FileVisitResult.CONTINUE;
				}
				Path relative = sourceDirectory.relativize(file);
				Path targetFile = resolveInTargetFileSystem(targetDirectory, relative);
				Path resolvedTargetFile = resolveFileTargetPath(file, targetFile, options);
				if (resolvedTargetFile == null) {
					copiedEverything.set(false);
					tracker.skipFile(file, targetFile, options);
					return FileVisitResult.CONTINUE;
				}
				if (Files.isSymbolicLink(file) && !followSymbolicLinks(options)) {
					copySymbolicLink(file, resolvedTargetFile, options, tracker);
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
		applyAttributes(sourcePath, targetPath, options);
		tracker.fileCompleted(sourcePath, targetPath);
	}

	private void copySymbolicLink(
			Path sourcePath,
			Path targetPath,
			TransferOptions options,
			ProgressTracker tracker) throws IOException {
		Path parent = targetPath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		tracker.report(sourcePath, targetPath);
		try {
			Files.deleteIfExists(targetPath);
			Files.createSymbolicLink(targetPath, Files.readSymbolicLink(sourcePath));
			applyAttributes(sourcePath, targetPath, options);
			tracker.fileCompleted(sourcePath, targetPath);
		} catch (UnsupportedOperationException | IOException ex) {
			if (Files.isDirectory(sourcePath)) {
				copyDirectory(sourcePath, targetPath, options.withFollowSymbolicLinks(true), tracker);
			} else {
				try (InputStream input = Files.newInputStream(sourcePath)) {
					copyStream(input, targetPath, sourcePath, options.withFollowSymbolicLinks(true), tracker);
				}
			}
		}
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
		return countFiles(sources, null);
	}

	private long countFiles(List<PluginPathResource> sources, TransferOptions options) throws IOException {
		long total = 0L;
		for (PluginPathResource source : sources) {
			if (source == null || source.getPath() == null) {
				continue;
			}
			Path path = source.getPath();
			if (Files.isDirectory(path)) {
				try (var stream = walk(path, options)) {
					total += stream
							.filter(file -> !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS))
							.filter(file -> shouldInclude(path, file, options))
							.count();
				}
			} else {
				if (shouldInclude(path.getParent(), path, options)) {
					total++;
				}
			}
		}
		return total * destinationDirectories(options).size();
	}

	private long countBytes(List<PluginPathResource> sources) throws IOException {
		return countBytes(sources, null);
	}

	private long countBytes(List<PluginPathResource> sources, TransferOptions options) throws IOException {
		long total = 0L;
		for (PluginPathResource source : sources) {
			if (source == null || source.getPath() == null) {
				continue;
			}
			Path path = source.getPath();
			if (Files.isDirectory(path)) {
				try (var stream = walk(path, options)) {
					total += stream
							.filter(file -> !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS))
							.filter(file -> shouldInclude(path, file, options))
							.mapToLong(this::safeSize)
							.sum();
				}
				continue;
			}
			if (shouldInclude(path.getParent(), path, options)) {
				total += safeSize(path, source.getSizeBytes());
			}
		}
		return total * destinationDirectories(options).size();
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

	private boolean preserveTimestamps(TransferOptions options) {
		return options != null && options.preserveTimestamps();
	}

	private boolean followSymbolicLinks(TransferOptions options) {
		return options != null && options.followSymbolicLinks();
	}

	private AccessPolicy accessPolicy(TransferOptions options) {
		return options != null && options.accessPolicy() != null ? options.accessPolicy() : AccessPolicy.DEFAULT;
	}

	private List<Path> destinationDirectories(TransferOptions options) {
		if (options == null) {
			return List.of();
		}
		if (options.destinationDirectories() != null && !options.destinationDirectories().isEmpty()) {
			return options.destinationDirectories();
		}
		return options.destinationDirectory() != null ? List.of(options.destinationDirectory()) : List.of();
	}

	private boolean shouldIncludeDirectory(Path root, Path directory, TransferOptions options) {
		if (root == null || directory == null) {
			return true;
		}
		if (root.equals(directory)) {
			return true;
		}
		return shouldInclude(root, directory, options)
				|| hasIncludedDescendants(root, directory, options);
	}

	private boolean hasIncludedDescendants(Path root, Path directory, TransferOptions options) {
		if (options == null || options.filterMatchers().isEmpty()) {
			return true;
		}
		try (var stream = Files.walk(directory, 1)) {
			return stream
					.filter(path -> !directory.equals(path))
					.anyMatch(path -> shouldInclude(root, path, options)
							|| (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && hasIncludedDescendants(root, path, options)));
		} catch (IOException ex) {
			return false;
		}
	}

	private boolean shouldInclude(Path root, Path path, TransferOptions options) {
		if (options == null || options.filterMatchers().isEmpty() || path == null) {
			return true;
		}
		Path relative = root != null && path.normalize().startsWith(root.normalize()) ? root.normalize().relativize(path.normalize()) : path.getFileName();
		String unix = relative != null ? relative.toString().replace('\\', '/') : "";
		String fileName = path.getFileName() != null ? path.getFileName().toString() : unix;
		return options.filterMatchers().stream().anyMatch(matcher -> matcher.matches(Path.of(unix)) || matcher.matches(Path.of(fileName)));
	}

	private void applyAttributes(Path sourcePath, Path targetPath, TransferOptions options) {
		try {
			if (preserveTimestamps(options)) {
				BasicFileAttributes attrs = Files.readAttributes(sourcePath, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
				BasicFileAttributeView targetView = Files.getFileAttributeView(targetPath, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
				if (targetView != null) {
					targetView.setTimes(attrs.lastModifiedTime(), attrs.lastAccessTime(), attrs.creationTime());
				}
			}

			if (accessPolicy(options) == AccessPolicy.COPY) {
				copyAccessRights(sourcePath, targetPath);
			}
		} catch (Exception ignored) {
			// best effort
		}
	}

	private void copyAccessRights(Path sourcePath, Path targetPath) throws IOException {
		PosixFileAttributeView sourcePosix = Files.getFileAttributeView(sourcePath, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		PosixFileAttributeView targetPosix = Files.getFileAttributeView(targetPath, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		if (sourcePosix != null && targetPosix != null) {
			targetPosix.setPermissions(sourcePosix.readAttributes().permissions());
			return;
		}

		DosFileAttributeView sourceDos = Files.getFileAttributeView(sourcePath, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		DosFileAttributeView targetDos = Files.getFileAttributeView(targetPath, DosFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
		if (sourceDos != null && targetDos != null) {
			var attrs = sourceDos.readAttributes();
			targetDos.setArchive(attrs.isArchive());
			targetDos.setHidden(attrs.isHidden());
			targetDos.setReadOnly(attrs.isReadOnly());
			targetDos.setSystem(attrs.isSystem());
		}
	}

	private Stream<Path> walk(Path path, TransferOptions options) throws IOException {
		return followSymbolicLinks(options)
				? Files.walk(path, FileVisitOption.FOLLOW_LINKS)
				: Files.walk(path);
	}

	public enum ConflictResolution {
		ASK,
		OVERWRITE,
		SKIP,
		RENAME
	}

	public enum AccessPolicy {
		DEFAULT,
		COPY,
		INHERIT
	}

	public record TransferOptions(
			Path destinationDirectory,
			List<Path> destinationDirectories,
			ConflictResolution conflictResolution,
			ConflictResolver conflictResolver,
			ProgressListener progressListener,
			CancellationToken cancellationToken,
			AccessPolicy accessPolicy,
			boolean preserveTimestamps,
			boolean followSymbolicLinks,
			String filterExpression,
			List<PathMatcher> filterMatchers) {

		public TransferOptions(Path destinationDirectory, ConflictResolution conflictResolution, ConflictResolver conflictResolver) {
			this(destinationDirectory, List.of(destinationDirectory), conflictResolution, conflictResolver, null, null, AccessPolicy.DEFAULT, false, false, null, List.of());
		}

		public TransferOptions {
			destinationDirectories = destinationDirectories != null && !destinationDirectories.isEmpty()
					? List.copyOf(destinationDirectories)
					: destinationDirectory != null ? List.of(destinationDirectory) : List.of();
			accessPolicy = accessPolicy != null ? accessPolicy : AccessPolicy.DEFAULT;
			filterExpression = filterExpression != null && !filterExpression.isBlank() ? filterExpression.trim() : null;
			filterMatchers = filterMatchers != null ? List.copyOf(filterMatchers) : compileFilterMatchers(filterExpression);
		}

		public TransferOptions withFollowSymbolicLinks(boolean value) {
			return new TransferOptions(
					destinationDirectory,
					destinationDirectories,
					conflictResolution,
					conflictResolver,
					progressListener,
					cancellationToken,
					accessPolicy,
					preserveTimestamps,
					value,
					filterExpression,
					filterMatchers);
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

	private final class ProgressTracker {
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

		private void skipPath(Path sourcePath, Path targetPath, TransferOptions options) throws IOException {
			if (sourcePath != null && Files.isDirectory(sourcePath)) {
				try (var stream = walk(sourcePath, options)) {
					long skippedFiles = stream
							.filter(file -> !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS))
							.filter(file -> shouldInclude(sourcePath, file, options))
							.count();
					completedFiles += skippedFiles;
				}
				try (var stream = walk(sourcePath, options)) {
					transferredBytes += stream
							.filter(file -> !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS))
							.filter(file -> shouldInclude(sourcePath, file, options))
							.mapToLong(ProgressTracker::safeStaticSize)
							.sum();
				}
			} else {
				completedFiles++;
				transferredBytes += Math.max(0L, safeStaticSize(sourcePath));
			}
			report(sourcePath, targetPath);
		}

		private void skipFile(Path sourcePath, Path targetPath, TransferOptions options) {
			if (!shouldInclude(sourcePath != null ? sourcePath.getParent() : null, sourcePath, options)) {
				return;
			}
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

	private static List<PathMatcher> compileFilterMatchers(String expression) {
		if (expression == null || expression.isBlank()) {
			return List.of();
		}
		return List.of(expression.split("[;\\r\\n]+")).stream()
				.map(String::trim)
				.filter(token -> !token.isBlank())
				.map(token -> "glob:" + token.replace('\\', '/'))
				.map(syntax -> Path.of(".").getFileSystem().getPathMatcher(syntax))
				.collect(Collectors.toList());
	}
}
