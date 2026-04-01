package dev.nuclr.commander.service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

/**
 * Provides access to the Nuclr user data directory. The directory is named
 * ".nuclr" and is located directly under the user's home directory on all
 * platforms.
 *
 * <p>
 * Resolved paths:
 * <ul>
 * <li>Windows: {@code C:\Users\<user>\.nuclr}</li>
 * <li>macOS: {@code /Users/<user>/.nuclr}</li>
 * <li>Linux: {@code /home/<user>/.nuclr}</li>
 * </ul>
 */
public class LocalDataLocation {

	private static final String NUCLR_DIR = ".nuclr";

	private static final Path DATA_PATH = resolveDataPath();

	private LocalDataLocation() {
	}

	/**
	 * Returns the .nuclr data directory as a {@link Path}. The directory is created
	 * if it does not already exist.
	 *
	 * @return the resolved and initialised data directory path
	 * @throws RuntimeException if the directory cannot be created
	 */
	public static Path path() {
		ensureExists(DATA_PATH);
		return DATA_PATH;
	}

	/**
	 * Returns the .nuclr data directory as a {@link File}. The directory is created
	 * if it does not already exist.
	 *
	 * @return the resolved and initialised data directory file
	 * @throws RuntimeException if the directory cannot be created
	 */
	public static File file() {
		return path().toFile();
	}

	/**
	 * Convenience method: resolves a child path inside the data directory.
	 *
	 * <pre>{@code
	 * Path config = LocalTempFile.resolve("config.json");
	 * Path cache = LocalTempFile.resolve("cache/thumbnails");
	 * }</pre>
	 *
	 * @param first first part of the child path
	 * @param more  additional parts (optional)
	 * @return the resolved child path
	 */
	public static Path resolve(String first, String... more) {
		return path().resolve(Paths.get(first, more));
	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	private static Path resolveDataPath() {
		String home = System.getProperty("user.home");
		if (home == null || home.isBlank()) {
			throw new IllegalStateException("System property 'user.home' is not set");
		}
		return Paths.get(home, NUCLR_DIR);
	}

	private static void ensureExists(Path dir) {
		if (Files.exists(dir)) {
			if (!Files.isDirectory(dir)) {
				throw new IllegalStateException("Nuclr data location exists but is not a directory: " + dir);
			}
			return;
		}
		try {
			Files.createDirectories(dir);
		} catch (Exception e) {
			throw new RuntimeException("Failed to create Nuclr data directory: " + dir, e);
		}
	}
}
