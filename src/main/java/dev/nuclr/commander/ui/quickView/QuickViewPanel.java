/*

	Copyright 2026 Sergio, Nuclr (https://nuclr.dev)
	
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	
	http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

*/
package dev.nuclr.commander.ui.quickView;

import java.awt.BorderLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.plugin.PluginRegistry;
import dev.nuclr.platform.plugin.NuclrPlugin;
import dev.nuclr.platform.plugin.NuclrPluginRole;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class QuickViewPanel {

	@Autowired
	private TaskExecutor taskExecutor;

	@Autowired
	private NoQuickViewAvailablePlugin noQuickViewAvailablePlugin;

	@Autowired
	private FolderQuickViewPlugin folderQuickViewPlugin;

	@Autowired
	private LoadingQuickViewPlugin loadingQuickViewPlugin;

	@Autowired
	private PluginRegistry pluginRegistry;

	private volatile Thread currentLoadThread;

	/** Cancellation token handed to the in-flight plugin's open() call. */
	private volatile AtomicBoolean currentCancelled;

	/** The provider whose content is currently displayed (null if none). */
	private volatile NuclrPlugin activeProvider;

	/**
	 * Monotonically increasing counter. Each call to show() increments it. Loading
	 * threads capture their generation at start and abandon work if the counter has
	 * moved on — meaning a newer show() superseded them.
	 */
	private final AtomicLong currentGeneration = new AtomicLong(0);

	private JPanel container;
	private Runnable onProviderChanged;

	public void setOnProviderChanged(Runnable callback) {
		this.onProviderChanged = callback;
	}

	@PostConstruct
	public void init() {
		log.info("QuickViewPanel initialized");
		container = new JPanel(new BorderLayout());
		setActiveProvider(loadingQuickViewPlugin);
	}

	public JPanel getPanel() {
		return container;
	}

	public void show(Path p) {

		if (p == null) {
			return;
		}

		var path = new PathQuickViewItem(p);

		// Claim the slot before stopping the old thread so that any in-flight
		// thread sees its generation is stale as soon as we increment.
		long myGen = currentGeneration.incrementAndGet();

		stop();

		if (Files.isDirectory(path.getPath())) {
			this.folderQuickViewPlugin.openResource(path, currentCancelled);
			showCard(folderQuickViewPlugin);
			return;
		}

		var plugins = pluginRegistry.getPluginByItem(path, NuclrPluginRole.QuickViewer);

		if (plugins == null || plugins.isEmpty()) {
			log.info("No providers found for: {}", path);
			this.noQuickViewAvailablePlugin.openResource(path, currentCancelled);
			showCard(noQuickViewAvailablePlugin);
			return;
		}

		// Initialise plugin panels on the EDT before going async
		for (var plugin : plugins) {
			log.info("Found provider [{}] for: {}", plugin.getClass().getName(), path);
			String pluginKey = plugin.getClass().getName();
			/*
			 * if (!loadedPlugins.containsKey(pluginKey)) { var pluginPanel =
			 * plugin.panel(); loadedPlugins.put(pluginKey, plugin); panel.add(pluginPanel,
			 * pluginKey); }
			 */
		}

		// Show loading feedback immediately while the plugin opens the file
		showCard(loadingQuickViewPlugin);

		AtomicBoolean cancelled = new AtomicBoolean(false);
		currentCancelled = cancelled;

		currentLoadThread = Thread.ofVirtual().start(() -> {
			for (var plugin : plugins) {
				// Bail out before every expensive operation
				if (isStale(myGen))
					return;

				long start = System.currentTimeMillis();
				boolean success;
				try {
					success = plugin.openResource(path, cancelled);
					log.info("Plugin [{}] open took {} ms", plugin.getClass().getName(),
							System.currentTimeMillis() - start);
				} catch (Exception e) {
					log.error("Error in plugin [{}]: {}", plugin.getClass().getName(), e.getMessage(), e);
					continue;
				}

				// If superseded while plugin was opening, close what we just opened
				if (isStale(myGen)) {
					closeQuietly(plugin);
					return;
				}

				if (success) {
					if (!isStale(myGen))
						setActiveProvider(plugin);
					return;
				}
			}

			// All plugins failed — nothing to show
			if (!isStale(myGen))
				showNoProvider(path);
		});
	}

	public void stop() {
		folderQuickViewPlugin.stopScan();
		// Signal the in-flight plugin to abort before interrupting the thread,
		// so the plugin can react even if it is not sensitive to thread interrupts.
		AtomicBoolean c = currentCancelled;
		if (c != null) {
			c.set(true);
			currentCancelled = null;
		}
		Thread t = currentLoadThread;
		if (t != null) {
			t.interrupt();
			currentLoadThread = null;
		}
		NuclrPlugin prev = activeProvider;
		if (prev != null) {
			setActiveProvider(null);
			closeQuietly(prev);
		}
	}

	// -------------------------------------------------------------------------

	/** Returns true if myGen is no longer the current generation. */
	private boolean isStale(long myGen) {
		return currentGeneration.get() != myGen || Thread.currentThread().isInterrupted();
	}

	private void closeQuietly(NuclrPlugin provider) {
		try {
			provider.closeResource();
		} catch (Exception e) {
			log.warn("Error closing provider [{}]: {}", provider.getClass().getName(), e.getMessage());
		}
	}

	private void showNoProvider(PathQuickViewItem path) {
		if (SwingUtilities.isEventDispatchThread()) {
			noQuickViewAvailablePlugin.openResource(path, currentCancelled);
			showCard(noQuickViewAvailablePlugin);
			return;
		}

		SwingUtilities.invokeLater(() -> showNoProvider(path));
	}

	private void showCard(NuclrPlugin plugin) {

		log.info("Attempting to show plugin, current one [{}]", this.activeProvider);

		if (activeProvider != null && Objects.equals(plugin.id(), this.activeProvider.id())) {
			log.info("Plugin [{}] is already active, skipping showCard", plugin.getClass().getName());
			setActiveProvider(plugin);
		} else {
			log.info("Showing new  plugin [{}]", plugin.getClass().getName());
			setActiveProvider(plugin);
		}

	}

	private void setActiveProvider(NuclrPlugin plugin) {
		this.activeProvider = plugin;
		log.info("Set active provider to [{}]", this.activeProvider);
		if (container == null || plugin == null) {
			return;
		}
		Runnable swap = () -> {
			container.removeAll();
			container.add(plugin.panel(), BorderLayout.CENTER);
			container.revalidate();
			container.repaint();
			if (onProviderChanged != null) {
				onProviderChanged.run();
			}
		};
		if (SwingUtilities.isEventDispatchThread()) {
			swap.run();
		} else {
			SwingUtilities.invokeLater(swap);
		}
	}

	public NuclrPlugin getActiveProvider() {
		return this.activeProvider;
	}

}
