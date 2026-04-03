package dev.nuclr.commander.event;

import java.nio.file.Path;

import lombok.Data;

@Data
public class ShowEditorScreenEvent {

	private final Object source;

	private Path path;

	public ShowEditorScreenEvent(Object source, Path path) {
		this.source = source;
		this.path = path;
	}

}
