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
package dev.nuclr.commander.service;

import java.awt.Desktop;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JOptionPane;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.nuclr.commander.plugin.DefaultApplicationPluginContext;
import dev.nuclr.commander.plugin.PluginRegistry;
import dev.nuclr.commander.ui.common.Alerts;
import dev.nuclr.commander.ui.main.SplitPanel;
import dev.nuclr.platform.plugin.NuclrPluginRole;
import dev.nuclr.platform.plugin.NuclrResourcePath;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileSystemService {

    private final DefaultApplicationPluginContext defaultApplicationPluginContext;

	@Autowired
	private PluginRegistry pluginRegistry;
	
	@Autowired
	private SplitPanel splitPanel;

    FileSystemService(DefaultApplicationPluginContext defaultApplicationPluginContext) {
        this.defaultApplicationPluginContext = defaultApplicationPluginContext;
    }

	public void open(NuclrResourcePath nuclrPath) {

		if (nuclrPath == null || nuclrPath.getPath() == null || !Files.exists(nuclrPath.getPath())) {
			return;
		}

		// First step check if there is a plugin that supports opening this file type,
		// if so use it, otherwise use the system default application
		var pluginTemplate = pluginRegistry.getPluginByResource(nuclrPath, NuclrPluginRole.FilePanel);

		if (pluginTemplate != null) {
			
			log.info("Found plugin \"{}\" to open file {}", pluginTemplate.name(), nuclrPath.getPath());
			
			// replace the current panel with the new plugin view
			// TODO
			var pluginInstance = pluginRegistry.getPluginInstance(pluginTemplate.id());
			pluginInstance.openResource(nuclrPath, new AtomicBoolean(false));
			
			this.splitPanel.setPluginToActivePanel(pluginInstance);
			
			pluginInstance.onFocusGained();
			
			return;
			
		}

		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			Alerts.showMessageDialog(null,
					"Opening items with the system default application is not supported on this platform.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		try {
			Desktop.getDesktop().open(nuclrPath.getPath().toFile());
		} catch (Exception ex) {
			Alerts.showMessageDialog(null, "Cannot open item " + nuclrPath.getPath().toFile(), "Error", JOptionPane.ERROR_MESSAGE);
		}

	}

}
