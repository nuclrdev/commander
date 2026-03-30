package dev.nuclr.plugin.event;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class PluginEvent {

	private boolean handled;
}
