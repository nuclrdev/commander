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

}
