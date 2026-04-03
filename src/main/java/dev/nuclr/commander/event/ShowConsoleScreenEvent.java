package dev.nuclr.commander.event;

import lombok.Getter;

@Getter
public class ShowConsoleScreenEvent {

	private final Object source;

	public ShowConsoleScreenEvent(Object source) {
		this.source = source;
	}

}
