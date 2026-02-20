package dev.nuclr.commander.event;

import java.nio.file.Path;

import org.springframework.context.ApplicationEvent;

import lombok.Data;

@Data
public class QuickViewEvent extends ApplicationEvent {

	/** Path of the file to preview, or {@code null} to close quick view. */
	private Path path;

	public QuickViewEvent(Object source, Path path) {
		super(source);
		this.path = path;
	}

}
