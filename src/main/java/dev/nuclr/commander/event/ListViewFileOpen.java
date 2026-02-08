package dev.nuclr.commander.event;

import java.io.File;

import org.springframework.context.ApplicationEvent;

import lombok.Data;

@Data
public final class ListViewFileOpen extends ApplicationEvent {

	private File file;

	public ListViewFileOpen(Object source, File file) {
		super(source);
		this.file = file;
	}
	

}