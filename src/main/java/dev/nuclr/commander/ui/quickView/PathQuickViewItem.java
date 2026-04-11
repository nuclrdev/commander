package dev.nuclr.commander.ui.quickView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import dev.nuclr.platform.plugin.NuclrResourcePath;
import lombok.Data;

/**
 * {@link NuclrResourcePath} adapter for a NIO.2 {@link Path}.
 *
 * <p>Works with any filesystem backend (local, ZIP, SFTP, etc.) — plugins
 * receive a plain {@link InputStream} and never see the underlying path.
 */
@Data
public class PathQuickViewItem extends NuclrResourcePath {

	private final Path path;

	public PathQuickViewItem(Path path) {
		this.path = path;
		setUuid(UUID.randomUUID().toString());
		setName(name());
		setSizeBytes(sizeBytes());
		setExtension(extension());
		setMimeType(mimeType());
	}

	public String name() {
		if (path == null) {
			return "";
		}
		var fn = path.getFileName();
		return fn != null ? fn.toString() : path.toString();
	}

	public long sizeBytes() {
		if (path == null) {
			return 0L;
		}
		try {
			return Files.size(path);
		} catch (Exception e) {
			return 0L;
		}
	}

	public String extension() {
		String n = name();
		if (n.isEmpty()) {
			return "";
		}
		int dot = n.lastIndexOf('.');
		if (dot >= 0) {
			return n.substring(dot + 1);
		}
		return n;
	}

	public String mimeType() {
		if (path == null) {
			return null;
		}
		try {
			return Files.probeContentType(path);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public InputStream openStream() throws IOException {
		Objects.requireNonNull(path, "path");
		return new BufferedInputStream(Files.newInputStream(path));
	}
}
