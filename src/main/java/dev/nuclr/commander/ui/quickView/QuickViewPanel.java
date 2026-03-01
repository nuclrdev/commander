package dev.nuclr.commander.ui.quickView;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.service.PluginRegistry;
import dev.nuclr.plugin.QuickViewProvider;
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

	private Map<String, QuickViewProvider> loadedPlugins = new HashMap<>();

	private volatile Thread currentLoadThread;

	/** Cancellation token handed to the in-flight plugin's open() call. */
	private volatile AtomicBoolean currentCancelled;

	/** The provider whose content is currently displayed (null if none). */
	private volatile QuickViewProvider activeProvider;

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

		if (path == null) return;

		var cards = (CardLayout) panel.getLayout();

		if (Files.isDirectory(path)) {
			folderQuickViewPanel.show(path);
			cards.show(panel, CARD_FOLDER);
			return;
		}

		var item = new PathQuickViewItem(path);
		var plugins = pluginRegistry.getQuickViewProvidersByItem(item);

		if (plugins == null || plugins.isEmpty()) {
			cards.show(panel, CARD_NO_PROVIDER);
			return;
		}

		// Initialise plugin panels on the EDT before going async
		for (var plugin : plugins) {
			log.info("Found provider [{}] for: {}", plugin.getClass().getName(), path);
			if (!loadedPlugins.containsKey(plugin.getPluginClass())) {
				plugin.getPanel();
				loadedPlugins.put(plugin.getPluginClass(), plugin);
				panel.add(plugin.getPanel(), plugin.getPluginClass());
			}
		}

		// Show loading feedback immediately while the plugin opens the file
		cards.show(panel, CARD_LOADING);
		panel.repaint();

		AtomicBoolean cancelled = new AtomicBoolean(false);
		currentCancelled = cancelled;

		currentLoadThread = Thread.ofVirtual().start(() -> {
			for (var plugin : plugins) {
				// Bail out before every expensive operation
				if (isStale(myGen)) return;

				long start = System.currentTimeMillis();
				boolean success;
				try {
					success = plugin.open(item, cancelled);
					log.info("Plugin [{}] open took {} ms", plugin.getPluginClass(),
							System.currentTimeMillis() - start);
				} catch (Exception e) {
					log.error("Error in plugin [{}]: {}", plugin.getPluginClass(), e.getMessage(), e);
					continue;
				}

				// If superseded while plugin was opening, close what we just opened
				if (isStale(myGen)) {
					closeQuietly(plugin);
					return;
				}

				if (success) {
					activeProvider = plugin;
					String card = plugin.getPluginClass();
					SwingUtilities.invokeLater(() -> {
						if (!isStale(myGen)) cards.show(panel, card);
					});
					return;
				}
			}

			// All plugins failed — nothing to show
			SwingUtilities.invokeLater(() -> {
				if (!isStale(myGen)) cards.show(panel, CARD_NO_PROVIDER);
			});
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
		QuickViewProvider prev = activeProvider;
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

	private void closeQuietly(QuickViewProvider provider) {
		try {
			provider.close();
		} catch (Exception e) {
			log.warn("Error closing provider [{}]: {}", provider.getPluginClass(), e.getMessage());
		}
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
}
