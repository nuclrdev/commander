package dev.nuclr.commander.event;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

@Getter
public class FunctionKeyCommandEvent extends ApplicationEvent {

	private final int functionKeyNumber;
	private final String label;

	public FunctionKeyCommandEvent(Object source, int functionKeyNumber, String label) {
		super(source);
		this.functionKeyNumber = functionKeyNumber;
		this.label = label;
	}
}
