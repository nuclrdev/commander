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
package dev.nuclr.commander.ui.quickView;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.plugin.NuclrPlugin;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrPluginRole;
import dev.nuclr.platform.plugin.NuclrResourcePath;

public abstract class InternalPlugin implements NuclrPlugin {

	protected NuclrResourcePath currentResource;

	@Override
	public String name() {
		return id();
	}

	@Override
	public String version() {
		return "1.0.0";
	}

	@Override
	public String description() {
		return id();
	}

	@Override
	public String author() {
		return "";
	}

	@Override
	public String license() {
		return "";
	}

	@Override
	public String website() {
		return "";
	}

	@Override
	public String pageUrl() {
		return "";
	}

	@Override
	public String docUrl() {
		return "";
	}

	@Override
	public Developer type() {
		return Developer.Official;
	}

	@Override
	public NuclrPluginRole role() {
		return NuclrPluginRole.QuickViewer;
	}

	@Override
	public void load(NuclrPluginContext context, boolean isTemplate) {
	}

	@Override
	public void unload() {
	}

	@Override
	public void closeResource() {
	}

	@Override
	public NuclrResourcePath getCurrentResource() {
		return this.currentResource;
	}

	@Override
	public int priority() {
		return 0;
	}

	@Override
	public void updateTheme(NuclrThemeScheme themeScheme) {
	}

	@Override
	public String uuid() {
		return id();
	}

	@Override
	public boolean isFocused() {
		return false;
	}

	@Override
	public boolean onFocusGained() {
		return false;
	}

	@Override
	public void onFocusLost() {
	}

}
