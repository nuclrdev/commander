package dev.nuclr.commander.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.google.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public final class AppEventBus {

	private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

	public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
		listeners.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>())
				.add(event -> listener.accept(eventType.cast(event)));
	}

	public void publish(Object event) {
		if (event == null) {
			return;
		}
		List<Consumer<Object>> eventListeners = listeners.get(event.getClass());
		if (eventListeners == null) {
			return;
		}
		for (Consumer<Object> listener : eventListeners) {
			try {
				listener.accept(event);
			} catch (Exception e) {
				log.error("App event listener failed for {}", event.getClass().getName(), e);
			}
		}
	}
}
