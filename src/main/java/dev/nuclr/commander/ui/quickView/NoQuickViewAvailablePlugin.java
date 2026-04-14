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

import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.plugin.NuclrPlugin;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrPluginRole;
import dev.nuclr.platform.plugin.NuclrResourcePath;

@Component
public class NoQuickViewAvailablePlugin implements NuclrPlugin {
	
	@Autowired
	private NoQuickViewAvailablePanel panel;

	@Override
	public boolean onFocusGained() {
		return false;
	}

	@Override
	public void onFocusLost() {
	}

	@Override
	public boolean isFocused() {
		return false;
	}

	@Override
	public String id() {
		return null;
	}

	@Override
	public String name() {
		return null;
	}

	@Override
	public String version() {
		return "0";
	}

	@Override
	public String description() {
		return null;
	}

	@Override
	public String author() {
		return null;
	}

	@Override
	public String license() {
		return null;
	}

	@Override
	public String website() {
		return null;
	}

	@Override
	public String pageUrl() {
		return null;
	}

	@Override
	public String docUrl() {
		return null;
	}

	@Override
	public Developer type() {
		return null;
	}

	@Override
	public JComponent panel() {
		return panel;
	}

	@Override
	public boolean supports(NuclrResourcePath resource) {
		return true;
	}

	@Override
	public void load(NuclrPluginContext context, boolean template) {

	}

	@Override
	public void unload() {

	}

	@Override
	public boolean openResource(NuclrResourcePath resource, AtomicBoolean cancelled) {
		panel.setPath(resource.getPath());
		return true;
	}

	@Override
	public void closeResource() {
	}

	@Override
	public int priority() {
		return 0;
	}

	@Override
	public void updateTheme(NuclrThemeScheme themeScheme) {

	}

	@Override
	public NuclrPluginRole role() {
		return null;
	}

}
