package dev.nuclr.commander.ui.quickView;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.nuclr.plugin.QuickViewItem;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * {@link QuickViewItem} adapter for a NIO.2 {@link Path}.
 *
 * <p>Works with any filesystem backend (local, ZIP, SFTP, etc.) — plugins
 * receive a plain {@link InputStream} and never see the underlying path.
 */
@Data
@AllArgsConstructor
public class PathQuickViewItem implements QuickViewItem {

	private final Path path;

	@Override
	public String name() {
		var fn = path.getFileName();
		return fn != null ? fn.toString() : path.toString();
	}

	@Override
	public long sizeBytes() {
		try {
			return Files.size(path);
		} catch (Exception e) {
			return 0L;
		}
	}

	@Override
	public String extension() {
		String n = name();
		int dot = n.lastIndexOf('.');
		return dot >= 0 ? n.substring(dot + 1) : "";
	}

	@Override
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
