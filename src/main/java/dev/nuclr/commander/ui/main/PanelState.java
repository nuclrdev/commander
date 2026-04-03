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
	
	private final ArrayDeque<PanelLayer> stack = new ArrayDeque<>();

	private boolean isEmpty() {
		return stack.isEmpty();
	}

	private int stackSize() {
		return stack.size();
	}

	private void push(PanelLayer layer) {
		stack.addLast(layer);
	}

	private PanelLayer pop() {
		return stack.removeLast();
	}

	private boolean contains(ResourceContentPlugin provider) {
		return stack.stream().anyMatch(layer -> layer.provider == provider);
	}

	private PanelLayer bottom() {
		return stack.peekFirst();
	}

	private PanelLayer top() {
		return stack.peekLast();
	}

	private ResourceContentPlugin provider() {
		PanelLayer layer = top();
		return layer != null ? layer.provider : null;
	}

	private JComponent component() {
		PanelLayer layer = top();
		return layer != null ? layer.component : null;
	}

	private PluginPathResource currentResource() {
		PanelLayer layer = top();
		return layer != null ? layer.currentResource : null;
	}

	private void setCurrentResource(PluginPathResource resource) {
		PanelLayer layer = top();
		if (layer != null) {
			layer.currentResource = resource;
		}
	}
}