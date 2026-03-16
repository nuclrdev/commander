package dev.nuclr.commander.ui.quickView;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import dev.nuclr.plugin.PluginPathResource;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * {@link PluginPathResource} adapter for a NIO.2 {@link Path}.
 *
 * <p>Works with any filesystem backend (local, ZIP, SFTP, etc.) — plugins
 * receive a plain {@link InputStream} and never see the underlying path.
 */
@Data
@AllArgsConstructor
public class PathQuickViewItem extends PluginPathResource {

	private final Path path;

	{
		setUuid(UUID.randomUUID().toString());
		setName(name());
		setSizeBytes(sizeBytes());
		setExtension(extension());
		setMimeType(mimeType());
	}

	public String name() {
		var fn = path.getFileName();
		return fn != null ? fn.toString() : path.toString();
	}

	public long sizeBytes() {
		try {
			return Files.size(path);
		} catch (Exception e) {
			return 0L;
		}
	}

	public String extension() {
		String n = name();
		int dot = n.lastIndexOf('.');
		if (dot >= 0) {
			return n.substring(dot + 1);
		}
		return n;
	}

	public String mimeType() {
		try {
			return Files.probeContentType(path);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public InputStream openStream() throws Exception {
		return new BufferedInputStream(Files.newInputStream(path));
	}
}
