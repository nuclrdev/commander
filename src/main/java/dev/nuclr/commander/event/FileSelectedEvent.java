package dev.nuclr.commander.event;

import java.io.File;

import org.springframework.context.ApplicationEvent;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class FileSelectedEvent extends ApplicationEvent {

	private File file;
	
	public FileSelectedEvent(Object source, File file) {
		super(source);
		this.file = file;
		log.info("FileSelectedEvent created for file: {}", file.getAbsolutePath());
	}


}
