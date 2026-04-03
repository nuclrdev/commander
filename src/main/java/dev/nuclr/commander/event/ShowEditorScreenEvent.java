package dev.nuclr.commander.event;

import java.nio.file.Path;

import org.springframework.context.ApplicationEvent;

import lombok.Data;

@Data
public class ShowEditorScreenEvent extends ApplicationEvent {

	private Path path;

	public ShowEditorScreenEvent(Object source, Path path) {
		super(source);
		this.path = path;
	}

}
