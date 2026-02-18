package dev.nuclr.commander.service;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

import org.springframework.stereotype.Service;

import dev.nuclr.plugin.QuickViewItem;
import dev.nuclr.plugin.QuickViewProvider;
import dev.nuclr.plugin.ViewProvider;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class PluginRegistry {

	public void registerViewProvider(ViewProvider provider) {
		log.info("Registering ViewProvider: [{}]", provider.getClass().getName());
	}

	public QuickViewProvider getQuickViewProviderByItem(QuickViewItem item) {
		return this.getQuickViewProviders()
				.stream()
				.filter(p -> p.matches(item))
				.findFirst()
				.orElse(null);
	}

	public Collection<QuickViewProvider> getQuickViewProviders() {
		return Collections.emptyList();
	}

	public void removeViewProvider(ViewProvider provider) {
		log.info("Removing ViewProvider: [{}]", provider.getClass().getName());
	}

	public void loadPlugin(File file) {
		log.info("Loading plugin: [{}]", file.getName());
	}

}
