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
package dev.nuclr.commander.ui.main;

import java.util.ArrayDeque;

import javax.swing.JComponent;

import dev.nuclr.plugin.PluginPathResource;
import dev.nuclr.plugin.ResourceContentPlugin;

public class PanelState {

	public final ArrayDeque<PanelLayer> stack = new ArrayDeque<>();

	public boolean isEmpty() {
		return stack.isEmpty();
	}

	public int stackSize() {
		return stack.size();
	}

	public void push(PanelLayer layer) {
		stack.addLast(layer);
	}

	public PanelLayer pop() {
		return stack.removeLast();
	}

	public boolean contains(ResourceContentPlugin provider) {
		return stack.stream().anyMatch(layer -> layer.provider == provider);
	}

	public PanelLayer bottom() {
		return stack.peekFirst();
	}

	public PanelLayer top() {
		return stack.peekLast();
	}

	public ResourceContentPlugin provider() {
		PanelLayer layer = top();
		return layer != null ? layer.provider : null;
	}

	public JComponent component() {
		PanelLayer layer = top();
		return layer != null ? layer.component : null;
	}

	public PluginPathResource currentResource() {
		PanelLayer layer = top();
		return layer != null ? layer.currentResource : null;
	}

	public void setCurrentResource(PluginPathResource resource) {
		PanelLayer layer = top();
		if (layer != null) {
			layer.currentResource = resource;
		}
	}
}