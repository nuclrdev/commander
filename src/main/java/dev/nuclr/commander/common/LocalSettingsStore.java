package dev.nuclr.commander.common;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalSettingsStore {

	/**
	 * Change this to your app name (folder name under per-user config dir).
	 * Keep it stable across versions.
	 */
	private static final String APP_NAME = "nuclr";
	private static final String FILE_NAME = "settings.json";

	@Autowired
	private ObjectMapper mapper;
	
	private Path settingsFile;

	@PostConstruct
	void init() {
		this.settingsFile = resolveSettingsFile(APP_NAME, FILE_NAME);

		try {
			Files.createDirectories(settingsFile.getParent());
		} catch (IOException e) {
			throw new IllegalStateException(
					"Failed to create settings directory: " + settingsFile.getParent(),
					e);
		}
	}

	public Path settingsPath() {
		return settingsFile;
	}

	/** Read settings; returns empty if file doesn't exist yet. */
	public synchronized Optional<AppSettings> load() {
		if (!Files.exists(settingsFile)) {
			return Optional.empty();
		}

		// Shared lock for reading (best-effort; some FS may ignore it)
		try (FileChannel ch = FileChannel.open(settingsFile, READ);
				FileLock lock = ch.lock(0L, Long.MAX_VALUE, true)) {

			byte[] bytes = Channels.newInputStream(ch).readAllBytes();
			return Optional.of(mapper.readValue(bytes, AppSettings.class));
		} catch (IOException e) {
			log.warn("Failed to load settings from {}: {}", settingsFile, e.toString());
			return Optional.empty();
		}
	}

	/** Save settings atomically (write temp file then move). */
	public synchronized void save(AppSettings settings) {
		
		Path dir = settingsFile.getParent();
		String tmpName = FILE_NAME + ".tmp";
		Path tmp = dir.resolve(tmpName);

		try {
			
			Files.createDirectories(dir);

			// Exclusive lock on temp during write
			try (FileChannel ch = FileChannel.open(tmp, WRITE, CREATE, TRUNCATE_EXISTING);
					FileLock ignored = ch.lock()) {

				var out = Channels.newOutputStream(ch);
				out.write(mapper.writeValueAsBytes(settings));
				out.flush();
			}

			// Ensure parent dir exists and then atomic replace if supported
			try {
				Files
						.move(
								tmp,
								settingsFile,
								StandardCopyOption.REPLACE_EXISTING,
								StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(tmp, settingsFile, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			// Best effort cleanup
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
			}
			throw new IllegalStateException("Failed to save settings to " + settingsFile, e);
		}
	}

	/** Load or return defaults if missing/unreadable. */
	public synchronized AppSettings loadOrDefault() {
		return load().orElseGet(AppSettings::defaults);
	}

	// ---------- Path resolution (least-privilege) ----------

	static Path resolveSettingsFile(String appName, String fileName) {

		if (SystemUtils.isOsWindows()) {
			// Prefer per-user roaming AppData (no admin needed)
			String appData = System.getenv("APPDATA");
			if (appData != null && !appData.isBlank()) {
				return Paths.get(appData, appName, fileName);
			}
			// Fallback
			return Paths
					.get(System.getProperty("user.home"), "AppData", "Roaming", appName, fileName);
		}

		if (SystemUtils.isOsMac()) {
			return Paths
					.get(
							System.getProperty("user.home"),
							"Library",
							"Application Support",
							appName,
							fileName);
		}

		// Linux/Unix: XDG config home, else ~/.config
		String xdg = System.getenv("XDG_CONFIG_HOME");
		Path base = (xdg != null && !xdg.isBlank())
				? Paths.get(xdg)
				: Paths.get(System.getProperty("user.home"), ".config");

		return base.resolve(appName).resolve(fileName);
	}

	// ---------- Example settings DTO ----------

	public record AppSettings(
			String theme,              // e.g. "dark" / "light"
			int windowWidth,
			int windowHeight,
			int windowX,
			int windowY,
			boolean maximized,
			String lastOpenedPath,
			Duration autosaveInterval,
			int dividerLocation) {
		public static AppSettings defaults() {
			return new AppSettings(
					"dark",
					1024,
					768,
					-1,
					-1,
					false,
					null,
					Duration.ofMinutes(2),
					-1);
		}
	}
}
