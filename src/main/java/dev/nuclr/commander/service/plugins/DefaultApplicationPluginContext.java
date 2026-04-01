package dev.nuclr.commander.service.plugins;

import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.nuclr.platform.Settings;
import lombok.Data;

@Data
private final class DefaultApplicationPluginContext implements ApplicationPluginContext {
	
	private final PluginEventBus eventBus = new DefaultPluginEventBus();
	private final ConcurrentHashMap<String, Object> globalData = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Object> theme = new ConcurrentHashMap<>();
	private final Settings settings = new InMemorySettings();
	private ObjectMapper objectMapper = new ObjectMapper();
}