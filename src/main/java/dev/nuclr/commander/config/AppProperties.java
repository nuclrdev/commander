package dev.nuclr.commander.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.google.inject.Singleton;

@Singleton
public final class AppProperties {

	private final Properties properties = new Properties();

	public AppProperties() {
		try (InputStream input = AppProperties.class.getResourceAsStream("/dev/nuclr/commander/app.properties")) {
			if (input == null) {
				throw new IllegalStateException("Missing application properties: /dev/nuclr/commander/app.properties");
			}
			properties.load(input);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to load application properties", e);
		}
	}

	public String getRequired(String key) {
		String value = properties.getProperty(key);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Missing application property: " + key);
		}
		return value;
	}
}
