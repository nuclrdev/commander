package dev.nuclr.commander.event;

import dev.nuclr.plugin.MenuResource;
import lombok.Getter;

@Getter
public class FunctionKeyCommandEvent {

	private final Object source;

	private final int functionKeyNumber;
	private final String label;
	private final MenuResource menuResource;

	public FunctionKeyCommandEvent(Object source, int functionKeyNumber, String label) {
		this(source, functionKeyNumber, label, null);
	}

	public FunctionKeyCommandEvent(Object source, int functionKeyNumber, String label, MenuResource menuResource) {
		this.source = source;
		this.functionKeyNumber = functionKeyNumber;
		this.label = label;
		this.menuResource = menuResource;
	}
}
