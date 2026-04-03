package dev.nuclr.commander.event;

import lombok.Getter;

@Getter
public class ShowFilePanelsViewEvent {

	private final Object source;

	public ShowFilePanelsViewEvent(Object source) {
		this.source = source;
	}

}
