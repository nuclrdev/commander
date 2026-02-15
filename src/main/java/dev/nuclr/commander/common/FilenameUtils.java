package dev.nuclr.commander.common;

public final class FilenameUtils {

	public static String getExtension(String name) {
		return name.lastIndexOf(".") > 0 ? name.substring(name.lastIndexOf(".") + 1) : "";
	}

}
