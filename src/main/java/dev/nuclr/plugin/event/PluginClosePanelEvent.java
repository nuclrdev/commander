package dev.nuclr.plugin.event;

import dev.nuclr.plugin.PanelProviderPlugin;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PluginClosePanelEvent extends PluginEvent {

	private final PanelProviderPlugin sourceProvider;
}
