package dev.nuclr.commander.common;

import java.util.Properties;

public final class AppVersion {

	private static final Properties props = new Properties();

	static {
		try (var in = AppVersion.class
				.getResourceAsStream("/dev/nuclr/commander/version.txt")) {
			props.load(in);
		} catch (Exception e) {
			throw new RuntimeException("Cannot load version", e);
		}
	}

	public static String get() {
		return props.getProperty("version", "dev");
	}
}