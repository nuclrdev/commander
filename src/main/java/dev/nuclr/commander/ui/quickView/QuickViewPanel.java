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

	@PostConstruct
	public void init() {

		log.info("QuickViewPanel initialized");

		this.panel = new JPanel(new CardLayout());

		this.panel.add(noQuickViewAvailablePanel, "NoQuickViewAvailablePanel");

	}
	
	private Map<String, QuickViewProvider> loadedPlugins = new HashMap<>();

	public void show(File file) {

		stop();

		var cards = (CardLayout) panel.getLayout();

		// TODO: construct QuickViewItem from File and call pluginRegistry.getQuickViewProviderByItem(item)
		QuickViewProvider plugin = null;
		
		if (plugin!=null) {
			
			
			
		} else {
			log.warn("No quick view available for file: {}", file.getAbsolutePath());
			noQuickViewAvailablePanel.setFile(file);
			cards.show(panel, "NoQuickViewAvailablePanel");
		}

	}

	public void stop() {


	}

}
