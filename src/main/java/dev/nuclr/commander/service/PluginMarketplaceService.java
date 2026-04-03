package dev.nuclr.commander.service;

import org.springframework.stereotype.Service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@Service
public class PluginMarketplaceService {

	public void searchQuickViewPlugins(String query) {
		log.info("Searching for quick view plugins with query: {}", query);
	}

}
