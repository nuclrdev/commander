package dev.nuclr.commander.event;

import java.nio.file.Path;

import org.springframework.context.ApplicationEvent;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class FileSelectedEvent extends ApplicationEvent {

	private Path path;

	public FileSelectedEvent(Object source, Path path) {
		super(source);
		this.path = path;
		log.info("FileSelectedEvent: {}", path);
	}

}
