package dev.nuclr.commander.event;

import org.springframework.context.ApplicationEvent;

public class ShowConsoleScreenEvent extends ApplicationEvent {

	public ShowConsoleScreenEvent(Object source) {
		super(source);
	}

}
