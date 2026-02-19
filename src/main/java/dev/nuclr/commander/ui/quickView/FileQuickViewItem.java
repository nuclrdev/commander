package dev.nuclr.commander.ui.quickView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

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
		return file.getName().contains(".")
				? file.getName().substring(file.getName().lastIndexOf(".") + 1)
				: "";
	}

	@Override
	public String mimeType() {
		return null;
	}

	@Override
	public InputStream openStream() throws Exception {
		return new BufferedInputStream(new FileInputStream(file));
	}

}
