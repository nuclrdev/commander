package dev.nuclr.commander.common;

public final class SystemUtils {

	public static boolean isOsWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}
	
	public static boolean isOsLinux() {
		return System.getProperty("os.name").toLowerCase().contains("linux");
	}
	
	public static boolean isOsMac() {
		return System.getProperty("os.name").toLowerCase().contains("mac");
	}
	
	/** Get version from the /dev/nuclr/commander/app.properties file */
	public static String getAppVersion() {
		var props = new java.util.Properties();
		try (var stream = SystemUtils.class.getResourceAsStream("/dev/nuclr/commander/app.properties")) {
			props.load(stream);
			return props.getProperty("version", "unknown");
		} catch (Exception e) {
			return "unknown";
		}
	}

}
