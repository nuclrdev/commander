package dev.nuclr.commander.ui.quickView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;

import dev.nuclr.plugin.QuickViewItem;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileQuickViewItem implements QuickViewItem {

	private File file;

	@Override
	public String name() {
		return file.getName();
	}

	@Override
	public long sizeBytes() {
		return file.length();
	}

	@Override
	public String extension() {
		String name = file.getName();
		return name.contains(".")
				? name.substring(name.lastIndexOf(".") + 1)
				: name;
	}

	@Override
	public String mimeType() {
		return null;
	}

	@Override
	public InputStream openStream() throws Exception {
		return new BufferedInputStream(new FileInputStream(file));
	}

	@Override
	public Path path() {
		return file.toPath();
	}

}
