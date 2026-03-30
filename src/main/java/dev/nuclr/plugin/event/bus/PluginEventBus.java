package dev.nuclr.plugin.event.bus;

import dev.nuclr.platform.events.NuclrEventBus;
import dev.nuclr.plugin.event.PluginEvent;

public interface PluginEventBus extends NuclrEventBus {

	void emit(PluginEvent event);

	void subscribe(PluginEventListener listener);

	void unsubscribe(PluginEventListener listener);
}
