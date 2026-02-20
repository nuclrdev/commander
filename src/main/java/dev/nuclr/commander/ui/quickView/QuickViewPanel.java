package dev.nuclr.commander.ui.quickView;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.nio.file.Path;
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

	public void show(Path path) {
		stop();

		if (path == null) return;

		var cards = (CardLayout) panel.getLayout();
		var item = new PathQuickViewItem(path);
		var plugins = pluginRegistry.getQuickViewProvidersByItem(item);

		if (plugins == null || plugins.isEmpty()) {
			log.warn("No quick view providers for: {}", path);
			noQuickViewAvailablePanel.setPath(path);
			cards.show(panel, CARD_NO_PROVIDER);
			return;
		}

		// Initialise plugin panels on the EDT before going async
		for (var plugin : plugins) {
			log.info("Found provider [{}] for: {}", plugin.getClass().getName(), path);
			if (!loadedPlugins.containsKey(plugin.getPluginClass())) {
				plugin.getPanel();
				loadedPlugins.put(plugin.getPluginClass(), plugin);
				panel.add(plugin.getPanel(), plugin.getPluginClass());
			}
		}

		// Show loading feedback immediately while the plugin opens the file
		cards.show(panel, CARD_LOADING);
		panel.repaint();

		currentLoadThread = Thread.ofVirtual().start(() -> {
			for (var plugin : plugins) {
				long start = System.currentTimeMillis();
				try {
					boolean success = plugin.open(item);
					log.info("Plugin [{}] open took {} ms", plugin.getPluginClass(),
							System.currentTimeMillis() - start);
					if (success) {
						String card = plugin.getPluginClass();
						SwingUtilities.invokeLater(() -> cards.show(panel, card));
						return;
					}
				} catch (Exception e) {
					log.error("Error in plugin [{}]: {}", plugin.getPluginClass(), e.getMessage(), e);
				}
			}
			SwingUtilities.invokeLater(() -> {
				noQuickViewAvailablePanel.setPath(path);
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
