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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.nuclr.commander.common.ThemeSchemeStore;
import dev.nuclr.platform.Settings;
import dev.nuclr.platform.ThemeScheme;
import dev.nuclr.platform.events.NuclrEventBus;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import lombok.Data;

@Data
@Component
public final class DefaultApplicationPluginContext implements NuclrPluginContext {

	@Autowired
	private NuclrEventBus eventBus;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private Settings settings;

	@Autowired
	private ThemeSchemeStore themeSchemeStore;

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
