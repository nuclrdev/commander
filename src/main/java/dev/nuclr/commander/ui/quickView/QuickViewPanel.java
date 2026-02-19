package dev.nuclr.commander.ui.quickView;

import java.awt.CardLayout;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JPanel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.service.PluginRegistry;
import dev.nuclr.plugin.QuickViewProvider;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Lazy
@Data
public class QuickViewPanel {

	private JPanel panel;

	@Autowired
	private NoQuickViewAvailablePanel noQuickViewAvailablePanel;
	
	@Autowired
	private PluginRegistry pluginRegistry;
	
	private Map<String, QuickViewProvider> loadedPlugins = new HashMap<>();

	@PostConstruct
	public void init() {

		log.info("QuickViewPanel initialized");

		this.panel = new JPanel(new CardLayout());

		this.panel.add(noQuickViewAvailablePanel, "NoQuickViewAvailablePanel");

	}
	

	public void show(File file) {

		stop();

		var cards = (CardLayout) panel.getLayout();

		// TODO: construct QuickViewItem from File and call pluginRegistry.getQuickViewProviderByItem(item)
		
		FileQuickViewItem item = new FileQuickViewItem(file);
		
		QuickViewProvider plugin = pluginRegistry.getQuickViewProviderByItem(item);
		
		if (plugin!=null) {
			
			log.info("Found quick view provider [{}] for file: {}", plugin.getClass().getName(), file.getAbsolutePath());
			
			if (false == loadedPlugins.containsKey(plugin.getPluginClass())) {
				log.info("Loading plugin [{}] for quick view", plugin.getPluginClass());
				plugin.getPanel(); // ensure panel is created
				loadedPlugins.put(plugin.getPluginClass(), plugin);
				panel.add(plugin.getPanel(), plugin.getPluginClass());
				log.info("Plugin [{}] loaded and panel added to QuickViewPanel", plugin.getPluginClass());				
			}
			
			cards.show(panel, plugin.getPluginClass());
			
			plugin.open(item);
			
			
		} else {
			log.warn("No quick view available for file: {}", file.getAbsolutePath());
			noQuickViewAvailablePanel.setFile(file);
			cards.show(panel, "NoQuickViewAvailablePanel");
		}

	}

	public void stop() {


	}

}
