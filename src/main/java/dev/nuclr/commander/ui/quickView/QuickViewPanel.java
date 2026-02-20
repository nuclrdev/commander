package dev.nuclr.commander.ui.quickView;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

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

	private volatile Thread currentLoadThread;

	private static final String CARD_LOADING     = "Loading";
	private static final String CARD_NO_PROVIDER = "NoQuickViewAvailablePanel";

	@PostConstruct
	public void init() {

		log.info("QuickViewPanel initialized");

		this.panel = new JPanel(new CardLayout());
		this.panel.add(noQuickViewAvailablePanel, CARD_NO_PROVIDER);
		this.panel.add(buildLoadingPanel(), CARD_LOADING);

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
			cards.show(panel, CARD_NO_PROVIDER);
			return;
		}

		// Initialise plugin panels on the EDT before going async
		for (var plugin : plugins) {
			log.info("Found quick view provider [{}] for file: {}", plugin.getClass().getName(), file.getAbsolutePath());
			if (!loadedPlugins.containsKey(plugin.getPluginClass())) {
				log.info("Loading plugin [{}] for quick view", plugin.getPluginClass());
				plugin.getPanel();
				loadedPlugins.put(plugin.getPluginClass(), plugin);
				panel.add(plugin.getPanel(), plugin.getPluginClass());
				log.info("Plugin [{}] loaded and panel added to QuickViewPanel", plugin.getPluginClass());
			}
		}

		// Show loading feedback immediately before the (potentially slow) plugin open
		cards.show(panel, CARD_LOADING);
		panel.repaint();

		// Run the blocking open on a virtual thread so the EDT stays responsive
		currentLoadThread = Thread.ofVirtual().start(() -> {

			for (var plugin : plugins) {

				var st = System.currentTimeMillis();

				try {
					boolean success = plugin.open(item);

					var et = System.currentTimeMillis();
					log.info("Plugin [{}] open method took {} ms", plugin.getPluginClass(), (et - st));

					if (success) {
						String card = plugin.getPluginClass();
						SwingUtilities.invokeLater(() -> cards.show(panel, card));
						return;
					}
				} catch (Exception e) {
					log.error("Error opening quick view with plugin [{}]: {}", plugin.getPluginClass(), e.getMessage(), e);
				}

			}

			SwingUtilities.invokeLater(() -> {
				noQuickViewAvailablePanel.setFile(file);
				cards.show(panel, CARD_NO_PROVIDER);
			});

		});

	}

	public void stop() {
		Thread t = currentLoadThread;
		if (t != null) {
			t.interrupt();
			currentLoadThread = null;
		}
	}

	private static JPanel buildLoadingPanel() {
		JPanel p = new JPanel();
		p.setBackground(Color.BLACK);
		JLabel label = new JLabel("Loading\u2026", SwingConstants.CENTER);
		label.setForeground(new Color(140, 140, 140));
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
		p.add(label);
		return p;
	}

}
