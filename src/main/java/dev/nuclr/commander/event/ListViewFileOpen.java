package dev.nuclr.commander.event;

import java.nio.file.Path;

import org.springframework.context.ApplicationEvent;

import lombok.Data;

@Data
public final class ListViewFileOpen extends ApplicationEvent {

	private Path path;

	public ListViewFileOpen(Object source, Path path) {
		super(source);
		this.path = path;
	}

}
