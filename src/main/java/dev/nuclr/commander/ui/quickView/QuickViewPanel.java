package dev.nuclr.commander.ui.quickView;

import java.awt.CardLayout;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JPanel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.common.Timed;
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

		var plugins = pluginRegistry.getQuickViewProvidersByItem(item);

		if (plugins == null || plugins.isEmpty()) {
			log.warn("No quick view providers found for file: {}", file.getAbsolutePath());
			noQuickViewAvailablePanel.setFile(file);
			cards.show(panel, "NoQuickViewAvailablePanel");
			return;
		}

		for (var plugin : plugins) {

			log
					.info(
							"Found quick view provider [{}] for file: {}",
							plugin.getClass().getName(),
							file.getAbsolutePath());

			if (false == loadedPlugins.containsKey(plugin.getPluginClass())) {
				log.info("Loading plugin [{}] for quick view", plugin.getPluginClass());
				plugin.getPanel(); // ensure panel is created
				loadedPlugins.put(plugin.getPluginClass(), plugin);
				panel.add(plugin.getPanel(), plugin.getPluginClass());
				log
						.info(
								"Plugin [{}] loaded and panel added to QuickViewPanel",
								plugin.getPluginClass());
			}

			var success = false;

			var st = System.currentTimeMillis();
			success = plugin.open(item);
			var et = System.currentTimeMillis();
			log.info("Plugin [{}] open method took {} ms", plugin.getPluginClass(), (et - st));

			if (success) {
				cards.show(panel, plugin.getPluginClass());
				return;
			}

		}

		cards.show(panel, "NoQuickViewAvailablePanel");

	}

	public void stop() {

	}

}
