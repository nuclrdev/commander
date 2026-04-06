package dev.nuclr.commander.event;

import org.springframework.context.ApplicationEvent;

import dev.nuclr.plugin.NuclrMenuResource;
import lombok.Getter;

@Getter
public class FunctionKeyCommandEvent extends ApplicationEvent {

	private final int functionKeyNumber;
	private final String label;
	private final NuclrMenuResource menuResource;

	public FunctionKeyCommandEvent(Object source, int functionKeyNumber, String label) {
		this(source, functionKeyNumber, label, null);
	}

	public FunctionKeyCommandEvent(Object source, int functionKeyNumber, String label, NuclrMenuResource menuResource) {
		super(source);
		this.functionKeyNumber = functionKeyNumber;
		this.label = label;
		this.menuResource = menuResource;
	}
}
