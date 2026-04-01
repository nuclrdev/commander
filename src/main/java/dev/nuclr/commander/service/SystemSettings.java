package dev.nuclr.commander.service;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.nuclr.platform.Settings;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Data
public class SystemSettings implements Settings {
	
	private Map<String, Map<String, Object>> namespaces = new HashMap<>();
	
	@Autowired
	private ObjectMapper objectMapper;
	
	private Path config(String folder) {
		return LocalDataLocation.resolve(folder, "settings.json");
	}

	
	public void set(String namespace, String key, Object value) {

		// Read the config JSON, convert to map, update the value, and write back to the file
		// Make sure it is thread-safe and handles concurrent updates properly
		// Also if the application dies while saving - it should not corrupt the config file so the operation must be atomic
		// Make it retry after a short delay if the file is locked by another process (e.g. another instance of the application)
		// Show an error popup and log the error if it fails after several retries
		
		// If the file does not exist, create it with an empty JSON object
		
	}

	@Override
	public <T> T get(String namespace, String key) {

		// Read the config JSON, convert to map, and return the value for the given namespace and key
		
		return null;
	}

	@Override
	public <T> T getOrDefault(String namespace, String key, T defaultValue) {

		// Read the config JSON, convert to map, and return the value for the given namespace and key, or defaultValue if not found
		
		return null;
	}

	
	
}
