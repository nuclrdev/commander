package dev.nuclr.commander.common;

public final class SystemUtils {

	public static boolean isOsWindows() {
		return System.getProperty("os.name").toLowerCase().contains("win");
	}

}
