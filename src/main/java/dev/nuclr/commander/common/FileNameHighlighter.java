package dev.nuclr.commander.common;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central registry for Commander-managed filename highlight colors.
 *
 * <p>
 * Plugins should read these values through {@link NuclrThemeScheme} using keys
 * in the form {@code file-hilight-<extension>}.
 */
public final class FileNameHighlighter {

	public static final String PREFIX = "file-hilight-";
	public static final String HIDDEN_KEY = PREFIX + "hidden";
	public static final String EXECUTABLE_KEY = PREFIX + "executable";

	private FileNameHighlighter() {
	}

	public static Map<String, String> mergeThemeEntries(Map<String, String> baseEntries) {
		Map<String, String> merged = new LinkedHashMap<>(defaultThemeEntries());
		merged.putAll(baseEntries);
		return Map.copyOf(merged);
	}

	public static Map<String, String> defaultThemeEntries() {
		Map<String, String> entries = new LinkedHashMap<>();
		put(entries, "e75c5c", "exe", "com", "bat", "cmd");
		put(entries, "787878", "hidden");
		put(entries, "e75c5c", "executable");
		put(entries, "3c965a", "txt", "md", "java", "py", "c", "cpp", "h", "html", "css", "js", "json",
				"xml", "csv", "log", "ini", "cfg", "conf", "properties", "yaml", "yml", "sh", "bat", "ps1",
				"pdf");
		put(entries, "286eb4", "png", "jpg", "jpeg", "bmp", "gif", "svg", "webp", "ico", "tiff", "psd",
				"ai", "eps");
		put(entries, "8c46aa", "mp3", "wav", "xm", "mod", "s3m", "it", "669", "mid", "midi");
		put(entries, "b96e19", "zip", "jar", "rar", "tar", "gz", "tgz", "war", "ear");
		put(entries, "1eaac8", "mp4", "mkv", "mov", "avi", "webm", "ts", "m2ts", "mxf", "3gp", "m4v",
				"wmv", "flv", "f4v", "vob", "ogv");
		return Map.copyOf(entries);
	}

	private static void put(Map<String, String> entries, String hexColor, String... extensions) {
		for (String extension : extensions) {
			entries.put(PREFIX + extension, "#" + hexColor);
		}
	}
}
