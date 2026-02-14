package dev.nuclr.commander.service;

import java.io.File;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import dev.nuclr.plugin.PluginInfo;
import dev.nuclr.plugin.QuickViewPlugin;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class PluginRegistry {

	private static final String quickViewPlugins = null;
	
	public void registerQuickViewPlugin(PluginInfo info, QuickViewPlugin plugin) {
		log.info("Registering QuickViewPlugin: [{}]", plugin.getClass().getName());
//		this.quickViewPlugins.put(info, plugin);
	}
	
	public QuickViewPlugin getQuickViewPluginByFile(File file) {
		
		var plugin = this.getQuickViewPlugins()
				.stream()
				.filter(p -> p.canQuickView(file))
				.findFirst()
				.orElse(null);

		return plugin;
		
	}

	public Collection<QuickViewPlugin> getQuickViewPlugins() {
		return null;//quickViewPlugins.values();
	}

	public void removeQuickViewPlugin(QuickViewPlugin plugin) {
		log.info("Removing QuickViewPlugin: [{}]", plugin.getClass().getName());
//		this.quickViewPlugins.remove(plugin);
	}

}
