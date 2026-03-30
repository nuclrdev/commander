package dev.nuclr.plugin;

import javax.swing.JComponent;

public interface ScreenProviderPlugin extends BasePlugin {

	JComponent getPanel();

	boolean supports(PluginPathResource item);
}
