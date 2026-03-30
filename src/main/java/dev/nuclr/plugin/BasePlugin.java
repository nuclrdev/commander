package dev.nuclr.plugin;

import dev.nuclr.commander.plugin.PluginDescriptor;

public interface BasePlugin {

	default void load(ApplicationPluginContext pluginContext) throws Exception {
	}

	default void unload() throws Exception {
	}

	default PluginDescriptor getPluginInfo() {
		return null;
	}
}
