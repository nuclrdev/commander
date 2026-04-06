/*

	Copyright 2026 Sergio, Nuclr (https://nuclr.dev)
	
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	
	http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

*/
package dev.nuclr.commander.plugin;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

import dev.nuclr.platform.events.NuclrEventBus;
import dev.nuclr.platform.events.NuclrEventListener;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public final class DefaultPluginEventBus implements NuclrEventBus {

	private List<NuclrEventListener> listeners = new CopyOnWriteArrayList<>();

	@Override
	public void emit(String source, String type, Map<String, Object> event) {
		for (NuclrEventListener listener : listeners) {
			try {
				if (listener.isMessageSupported(type)) {
					listener.handleMessage(source, type, event);
				}
			} catch (Exception e) {
				log.error("Error in event listener: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	@Override
	public void subscribe(NuclrEventListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void unsubscribe(NuclrEventListener listener) {
		this.listeners.remove(listener);
	}

	@Override
	public void emit(String type, Map<String, Object> event) {
		this.emit("default", type, event);
	}

	@Override
	public void emit(String type) {
		this.emit("default", type, Map.of());
	}

}
