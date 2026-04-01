package dev.nuclr.commander.service;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import dev.nuclr.platform.Settings;

@Lazy
@Component
public final class InMemorySettings implements Settings {

	private final ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> namespaces = new ConcurrentHashMap<>();

	@Override
	public void set(String namespace, String key, Object value) {
		namespaces.computeIfAbsent(namespace, unused -> new ConcurrentHashMap<>()).put(key, value);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(String namespace, String key) {
		return (T) namespaces.getOrDefault(namespace, new ConcurrentHashMap<>()).get(key);
	}

	@Override
	public <T> T getOrDefault(String namespace, String key, T defaultValue) {
		T value = get(namespace, key);
		return value != null ? value : defaultValue;
	}
}