package dev.nuclr.commander.event;

import java.nio.file.Path;

import lombok.Data;

@Data
public final class ListViewFileOpen {

	private final Object source;

	private Path path;

	public ListViewFileOpen(Object source, Path path) {
		this.source = source;
		this.path = path;
	}

}
