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

import javax.swing.JComponent;

import dev.nuclr.plugin.PluginPathResource;
import dev.nuclr.plugin.ResourceContentPlugin;
import lombok.Data;

@Data
public class PanelLayer {

	public final ResourceContentPlugin provider;
	public final JComponent component;
	public PluginPathResource currentResource;

	public PanelLayer(ResourceContentPlugin provider, JComponent component, PluginPathResource currentResource) {
		this.provider = provider;
		this.component = component;
		this.currentResource = currentResource;
	}

}