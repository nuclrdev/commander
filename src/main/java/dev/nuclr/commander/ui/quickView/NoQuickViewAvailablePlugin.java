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

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import dev.nuclr.platform.ThemeScheme;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.plugin.NuclrPlugin;
import dev.nuclr.plugin.NuclrResourcePath;

@Component
@Lazy
public class NoQuickViewAvailablePlugin implements NuclrPlugin {
	
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
	public int version() {
		return 0;
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
		
		if (panel == null) {
			panel = new NoQuickViewAvailablePanel();
		}
		
		return panel;
	}

	@Override
	public boolean supports(NuclrResourcePath resource) {
		return true;
	}

	@Override
	public void load(NuclrPluginContext context) {

	}

	@Override
	public void unload() {

	}

	@Override
	public boolean openResource(NuclrResourcePath resource, AtomicBoolean cancelled) {
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
	public void updateTheme(ThemeScheme themeScheme) {

	}

}
