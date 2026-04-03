package dev.nuclr.commander.service;

import com.google.inject.Singleton;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Singleton
public class PluginMarketplaceService {

	public void searchQuickViewPlugins(String query) {
		log.info("Searching for quick view plugins with query: {}", query);
	}

}
