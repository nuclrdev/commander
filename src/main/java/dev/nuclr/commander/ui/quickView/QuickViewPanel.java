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

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.common.ThemeSchemeStore;
import dev.nuclr.commander.plugin.PluginDescriptor;
import dev.nuclr.commander.plugin.PluginRegistry;
import dev.nuclr.plugin.ResourceContentPlugin;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Lazy
@Data
public class QuickViewPanel {

	private JPanel panel;

	@Autowired
	private NoQuickViewAvailablePanel noQuickViewAvailablePanel;

	@Autowired
	private FolderQuickViewPanel folderQuickViewPanel;

	@Autowired
	private PluginRegistry pluginRegistry;

	@Autowired
	private ThemeSchemeStore themeSchemeStore;

	private volatile Thread currentLoadThread;

	/** Cancellation token handed to the in-flight plugin's open() call. */
	private volatile AtomicBoolean currentCancelled;

	/** The provider whose content is currently displayed (null if none). */
	private volatile ResourceContentPlugin activeProvider;

	/**
	 * Monotonically increasing counter. Each call to show() increments it.
	 * Loading threads capture their generation at start and abandon work if the
	 * counter has moved on — meaning a newer show() superseded them.
	 */
	private final AtomicLong currentGeneration = new AtomicLong(0);

	private static final String CARD_LOADING     = "Loading";
	private static final String CARD_NO_PROVIDER = "NoQuickViewAvailablePanel";
	private static final String CARD_FOLDER      = "FolderQuickViewPanel";

	@PostConstruct
	public void init() {
		log.info("QuickViewPanel initialized");
		this.panel = new JPanel(new CardLayout());
		this.panel.add(noQuickViewAvailablePanel, CARD_NO_PROVIDER);
		this.panel.add(folderQuickViewPanel, CARD_FOLDER);
		this.panel.add(buildLoadingPanel(), CARD_LOADING);
	}

	public void show(Path path) {
		// Claim the slot before stopping the old thread so that any in-flight
		// thread sees its generation is stale as soon as we increment.
		long myGen = currentGeneration.incrementAndGet();
		stop();

		var cards = (CardLayout) panel.getLayout();
		if (path == null) {
			showNoProvider(null, cards);
			return;
		}

		if (Files.isDirectory(path)) {
			folderQuickViewPanel.show(path);
			showCard(cards, CARD_FOLDER);
			return;
		}

		var item = new PathQuickViewItem(path);
		var plugins = pluginRegistry.getPluginByItem(item);

		if (plugins == null || plugins.isEmpty()) {
			showNoProvider(path, cards);
			return;
		}

		// Initialise plugin panels on the EDT before going async
		for (var plugin : plugins) {
			log.info("Found provider [{}] for: {}", plugin.getClass().getName(), path);
			String pluginKey = plugin.getClass().getName();
			/*
			if (!loadedPlugins.containsKey(pluginKey)) {
				var pluginPanel = plugin.panel();
				loadedPlugins.put(pluginKey, plugin);
				panel.add(pluginPanel, pluginKey);
			}
			*/
		}

		// Show loading feedback immediately while the plugin opens the file
		showCard(cards, CARD_LOADING);

		AtomicBoolean cancelled = new AtomicBoolean(false);
		currentCancelled = cancelled;

		currentLoadThread = Thread.ofVirtual().start(() -> {
			for (var plugin : plugins) {
				// Bail out before every expensive operation
				if (isStale(myGen)) return;

				long start = System.currentTimeMillis();
				boolean success;
				try {
					success = plugin.openResource(item, cancelled);
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
					activeProvider = plugin;
					String card = plugin.getClass().getName();
					if (!isStale(myGen)) showCard(cards, card);
					return;
				}
			}

			// All plugins failed — nothing to show
			if (!isStale(myGen)) showNoProvider(path, cards);
		});
	}

	public void stop() {
		folderQuickViewPanel.stopScan();
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
		ResourceContentPlugin prev = activeProvider;
		if (prev != null) {
			activeProvider = null;
			closeQuietly(prev);
		}
	}

	// -------------------------------------------------------------------------

	/** Returns true if myGen is no longer the current generation. */
	private boolean isStale(long myGen) {
		return currentGeneration.get() != myGen || Thread.currentThread().isInterrupted();
	}

	private void closeQuietly(ResourceContentPlugin provider) {
		try {
			provider.closeResource();
		} catch (Exception e) {
			log.warn("Error closing provider [{}]: {}", provider.getClass().getName(), e.getMessage());
		}
	}

	private void showNoProvider(Path path, CardLayout cards) {
		if (SwingUtilities.isEventDispatchThread()) {
			noQuickViewAvailablePanel.setPath(path);
			showCard(cards, CARD_NO_PROVIDER);
			return;
		}

		SwingUtilities.invokeLater(() -> showNoProvider(path, cards));
	}

	private void showCard(CardLayout cards, String card) {
		if (SwingUtilities.isEventDispatchThread()) {
			cards.show(panel, card);
			panel.revalidate();
			panel.repaint();
			return;
		}

		SwingUtilities.invokeLater(() -> showCard(cards, card));
	}

	private static JPanel buildLoadingPanel() {
		JPanel p = new JPanel();
		p.setBackground(Color.BLACK);
		JLabel label = new JLabel("Loading\u2026", SwingConstants.CENTER);
		label.setForeground(new Color(140, 140, 140));
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
		p.add(label);
		return p;
	}

	private Object currentPluginTheme() {
		var scheme = themeSchemeStore.loadOrDefault().activeThemeScheme();
		Font defaultFont = UIManager.getFont("defaultFont");
		String fontFamily = defaultFont != null ? defaultFont.getFamily() : Font.MONOSPACED;
		int themeFontSize = defaultFont != null ? defaultFont.getSize() : 12;
		try {
			Class<?> themeClass = Class.forName("dev.nuclr.plugin.PluginTheme");
			Constructor<?> constructor = themeClass.getConstructor(
					String.class,
					Map.class,
					String.class,
					int.class);
			return constructor.newInstance(
					scheme.name(),
					scheme.uiDefaults(),
					fontFamily,
					themeFontSize);
		} catch (Exception e) {
			log.debug("PluginTheme is not available on the current classpath", e);
			return null;
		}
	}
}
