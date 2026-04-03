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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.nuclr.commander.common.ThemeSchemeStore;
import dev.nuclr.platform.Settings;
import dev.nuclr.platform.ThemeScheme;
import dev.nuclr.platform.events.NuclrEventBus;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import lombok.Data;

@Data
@Singleton
public final class DefaultApplicationPluginContext implements NuclrPluginContext {

	private final NuclrEventBus eventBus;

	private final ObjectMapper objectMapper;

	private final Settings settings;

	private final ThemeSchemeStore themeSchemeStore;

	@Inject
	public DefaultApplicationPluginContext(
			NuclrEventBus eventBus,
			ObjectMapper objectMapper,
			Settings settings,
			ThemeSchemeStore themeSchemeStore) {
		this.eventBus = eventBus;
		this.objectMapper = objectMapper;
		this.settings = settings;
		this.themeSchemeStore = themeSchemeStore;
	}

	public NuclrEventBus getEventBus() {
		return eventBus;
	}

	@Override
	public Settings getSettings() {
		return settings;
	}

	@Override
	public ThemeScheme getTheme() {
		return themeSchemeStore.loadOrDefault().activeThemeScheme();
	}

}
