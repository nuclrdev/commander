package dev.nuclr.commander.service;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.nuclr.plugin.PluginInfo;
import dev.nuclr.plugin.QuickViewPlugin;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class PluginRegistry {

	private Map<String, QuickViewPlugin> quickViewPlugins = new HashMap<>();

	public void registerQuickViewPlugin(PluginInfo info, QuickViewPlugin plugin) {
		
		log.info("Registering QuickViewPlugin: [{}]", plugin.getClass().getName());
		
		// detect if plugin with same id already exists
		
		this.quickViewPlugins.put(info.getId(), plugin);
	}
	
	public QuickViewPlugin getQuickViewPluginByFile(File file) {
		
		// detect if plugin supports 
		
		return this.getQuickViewPlugins()
				.stream()
				.filter(plugin -> plugin.isSupported(file))
				.findFirst()
				.orElse(null);
		
	}

	public Collection<QuickViewPlugin> getQuickViewPlugins() {
		return quickViewPlugins.values();
	}

	public void removeQuickViewPlugin(QuickViewPlugin plugin) {
		log.info("Removing QuickViewPlugin: [{}]", plugin.getClass().getName());
		this.quickViewPlugins.remove(plugin);
	}

}
