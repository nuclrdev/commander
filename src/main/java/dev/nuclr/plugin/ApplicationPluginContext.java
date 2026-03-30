package dev.nuclr.plugin;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.nuclr.platform.Settings;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.plugin.event.bus.PluginEventBus;

public interface ApplicationPluginContext extends NuclrPluginContext {

	@Override
	PluginEventBus getEventBus();

	Map<String, Object> getGlobalData();

	@Override
	default Map<String, Object> getGlobalConfig() {
		return getGlobalData();
	}

	@Override
	ObjectMapper getObjectMapper();

	@Override
	Map<String, Object> getTheme();

	@Override
	Settings getSettings();
}
