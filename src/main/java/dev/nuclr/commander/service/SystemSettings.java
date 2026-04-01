/*

	Copyright 2026 Sergio, Nuclr (https://nuclr.dev)
	
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	
	http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

 */
package dev.nuclr.commander.service;

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
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JOptionPane;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import dev.nuclr.platform.Settings;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
/**
 * Persistent implementation of {@link Settings} backed by JSON files under the
 * local Nuclr data directory.
 *
 * <p>
 * Each namespace is stored independently in:
 * {@code .nuclr/<namespace>/settings.json}
 *
 * <p>
 * Writes are synchronized within the process, coordinated across processes via
 * a lock file, and committed by writing a temporary file and replacing the
 * target file atomically when the filesystem supports it.
 *
 * <p>
 * Reads return {@code null} when a key is missing or the namespace file does
 * not yet exist. {@link #getOrDefault(String, String, Object)} returns the
 * provided fallback in that case.
 */
public class SystemSettings implements Settings {

	private static final String SettingsFileName = "settings.json";
	private static final String LockFileName = "settings.lock";
	private static final String TempFileName = SettingsFileName + ".tmp";
	private static final int MaxWriteRetries = 5;
	private static final Duration RetryDelay = Duration.ofMillis(150);
	private static final String DeveloperNamespace = "system";
	private static final String DeveloperModeKey = "developerModeOn";
	private static final TypeReference<Map<String, Object>> MapType = new TypeReference<>() {
	};

	@Autowired
	private ObjectMapper objectMapper;

	private Path config(String folder) {
		return LocalDataLocation.resolve(folder, SettingsFileName);
	}

	private Path lockFile(String folder) {
		return LocalDataLocation.resolve(folder, LockFileName);
	}

	public boolean isDeveloperModeOn() {
		return getOrDefault(DeveloperNamespace, DeveloperModeKey, false);
	}

	@Override
	public synchronized void set(String namespace, String key, Object value) {
		Path configPath = config(namespace);
		Path lockPath = lockFile(namespace);
		Exception lastFailure = null;

		for (int attempt = 1; attempt <= MaxWriteRetries; attempt++) {
			try {
				writeSetting(configPath, lockPath, key, value);
				return;
			} catch (Exception e) {
				lastFailure = e;
				if (attempt < MaxWriteRetries && isRetryable(e)) {
					sleepBeforeRetry();
					continue;
				}
				break;
			}
		}

		String message = "Failed to save settings for namespace '" + namespace + "'";
		log.error(message, lastFailure);
		showErrorPopup(message, lastFailure);
		throw new IllegalStateException(message, lastFailure);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(String namespace, String key) {
		Map<String, Object> values = readNamespace(config(namespace));
		return (T) values.get(key);
	}

	@Override
	public <T> T getOrDefault(String namespace, String key, T defaultValue) {
		Object value = get(namespace, key);
		if (value == null) {
			return defaultValue;
		}
		if (defaultValue == null) {
			@SuppressWarnings("unchecked")
			T cast = (T) value;
			return cast;
		}
		return objectMapper.convertValue(value, objectMapper.constructType(defaultValue.getClass()));
	}

	private void writeSetting(Path configPath, Path lockPath, String key, Object value) throws IOException {
		Files.createDirectories(configPath.getParent());

		try (FileChannel lockChannel = FileChannel.open(lockPath, CREATE, WRITE);
				FileLock ignored = lockChannel.lock()) {
			Map<String, Object> settings = readNamespace(configPath);
			settings.put(key, value);
			writeAtomically(configPath, settings);
		}
	}

	private Map<String, Object> readNamespace(Path configPath) {
		if (!Files.exists(configPath)) {
			return new HashMap<>();
		}

		try (FileChannel ch = FileChannel.open(configPath, READ);
				FileLock ignored = ch.lock(0L, Long.MAX_VALUE, true)) {
			byte[] bytes = Channels.newInputStream(ch).readAllBytes();
			if (bytes.length == 0) {
				return new HashMap<>();
			}
			Map<String, Object> values = objectMapper.readValue(bytes, MapType);
			return values != null ? new HashMap<>(values) : new HashMap<>();
		} catch (IOException e) {
			log.warn("Failed to load settings from {}: {}", configPath, e.toString());
			return new HashMap<>();
		}
	}

	private void writeAtomically(Path configPath, Map<String, Object> settings) throws IOException {
		Path dir = configPath.getParent();
		Path tmp = dir.resolve(TempFileName);

		try {
			try (FileChannel ch = FileChannel.open(tmp, WRITE, CREATE, TRUNCATE_EXISTING);
					FileLock ignored = ch.lock()) {
				var out = Channels.newOutputStream(ch);
				out.write(objectMapper.writeValueAsBytes(settings));
				out.flush();
				ch.force(true);
			}

			try {
				Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(tmp, configPath, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			try {
				Files.deleteIfExists(tmp);
			} catch (IOException cleanupError) {
				log.debug("Failed to delete temp settings file {}", tmp, cleanupError);
			}
			throw e;
		}
	}

	private boolean isRetryable(Exception e) {
		return e instanceof IOException;
	}

	private void sleepBeforeRetry() {
		try {
			Thread.sleep(RetryDelay.toMillis());
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting to retry settings save", interrupted);
		}
	}

	private void showErrorPopup(String message, Exception cause) {
		String detail = cause != null && cause.getMessage() != null ? cause.getMessage() : "Unknown error";
		JOptionPane.showMessageDialog(null, message + "\n" + detail, "Settings Error", JOptionPane.ERROR_MESSAGE);
	}
}
