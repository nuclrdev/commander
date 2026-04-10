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
import java.awt.event.ActionEvent;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.plugin.PluginRegistry;
import dev.nuclr.commander.ui.quickView.PathQuickViewItem;
import dev.nuclr.commander.ui.quickView.QuickViewPanel;
import dev.nuclr.platform.NuclrSettings;
import dev.nuclr.platform.events.NuclrEventBus;
import dev.nuclr.platform.events.NuclrEventListener;
import dev.nuclr.platform.plugin.NuclrPlugin;
import dev.nuclr.platform.plugin.NuclrResourcePath;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SplitPanel extends JPanel implements NuclrEventListener {

	public static final String SettingsNamespace = MainWindow.SettingsNamespace + ".SplitPanel.";

	private JSplitPane mainSplitPane;
	private NuclrPlugin leftPlugin;
	private NuclrPlugin rightPlugin;
	private double dividerRatio = 0.5;
	private boolean isQuickViewActive = false;
	private PathQuickViewItem selectedPath;

	private NuclrPlugin preQuickViewPlugin;

	@Autowired
	private QuickViewPanel quickViewPanel;

	@Autowired
	private NuclrSettings settings;

	@Autowired
	private NuclrEventBus eventBus;

	@Autowired
	private PluginRegistry pluginRegistry;

	private static enum Side {
		Left, Right, Fullscreen
	}

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

		// Keyboard input for quick view toggle
		var inputMap = this.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		{
			
			// Ctrl + Q for quick view toggle
			inputMap.put(KeyStroke.getKeyStroke("control pressed Q"), "quickView");
			this.getActionMap().put("quickView", new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e) {
					toggleQuickView();
				}
			});
			
		}

	}

	private Path getDefaultDrivePath() {

		String os = System.getProperty("os.name").toLowerCase();

		if (os.contains("win")) {
			// Windows: return a virtual "This PC" root — use null-root path
			// FileSystems.getDefault().getRootDirectories() gives C:\, D:\, etc.
			// But "This PC" itself has no real Path equivalent; conventionally use the
			// first root
			// or a sentinel. Here we return the user's home drive root.
			Path home = Path.of(System.getProperty("user.home"));
			return home.getRoot(); // e.g. C:\
		}

		// Unix / macOS / Linux
		return Path.of("/");
	}

	private void loadDefaultPanels() {

		var resource = new NuclrResourcePath();
		resource.setPath(getDefaultDrivePath());

		// Left
		{
			var plugin = pluginRegistry.getPluginInstance("dev.nuclr.plugin.core.panel.fs");
			plugin.openResource(resource, new AtomicBoolean(false));
			setLeftComponent(plugin);
		}

		// Right
		{
			var plugin = pluginRegistry.getPluginInstance("dev.nuclr.plugin.core.panel.fs");
			plugin.openResource(resource, new AtomicBoolean(false));
			setRightComponent(plugin);
			plugin.onFocusGained();
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

	public void setLeftComponent(NuclrPlugin component) {

		assert component != null : "Left component cannot be null";

		if (component == null) {
			return;
		}

		this.leftPlugin = component;
		this.mainSplitPane.setLeftComponent(component.panel());
		updateSplitPane();
	}

	public void setRightComponent(NuclrPlugin component) {

		assert component != null : "Right component cannot be null";

		if (component == null) {
			return;
		}

		this.rightPlugin = component;
		this.mainSplitPane.setRightComponent(component.panel());
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
	public void handleMessage(Object source, String type, Map<String, Object> event) {

		if (type.equals("fs.path.selected")) {
			var path = (Path) event.get("path");
			this.selectedPath = new PathQuickViewItem(path);
			log.info("Selected path updated to: " + this.selectedPath);

			if (isQuickViewActive()) {
				quickViewPanel.show(this.selectedPath.getPath());
			}

		}

	}

	@Override
	public boolean isMessageSupported(String type) {
		return type.equals("fs.path.selected");
	}

	public boolean switchFocus() {

		if (leftPlugin == null && rightPlugin == null) {
			log.info("No plugins to switch focus to.");
			return false;
		}

		if (leftPlugin.isFocused()) {
			leftPlugin.onFocusLost();
			rightPlugin.onFocusGained();
		} else {
			leftPlugin.onFocusGained();
			rightPlugin.onFocusLost();
		}

		return true;
	}

	public NuclrPlugin getFocusedPlugin() {
		if (leftPlugin != null && leftPlugin.isFocused()) {
			return leftPlugin;
		}
		if (rightPlugin != null && rightPlugin.isFocused()) {
			return rightPlugin;
		}
		return rightPlugin != null ? rightPlugin : leftPlugin;
	}

	public NuclrResourcePath getSelectedResource() {
		return selectedPath;
	}

	private void toggleQuickView() {

		if (isQuickViewActive()) {

			log.info("Toggling Quick View: Deactivating");

			quickViewPanel.setOnProviderChanged(null);

			if (leftPlugin.isFocused()) {
				setRightComponent(preQuickViewPlugin);
			} else {
				setLeftComponent(preQuickViewPlugin);
			}

			preQuickViewPlugin = null;

		} else {

			log.info("Toggling Quick View: Activating");

			quickViewPanel.setOnProviderChanged(this::restoreMainDividerLocation);
			quickViewPanel.show(this.selectedPath.getPath());

			if (leftPlugin.isFocused()) {
				preQuickViewPlugin = this.rightPlugin;
				mainSplitPane.setRightComponent(quickViewPanel.getPanel());
			} else {
				preQuickViewPlugin = this.leftPlugin;
				mainSplitPane.setLeftComponent(quickViewPanel.getPanel());
			}

		}

		setQuickViewActive(!isQuickViewActive());
		
		restoreMainDividerLocation();
	}

	private boolean isQuickViewActive() {
		return isQuickViewActive;
	}

	private void setQuickViewActive(boolean active) {
		isQuickViewActive = active;
	}

}
