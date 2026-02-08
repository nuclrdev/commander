package dev.nuclr.commander.event;

import org.springframework.context.ApplicationEvent;

public class ShowEditorScreenEvent extends ApplicationEvent {

	public ShowEditorScreenEvent(Object source) {
		super(source);
	}

}
