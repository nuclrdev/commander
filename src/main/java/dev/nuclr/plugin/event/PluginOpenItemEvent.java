package dev.nuclr.plugin.event;

import dev.nuclr.plugin.PanelProviderPlugin;
import dev.nuclr.plugin.PluginPathResource;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PluginOpenItemEvent extends PluginEvent {

	private final PanelProviderPlugin sourceProvider;
	private final PluginPathResource resource;
}
