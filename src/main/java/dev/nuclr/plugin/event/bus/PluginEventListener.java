package dev.nuclr.plugin.event.bus;

import dev.nuclr.platform.events.NuclrEventListener;
import dev.nuclr.plugin.event.PluginEvent;

public interface PluginEventListener extends NuclrEventListener {

	void handleMessage(PluginEvent event);

	boolean isMessageSupported(PluginEvent event);
}
