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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneDivider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.plugin.PluginRegistry;
import dev.nuclr.commander.service.FileSystemService;
import dev.nuclr.commander.ui.quickView.PathQuickViewItem;
import dev.nuclr.commander.ui.quickView.QuickViewPanel;
import dev.nuclr.platform.NuclrSettings;
import dev.nuclr.platform.events.NuclrEventBus;
import dev.nuclr.platform.events.NuclrEventListener;
import dev.nuclr.platform.plugin.NuclrPlugin;
import dev.nuclr.platform.plugin.NuclrPluginRole;
import dev.nuclr.platform.plugin.NuclrResourcePath;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SplitPanel implements NuclrEventListener {

	public static final String SettingsNamespace = MainWindow.SettingsNamespace + ".SplitPanel.";
	private static final int DIVIDER_STEP_PIXELS = 30;

	private JPanel container;
	
	private JSplitPane mainSplitPane;
	
	private boolean isQuickViewActive = false;
	
	private NuclrResourcePath selectedPath;

	private NuclrPlugin preQuickViewPlugin;

	@Autowired
	private QuickViewPanel quickViewPanel;

	@Autowired
	private NuclrSettings settings;

	@Autowired
	private NuclrEventBus eventBus;

	@Autowired
	private PluginRegistry pluginRegistry;
	
	@Autowired
	private FileSystemService fileSystemService;
	
	private final Deque<NuclrPlugin> leftViewStack = new ArrayDeque<>();
	private final Deque<NuclrPlugin> rightViewStack = new ArrayDeque<>();

	private static enum Side {
		Left, Right, Fullscreen
	}
	
	public SplitPanel() {
		this.container = new JPanel();
	}

	public void init() {

		log.info("Initializing FilePanel");
		
		eventBus.subscribe(this);

		mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, placeholder("Loading plugins..."),
				placeholder("Loading plugins..."));

		for (var c : mainSplitPane.getComponents()) {
		    if (c instanceof BasicSplitPaneDivider divider) {
		        divider.addMouseListener(new MouseAdapter() {
		            @Override
		            public void mouseReleased(MouseEvent e) {
		                log.info("User finished dragging: " 
		                    + mainSplitPane.getDividerLocation());
		                saveDividerLocation(mainSplitPane.getDividerLocation()); // Save the new location
		            }
		        });
		    }
		}
		
		this.container.setLayout(new java.awt.BorderLayout());
		this.container.add(mainSplitPane, java.awt.BorderLayout.CENTER);

		loadDefaultPanels();

		// Keyboard input for quick view toggle
		var inputMap = this.container.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		{
			
			// Ctrl + Q for quick view toggle
			inputMap.put(KeyStroke.getKeyStroke("control pressed Q"), "quickView");
			this.container.getActionMap().put("quickView", new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e) {
					toggleQuickView();
				}
			});

		}
		
		SwingUtilities.invokeLater(() -> {
			restoreMainDividerLocation();
		});

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

	private JLabel placeholder(String text) {
		JLabel label = new JLabel(text, JLabel.CENTER);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
		return label;
	}

	public void setLeftComponent(NuclrPlugin plugin) {

		assert plugin != null : "Left component cannot be null";

		if (plugin == null) {
			return;
		}

		int dividerLocation = getCurrentOrSavedDividerLocation();
		this.mainSplitPane.setLeftComponent(plugin.panel());
		
		updateSplitPane(dividerLocation);
		
		leftViewStack.push(plugin);
		
	}

	public void setRightComponent(NuclrPlugin plugin) {

		assert plugin != null : "Right component cannot be null";

		if (plugin == null) {
			return;
		}

		int dividerLocation = getCurrentOrSavedDividerLocation();
		this.mainSplitPane.setRightComponent(plugin.panel());
		
		updateSplitPane(dividerLocation);
		
		rightViewStack.push(plugin);
	}

	private void updateSplitPane(int dividerLocation) {
		Runnable update = () -> {
			this.container.revalidate();
			mainSplitPane.setDividerLocation(dividerLocation);
			this.container.repaint();
		};
		if (SwingUtilities.isEventDispatchThread()) {
			update.run();
		} else {
			SwingUtilities.invokeLater(update);
		}
	}
	
	private NuclrPlugin getCurrentLeftPlugin() {
		return leftViewStack.peek();
	}
	
	private NuclrPlugin getCurrentRightPlugin() {
		return rightViewStack.peek();
	}

	public boolean switchFocus() {

		var leftPlugin = getCurrentLeftPlugin();
		var rightPlugin = getCurrentRightPlugin();
		
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
		
		var leftPlugin = getCurrentLeftPlugin();
		var rightPlugin = getCurrentRightPlugin();
		
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

	public NuclrResourcePath getLeftResource() {
		var leftPlugin = getCurrentLeftPlugin();
		return leftPlugin != null ? leftPlugin.getCurrentResource() : null;
	}

	public NuclrResourcePath getRightResource() {
		var rightPlugin = getCurrentRightPlugin();
		return rightPlugin != null ? rightPlugin.getCurrentResource() : null;
	}

	public JComponent getLeftAnchorComponent() {
		return resolveAnchorComponent(Side.Left);
	}

	public JComponent getRightAnchorComponent() {
		return resolveAnchorComponent(Side.Right);
	}

	private JComponent resolveAnchorComponent(Side side) {
		java.awt.Component visibleComponent = side == Side.Left
				? mainSplitPane.getLeftComponent()
				: mainSplitPane.getRightComponent();
		if (visibleComponent instanceof JComponent visible && visible.isShowing()) {
			return visible;
		}

		NuclrPlugin plugin = side == Side.Left ? getCurrentLeftPlugin() : getCurrentRightPlugin();
		if (plugin != null) {
			JComponent panel = plugin.panel();
			if (panel != null && panel.isShowing()) {
				return panel;
			}
		}
		return container;
	}

	public boolean openLeftResource(NuclrResourcePath resource) {
		return openResourceOnSide(resource, true);
	}

	public boolean openRightResource(NuclrResourcePath resource) {
		return openResourceOnSide(resource, false);
	}

	private void toggleQuickView() {

		var leftPlugin = getCurrentLeftPlugin();
		var rightPlugin = getCurrentRightPlugin();
		
		if (isQuickViewActive()) {

			log.info("Toggling Quick View: Deactivating");

			quickViewPanel.setOnProviderChanged(null);
			quickViewPanel.stop();

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
				preQuickViewPlugin = rightPlugin;
				mainSplitPane.setRightComponent(quickViewPanel.getPanel());
			} else {
				preQuickViewPlugin = leftPlugin;
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
	
	private void restoreMainDividerLocation() {
		restoreMainDividerLocation(getDividerLocation());
	}

	private void restoreMainDividerLocation(int dividerLocation) {
		SwingUtilities.invokeLater(() -> {
			mainSplitPane.setDividerLocation(dividerLocation);
			log.info("Restored main divider location: " + dividerLocation);
		});
	}

	private int getCurrentOrSavedDividerLocation() {
		int dividerLocation = mainSplitPane.getDividerLocation();
		if (dividerLocation <= 0) {
			return getDividerLocation();
		}
		return dividerLocation;
	}
	
	private int getDividerLocation() {
		var divider = settings.getOrDefault(SettingsNamespace + "splitPanel", "dividerLocation", (int) ( this.mainSplitPane.getWidth() / 2));
		log.info("Loaded main divider location ratio: " + divider);
		
		// If divider location is off the window size, just make it in the middle of the window
		if (divider <= 0 || divider >= this.mainSplitPane.getWidth()) {
			divider = this.mainSplitPane.getWidth() / 2;
			log.info("Saved divider location was out of bounds, defaulting to: " + divider);
		}
		
		return divider;
	}

	private void saveDividerLocation(int dividerLocation) {
		settings.set(SettingsNamespace + "splitPanel", "dividerLocation", dividerLocation);
		log.info("Saved main divider location: " + dividerLocation);
	}

	public void moveDividerLeft() {
		moveDividerBy(-DIVIDER_STEP_PIXELS);
	}

	public void moveDividerRight() {
		moveDividerBy(DIVIDER_STEP_PIXELS);
	}

	private void moveDividerBy(int delta) {
		if (mainSplitPane == null) {
			return;
		}

		int currentLocation = mainSplitPane.getDividerLocation();
		if (currentLocation <= 0) {
			currentLocation = getDividerLocation();
		}

		int minimumLocation = mainSplitPane.getMinimumDividerLocation();
		int maximumLocation = mainSplitPane.getMaximumDividerLocation();
		int newLocation = Math.max(minimumLocation, Math.min(maximumLocation, currentLocation + delta));

		mainSplitPane.setDividerLocation(newLocation);
		saveDividerLocation(newLocation);
	}

	private boolean openResourceOnSide(NuclrResourcePath resource, boolean leftSide) {
		if (resource == null) {
			return false;
		}

		NuclrPlugin template = pluginRegistry.getPluginByResource(resource, NuclrPluginRole.FilePanel);
		if (template == null || template.id() == null) {
			log.warn("No plugin supports change-drive resource: {}", resource.getName());
			return false;
		}

		NuclrPlugin plugin = pluginRegistry.getPluginInstance(template.id());
		if (plugin == null || !plugin.openResource(resource, new AtomicBoolean(false))) {
			log.warn("Failed to open change-drive resource [{}] with plugin [{}]", resource.getName(), template.id());
			return false;
		}

		var leftPlugin = getCurrentLeftPlugin();
		var rightPlugin = getCurrentRightPlugin();

		if (leftSide) {
			if (rightPlugin != null) {
				rightPlugin.onFocusLost();
			}
			setLeftComponent(plugin);
			plugin.onFocusGained();
		} else {
			if (leftPlugin != null) {
				leftPlugin.onFocusLost();
			}
			setRightComponent(plugin);
			plugin.onFocusGained();
		}

		return true;
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

		} else if (type.equals("fs.path.opened")) {
			
			var path = (Path) event.get("path");
			
			this.selectedPath = new NuclrResourcePath(path);
			
			log.info("Opened path updated to: " + this.selectedPath);

			fileSystemService.open(selectedPath);
		
		} else if (type.equals("plugin.unload")) {
			
			unloadPluginIfPresent((String)event.get("uuid"));
			
		}

	}

	/**
	 * Find a plugin in both left/right stacks, remove it, and if it was the active one, switch to the next plugin in the stack or placeholder.
	 */
	private void unloadPluginIfPresent(String uuid) {
		
		log.info("Checking if unloaded plugin [{}] is in left or right view", uuid);

		// check left panel stack
		{
			var matchedPlugin = leftViewStack.stream().filter(p -> p.uuid().equals(uuid)).findAny().orElse(null);
			
			if (matchedPlugin!=null) {
				
				matchedPlugin.unload();
				
				pluginRegistry.unloadSingletonPluginInstance(uuid);
				
				leftViewStack.remove(matchedPlugin);
				
				var newPlugin = leftViewStack.peek();
				
				if (newPlugin != null) {
					log.info("Switching left panel to previous plugin [{}] after unload", newPlugin.id());
					setLeftComponent(newPlugin);
					newPlugin.onFocusGained();
				} else {
					log.info("No more plugins in left panel stack after unload, showing placeholder");
				}
				
			}
		}
		
		// check right panel stack
		{
			var matchedPlugin = rightViewStack.stream().filter(p -> p.uuid().equals(uuid)).findAny().orElse(null);
			
			if (matchedPlugin!=null) {
				
				matchedPlugin.unload();
				
				pluginRegistry.unloadSingletonPluginInstance(uuid);
				
				rightViewStack.remove(matchedPlugin);
				
				var newPlugin = rightViewStack.peek();
				
				if (newPlugin != null) {
					log.info("Switching right panel to previous plugin [{}] after unload", newPlugin.id());
					setRightComponent(newPlugin);
					newPlugin.onFocusGained();
				} else {
					log.info("No more plugins in right panel stack after unload, showing placeholder");
				}
				
			}
		}
		
	}

	private Set<String> supportedMessages = Set.of("fs.path.selected", "fs.path.opened", "plugin.unload");

	@Override
	public boolean isMessageSupported(String type) {
		return supportedMessages.contains(type);
	}

	public JComponent getContainer() {
		return this.container;
	}

	public JSplitPane getMainSplitPane() {
		return this.mainSplitPane;
	}

	public void setPluginToActivePanel(NuclrPlugin pluginInstance) {

		var leftPlugin = getCurrentLeftPlugin();
		var rightPlugin = getCurrentRightPlugin();

		if (leftPlugin.isFocused()) {
			setLeftComponent(pluginInstance);
		} else if (rightPlugin.isFocused()) {
			setRightComponent(pluginInstance);
		}

	}

}
