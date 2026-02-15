package dev.nuclr.commander.common;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public final class FileUtils {

	public static String byteCountToDisplaySize(long length) {
		if (length < 1024) {
			return length + " B";
		} else if (length < 1024 * 1024) {
			return String.format("%.2f KB", length / 1024.0);
		} else if (length < 1024 * 1024 * 1024) {
			return String.format("%.2f MB", length / (1024.0 * 1024));
		} else {
			return String.format("%.2f GB", length / (1024.0 * 1024 * 1024));
		}

	}

	public static String readFileToString(File file, Charset cs) throws IOException {
		return new String(readFileToByteArray(file), cs);
	}

	private static byte[] readFileToByteArray(File file) throws IOException {
		try (var inputStream = java.nio.file.Files.newInputStream(file.toPath())) {
			return inputStream.readAllBytes();
		} catch (IOException e) {
			throw e;
		}
	}

}
