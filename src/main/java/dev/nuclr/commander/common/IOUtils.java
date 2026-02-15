package dev.nuclr.commander.common;

import java.io.InputStream;
import java.nio.charset.Charset;

public final class IOUtils {

	public static byte[] toByteArray(InputStream resourceAsStream) {
		try (var is = resourceAsStream) {
			return is.readAllBytes();
		} catch (Exception e) {
			throw new RuntimeException(
					"Failed to read resource as byte array: " + e.getMessage(),
					e);
		}
	}

	public static String toString(InputStream resourceAsStream, Charset cs) {
		return new String(toByteArray(resourceAsStream), cs);
	}

}
