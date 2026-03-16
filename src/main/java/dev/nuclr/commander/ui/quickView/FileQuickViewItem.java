package dev.nuclr.commander.ui.quickView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.UUID;

import dev.nuclr.plugin.PluginPathResource;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileQuickViewItem extends PluginPathResource {

	private File file;

	{
		setUuid(UUID.randomUUID().toString());
		setName(name());
		setSizeBytes(sizeBytes());
		setExtension(extension());
		setMimeType(mimeType());
	}

	public String name() {
		return file.getName();
	}

	public long sizeBytes() {
		return file.length();
	}

	public String extension() {
		String name = file.getName();
		return name.contains(".")
				? name.substring(name.lastIndexOf(".") + 1)
				: name;
	}

	public String mimeType() {
		return null;
	}

	@Override
	public InputStream openStream() throws Exception {
		return new BufferedInputStream(new FileInputStream(file));
	}
}
