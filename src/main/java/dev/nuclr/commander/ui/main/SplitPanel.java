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

import java.awt.Font;
import java.nio.file.Path;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.plugin.PluginRegistry;
import dev.nuclr.commander.ui.quickView.QuickViewPanel;
import dev.nuclr.platform.Settings;
import dev.nuclr.platform.events.NuclrEventBus;
import dev.nuclr.platform.events.NuclrEventListener;
import dev.nuclr.plugin.NuclrResourcePath;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SplitPanel extends JPanel implements NuclrEventListener {

	public static final String SettingsNamespace = MainWindow.SettingsNamespace + ".SplitPanel.";

	private JSplitPane mainSplitPane;
	private JComponent leftComponent;
	private JComponent rightComponent;
	private double dividerRatio = 0.5;
	private boolean quickViewActive = false;

	@Autowired
	private QuickViewPanel quickViewPanel;
	
	@Autowired
	private Settings settings;

	@Autowired
	private NuclrEventBus eventBus;

	@Autowired
	private PluginRegistry pluginRegistry;

	private static enum Side {
		Left, Right, Fullscreen
	}

	@PostConstruct
	public void init() {

		log.info("Initializing FilePanel");
		
		eventBus.subscribe(this);

		dividerRatio = settings.getOrDefault(SettingsNamespace + "splitPanel", "dividerRatio", 0.5);

		mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, placeholder("Loading plugins..."),
				placeholder("Loading plugins..."));

		mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
			int loc = (int) evt.getNewValue();
			int paneWidth = mainSplitPane.getWidth();
			if (paneWidth > 0) {
				dividerRatio = Math.max(0.01, Math.min(0.99, loc / (double) paneWidth));
				saveDividerRatio(dividerRatio);
			}
		});
		
		this.setLayout(new java.awt.BorderLayout());
		this.add(mainSplitPane, java.awt.BorderLayout.CENTER);

		loadDefaultPanels();
		
	}

	private void loadDefaultPanels() {

		// Left
		{
			var plugin = pluginRegistry.getPluginInstance("dev.nuclr.plugin.core.panel.fs");
			var panel = plugin.panel();
			setLeftComponent(panel);
		}
		
		// Right
		{
			var plugin = pluginRegistry.getPluginInstance("dev.nuclr.plugin.core.panel.fs");
			var panel = plugin.panel();
			setRightComponent(panel);
		}
		
	}

	private void saveDividerRatio(double ratio) {
		settings.set(SettingsNamespace + "splitPanel", "dividerRatio", ratio);
	}

	private JLabel placeholder(String text) {
		JLabel label = new JLabel(text, JLabel.CENTER);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
		return label;
	}

	public void setLeftComponent(JComponent component) {
		this.leftComponent = component;
		this.mainSplitPane.setLeftComponent(component);
		updateSplitPane();
	}

	public void setRightComponent(JComponent component) {
		this.rightComponent = component;
		this.mainSplitPane.setRightComponent(component);
		updateSplitPane();
	}

	private void updateSplitPane() {
		this.revalidate();
		this.repaint();
	}

	private void restoreMainDividerLocation() {
		SwingUtilities.invokeLater(() -> {
			if (mainSplitPane.getLeftComponent() == null || mainSplitPane.getRightComponent() == null) {
				return;
			}
			mainSplitPane.setDividerLocation(dividerRatio);
		});
	}

	@Override
	public void handleMessage(String source, String type, Map<String, Object> event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isMessageSupported(String type) {
		// TODO Auto-generated method stub
		return false;
	}

	public void toggleQuickView() {
		
	}
	
	
	
}
