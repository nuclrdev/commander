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
import java.nio.file.StandardCopyOption;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ThemeSchemeStore {

	private static final String APP_NAME = "nuclr";
	private static final String FILE_NAME = "themes.json";

	@Autowired
	private ObjectMapper mapper;

	private Path configFile;

	@PostConstruct
	void init() {
		this.configFile = LocalSettingsStore.resolveSettingsFile(APP_NAME, FILE_NAME);
		try {
			Files.createDirectories(configFile.getParent());
			if (!Files.exists(configFile)) {
				save(ThemeSchemesConfig.defaults());
			}
		} catch (IOException e) {
			throw new IllegalStateException(
					"Failed to initialize theme config directory: " + configFile.getParent(),
					e);
		}
	}

	public Path configPath() {
		return configFile;
	}

	public synchronized Optional<ThemeSchemesConfig> load() {
		if (!Files.exists(configFile)) {
			return Optional.empty();
		}

		try (FileChannel ch = FileChannel.open(configFile, READ);
				FileLock lock = ch.lock(0L, Long.MAX_VALUE, true)) {
			byte[] bytes = Channels.newInputStream(ch).readAllBytes();
			return Optional.of(mapper.readValue(bytes, ThemeSchemesConfig.class));
		} catch (IOException e) {
			log.warn("Failed to load theme schemes from {}: {}", configFile, e.toString());
			return Optional.empty();
		}
	}

	public synchronized ThemeSchemesConfig loadOrDefault() {
		return load().orElseGet(ThemeSchemesConfig::defaults);
	}

	public synchronized void save(ThemeSchemesConfig config) {
		Path dir = configFile.getParent();
		Path tmp = dir.resolve(FILE_NAME + ".tmp");

		try {
			Files.createDirectories(dir);
			try (FileChannel ch = FileChannel.open(tmp, WRITE, CREATE, TRUNCATE_EXISTING);
					FileLock ignored = ch.lock()) {
				var out = Channels.newOutputStream(ch);
				out.write(mapper.writeValueAsBytes(config));
				out.flush();
			}

			try {
				Files.move(
						tmp,
						configFile,
						StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(tmp, configFile, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException ignored) {
			}
			throw new IllegalStateException("Failed to save theme schemes to " + configFile, e);
		}
	}
}
