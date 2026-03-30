package dev.nuclr.plugin;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;

public interface PanelProviderPlugin extends BasePlugin {

	JComponent getPanel();

	List<PluginPathResource> getChangeDriveResources();

	boolean openItem(PluginPathResource resource, AtomicBoolean cancelled) throws Exception;

	default boolean canSupport(PluginPathResource resource) {
		return false;
	}

	default List<MenuResource> getMenuItems(PluginPathResource currentResource) {
		return List.of();
	}
}
