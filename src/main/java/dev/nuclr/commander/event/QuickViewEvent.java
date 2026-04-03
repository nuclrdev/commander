package dev.nuclr.commander.event;

import java.nio.file.Path;

import lombok.Data;

@Data
public class QuickViewEvent {

	private final Object source;

	/** Path of the file to preview, or {@code null} to close quick view. */
	private Path path;

	public QuickViewEvent(Object source, Path path) {
		this.source = source;
		this.path = path;
	}

}
