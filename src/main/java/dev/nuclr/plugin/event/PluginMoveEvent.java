package dev.nuclr.plugin.event;

import java.util.List;

import dev.nuclr.plugin.PanelProviderPlugin;
import dev.nuclr.plugin.PluginPathResource;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PluginMoveEvent extends PluginEvent {

	private final PanelProviderPlugin sourceProvider;
	private final List<PluginPathResource> sources;
}
