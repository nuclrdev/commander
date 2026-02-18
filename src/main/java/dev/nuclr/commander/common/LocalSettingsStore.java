package dev.nuclr.commander.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.*;
import java.time.Duration;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.*;

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
				FileLock lock = ch.lock(0L, Long.MAX_VALUE, true);
				InputStream in = Channels.newInputStream(ch)) {

			return Optional.of(mapper.readValue(in, AppSettings.class));
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
					FileLock ignored = ch.lock();
					OutputStream out = Channels.newOutputStream(ch)) {

				mapper.writeValue(out, settings);
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
		String os = System.getProperty("os.name", "").toLowerCase();
		boolean isMac = os.contains("mac");
		boolean isWin = os.contains("win");

		if (isWin) {
			// Prefer per-user roaming AppData (no admin needed)
			String appData = System.getenv("APPDATA");
			if (appData != null && !appData.isBlank()) {
				return Paths.get(appData, appName, fileName);
			}
			// Fallback
			return Paths
					.get(System.getProperty("user.home"), "AppData", "Roaming", appName, fileName);
		}

		if (isMac) {
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

	static ObjectMapper defaultMapper() {
		ObjectMapper m = new ObjectMapper();
		m.registerModule(new JavaTimeModule());
		m.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		m.enable(SerializationFeature.INDENT_OUTPUT);
		return m;
	}

	// ---------- Example settings DTO ----------

	public record AppSettings(
			String theme,              // e.g. "dark" / "light"
			int windowWidth,
			int windowHeight,
			String lastOpenedPath,
			Duration autosaveInterval) {
		public static AppSettings defaults() {
			return new AppSettings(
					"dark",
					1200,
					800,
					null,
					Duration.ofMinutes(2));
		}
	}
}
