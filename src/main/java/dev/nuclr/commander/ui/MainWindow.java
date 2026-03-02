package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Taskbar;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLightLaf;

import dev.nuclr.commander.Nuclr;
import dev.nuclr.commander.common.LocalSettingsStore;
import dev.nuclr.commander.common.SystemUtils;
import dev.nuclr.commander.event.FileSelectedEvent;
import dev.nuclr.commander.event.FunctionKeyCommandEvent;
import dev.nuclr.commander.event.QuickViewEvent;
import dev.nuclr.commander.event.ShowConsoleScreenEvent;
import dev.nuclr.commander.event.ShowEditorScreenEvent;
import dev.nuclr.commander.event.ShowFilePanelsViewEvent;
import dev.nuclr.commander.panel.FilePanelProviderRegistry;
import dev.nuclr.commander.ui.editor.EditorScreen;
import dev.nuclr.commander.ui.functionBar.FunctionKeyBar;
import dev.nuclr.commander.ui.pluginManagement.PluginManagementPopup;
import dev.nuclr.commander.ui.quickView.QuickViewPanel;
import dev.nuclr.commander.vfs.ArchiveMountProviderRegistry;
import dev.nuclr.commander.vfs.MountRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MainWindow {

	private JFrame mainFrame;

	private JMenuBar menuBar;

	private JSplitPane mainSplitPane;

	private Component lastFocusedInSplitPane;

	private EditorScreen editorScreen;

	private boolean quickViewActive;
	private Component quickViewReplacedComponent;

	/** Current divider position as a fraction (0.0–1.0) of the split-pane width. */
	private double dividerRatio = 0.5;

	/** Current UI font size in points. */
	private int fontSize = LocalSettingsStore.DEFAULT_FONT_SIZE;

	@Autowired
	private ConsolePanel consolePanel;

	@Autowired
	private QuickViewPanel quickViewPanel;

	@Value("${version}")
	private String version;

	@Autowired
	private ApplicationEventPublisher applicationEventPublisher;

	@Autowired
	private LocalSettingsStore settingsStore;

	@Autowired
	private MountRegistry mountRegistry;

	@Autowired
	private ArchiveMountProviderRegistry archiveMountProviderRegistry;

	@Autowired
	private FilePanelProviderRegistry filePanelProviderRegistry;

	@Autowired
	private PluginManagementPopup pluginManagementPopup;

	@Autowired
	private FunctionKeyBar functionKeyBar;

	@PostConstruct
	public void init() {

		log.info("Initializing MainWindow");

		if (SystemUtils.isOsMac()) {
			System.setProperty("apple.laf.useScreenMenuBar", "true");
			System.setProperty("apple.awt.application.name", "Nuclr Commander");
		}

		var savedSettings = settingsStore.loadOrDefault();
		fontSize = savedSettings.fontSize();
		UIManager.put("defaultFont", new Font("JetBrains Mono", Font.PLAIN, fontSize));

		 FlatDarculaLaf.setup();
		// FlatLightLaf.setup();
//		FlatIntelliJLaf.setup();

		mainFrame = new JFrame("Nuclr Commander (" + version + ")");
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		mainFrame.setSize(savedSettings.windowWidth(), savedSettings.windowHeight());
		if (savedSettings.windowX() >= 0 && savedSettings.windowY() >= 0) {
			mainFrame.setLocation(savedSettings.windowX(), savedSettings.windowY());
		} else {
			mainFrame.setLocationRelativeTo(null);
		}
		if (savedSettings.maximized()) {
			mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		}

		var appIcon = new ImageIcon("data/images/icon-512.png").getImage();
		mainFrame.setIconImage(appIcon);

		if (SystemUtils.isOsMac()) {
			if (Taskbar.isTaskbarSupported()) {
				Taskbar.getTaskbar().setIconImage(appIcon);
			}
		}

		mainFrame.setLayout(new BorderLayout());

		var colors = savedSettings.colors();

		mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		mainSplitPane.setLeftComponent(
				new FilePanel(applicationEventPublisher, mountRegistry, archiveMountProviderRegistry, filePanelProviderRegistry, colors));
		mainSplitPane.setRightComponent(
				new FilePanel(applicationEventPublisher, mountRegistry, archiveMountProviderRegistry, filePanelProviderRegistry, colors));

		dividerRatio = savedSettings.dividerRatio();

		mainFrame.add(mainSplitPane, BorderLayout.CENTER);
		mainFrame.add(functionKeyBar.getPanel(), BorderLayout.SOUTH);

		mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
			int loc = (int) evt.getNewValue();
			int paneWidth = mainSplitPane.getWidth();
			if (paneWidth > 0) {
				dividerRatio = Math.max(0.01, Math.min(0.99, loc / (double) paneWidth));
				saveDividerRatio(dividerRatio);
			}
		});

		// ── Menu bar ─────────────────────────────────────────────────────────
		menuBar = new JMenuBar();
		mainFrame.setJMenuBar(menuBar);

		{
			JMenu menu = new JMenu("Left");
			menuBar.add(menu);
			menu.add("Brief");
			menu.add("Medium");
			menu.add("Full");
			menu.add("Wide");
			menu.add("Detailed");
			menu.addSeparator();
			menu.add("Info panel");
			menu.add("Quick view");
			menu.addSeparator();
			menu.add("Sort modes");
			menu.add("Show long names");
			menu.add("Panel on/off");
			menu.add("Re-read");
			menu.add("Change drive");
		}

		// ── Global keyboard shortcuts ─────────────────────────────────────────
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
			@Override
			public boolean dispatchKeyEvent(KeyEvent e) {

				// Never intercept events when a dialog (e.g. a confirmation popup) is active.
				// Returning false lets the dialog's own focus manager handle Tab, Enter, Escape, etc.
				if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != mainFrame) {
					return false;
				}

				// Ctrl+O — toggle console
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_O
						&& e.isControlDown()) {
					if (mainSplitPane.isVisible()) {
						applicationEventPublisher.publishEvent(new ShowConsoleScreenEvent(this));
					} else {
						applicationEventPublisher.publishEvent(new ShowFilePanelsViewEvent(this));
					}
					return true;
				}

				// Ctrl+Q — toggle quick view on the opposite panel
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_Q
						&& e.isControlDown()
						&& mainSplitPane.isVisible()) {
					if (quickViewActive) {
						applicationEventPublisher.publishEvent(new QuickViewEvent(this, null));
					} else {
						var focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
						var leftComponent = mainSplitPane.getLeftComponent();
						var rightComponent = mainSplitPane.getRightComponent();
						FilePanel activePanel;
						if (rightComponent instanceof FilePanel fp
								&& focusOwner != null
								&& SwingUtilities.isDescendingFrom(focusOwner, fp)) {
							activePanel = fp;
						} else if (leftComponent instanceof FilePanel fp) {
							activePanel = fp;
						} else {
							return true;
						}
						var selectedPath = activePanel.getSelectedPath();
						if (selectedPath != null) {
							applicationEventPublisher.publishEvent(new QuickViewEvent(this, selectedPath));
						}
					}
					return true;
				}

				// Alt+F1 — change drive on left panel
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_F1
						&& e.isAltDown()
						&& mainSplitPane.isVisible()) {
					if (mainSplitPane.getLeftComponent() instanceof FilePanel fp) {
						ChangeDrivePopup.show(fp, filePanelProviderRegistry);
					}
					return true;
				}

				// Alt+F2 — change drive on right panel
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_F2
						&& e.isAltDown()
						&& mainSplitPane.isVisible()) {
					if (mainSplitPane.getRightComponent() instanceof FilePanel fp) {
						ChangeDrivePopup.show(fp, filePanelProviderRegistry);
					}
					return true;
				}

				// Alt+F4 — exit
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_F4
						&& e.isAltDown()) {
					Nuclr.exit();
					return true;
				}

				// Alt+Enter — fullscreen
					if (e.getID() == KeyEvent.KEY_PRESSED
							&& !e.isAltDown()
							&& !e.isControlDown()
							&& e.getKeyCode() >= KeyEvent.VK_F1
							&& e.getKeyCode() <= KeyEvent.VK_F12) {
						int functionKeyNumber = e.getKeyCode() - KeyEvent.VK_F1 + 1;
						functionKeyBar.publish(functionKeyNumber);
						return false;
					}

					if (e.getID() == KeyEvent.KEY_PRESSED
							&& e.getKeyCode() == KeyEvent.VK_ENTER
							&& e.isAltDown()) {
						toggleFullscreen();
						return true;
					}

				// Escape — close editor / console
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_ESCAPE
						&& editorScreen != null) {
					applicationEventPublisher.publishEvent(new ShowFilePanelsViewEvent(this));
					return true;
				}

				// Ctrl+Left / Ctrl+Right — resize split pane
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.isControlDown()
						&& mainSplitPane.isVisible()
						&& (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT)) {
					int step = 10;
					int loc = mainSplitPane.getDividerLocation();
					if (e.getKeyCode() == KeyEvent.VK_LEFT) {
						mainSplitPane.setDividerLocation(Math.max(loc - step, mainSplitPane.getMinimumDividerLocation()));
					} else {
						mainSplitPane.setDividerLocation(Math.min(loc + step, mainSplitPane.getMaximumDividerLocation()));
					}
					return true;
				}

				// Ctrl+= or Ctrl+numpad+ — increase font size
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.isControlDown()
						&& !e.isAltDown()
						&& (e.getKeyCode() == KeyEvent.VK_EQUALS
								|| e.getKeyCode() == KeyEvent.VK_PLUS
								|| e.getKeyCode() == KeyEvent.VK_ADD)) {
					applyFontSize(Math.min(fontSize + 1, 32));
					return true;
				}

				// Ctrl+- or Ctrl+numpad- — decrease font size
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.isControlDown()
						&& !e.isAltDown()
						&& (e.getKeyCode() == KeyEvent.VK_MINUS
								|| e.getKeyCode() == KeyEvent.VK_SUBTRACT)) {
					applyFontSize(Math.max(fontSize - 1, 8));
					return true;
				}

				// Ctrl+0 — reset font size to default
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.isControlDown()
						&& !e.isAltDown()
						&& e.getKeyCode() == KeyEvent.VK_0) {
					applyFontSize(LocalSettingsStore.DEFAULT_FONT_SIZE);
					return true;
				}

				// Tab — switch focus between left and right panels
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_TAB
						&& !e.isControlDown()
						&& mainSplitPane.isVisible()) {
					Component left  = mainSplitPane.getLeftComponent();
					Component right = mainSplitPane.getRightComponent();
					if (!(left instanceof FilePanel) || !(right instanceof FilePanel)) {
						return false; // one side is quickview — let default Tab handling proceed
					}
					var leftPanel  = (FilePanel) left;
					var rightPanel = (FilePanel) right;
					var focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
					if (focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, rightPanel)) {
						leftPanel.focusFileTable();
					} else {
						rightPanel.focusFileTable();
					}
					return true;
				}

				return false;
			}
		});

		// ── Window state listeners ────────────────────────────────────────────
		mainFrame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if ((mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0) saveWindowState();
			}
			@Override
			public void componentMoved(ComponentEvent e) {
				if ((mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0) saveWindowState();
			}
		});
		mainFrame.addWindowStateListener(e -> saveWindowState());

		mainFrame.setVisible(true);

		SwingUtilities.invokeLater(() -> {
			mainSplitPane.setDividerLocation(dividerRatio);
			if (mainSplitPane.getLeftComponent() instanceof FilePanel fp) {
				fp.focusFileTable();
			}
		});
	}

	// ── Event listeners ───────────────────────────────────────────────────────

	@EventListener
	public void onShowConsoleScreen(ShowConsoleScreenEvent event) {
		lastFocusedInSplitPane = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		mainFrame.remove(mainSplitPane);
		mainFrame.add(consolePanel.getConsolePanel(), BorderLayout.CENTER);
		mainSplitPane.setVisible(false);
		consolePanel.getConsolePanel().setVisible(true);
		consolePanel.getTermWidget().requestFocusInWindow();
		mainFrame.revalidate();
		mainFrame.repaint();
	}

	@EventListener
	public void onShowEditorScreen(ShowEditorScreenEvent event) {
		lastFocusedInSplitPane = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		editorScreen = new EditorScreen(event.getPath());
		mainFrame.remove(mainSplitPane);
		mainSplitPane.setVisible(false);
		mainFrame.add(editorScreen.getPanel(), BorderLayout.CENTER);
		editorScreen.getPanel().setVisible(true);
		editorScreen.focus();
		mainFrame.revalidate();
		mainFrame.repaint();
	}

	@EventListener
	public void onFileSelectedEvent(FileSelectedEvent event) {
		if (quickViewActive) {
			quickViewPanel.show(event.getPath());
		}
	}

	@EventListener
	public void onQuickView(QuickViewEvent event) {
		if (quickViewActive) {
			quickViewPanel.stop();
			if (quickViewReplacedComponent != null) {
				var focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
				boolean focusInLeft = focusOwner != null
						&& SwingUtilities.isDescendingFrom(focusOwner, mainSplitPane.getLeftComponent());
				if (focusInLeft) {
					mainSplitPane.setRightComponent(quickViewReplacedComponent);
				} else {
					mainSplitPane.setLeftComponent(quickViewReplacedComponent);
				}
			}
			quickViewReplacedComponent = null;
			quickViewActive = false;
		} else {
			var focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
			boolean focusInRight = focusOwner != null
					&& SwingUtilities.isDescendingFrom(focusOwner, mainSplitPane.getRightComponent());
			if (focusInRight) {
				quickViewReplacedComponent = mainSplitPane.getLeftComponent();
				mainSplitPane.setLeftComponent(quickViewPanel.getPanel());
			} else {
				quickViewReplacedComponent = mainSplitPane.getRightComponent();
				mainSplitPane.setRightComponent(quickViewPanel.getPanel());
			}
			quickViewPanel.show(event.getPath());
			quickViewActive = true;
		}
		mainSplitPane.setDividerLocation(0.5);
		mainFrame.revalidate();
		mainFrame.repaint();
	}

	@EventListener
	public void onShowFilePanelsView(ShowFilePanelsViewEvent event) {
		if (editorScreen != null) {
			mainFrame.remove(editorScreen.getPanel());
			editorScreen.dispose();
			editorScreen = null;
		}
		mainFrame.remove(consolePanel.getConsolePanel());
		mainFrame.add(mainSplitPane, BorderLayout.CENTER);
		consolePanel.getConsolePanel().setVisible(false);
		mainSplitPane.setVisible(true);
		if (lastFocusedInSplitPane != null) {
			lastFocusedInSplitPane.requestFocusInWindow();
		}
		mainFrame.revalidate();
		mainFrame.repaint();
	}

	@EventListener
	public void onFunctionKeyCommand(FunctionKeyCommandEvent event) {
		if (event.getFunctionKeyNumber() == 11) {
			pluginManagementPopup.show(mainFrame);
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private void applyFontSize(int size) {
		fontSize = size;
		UIManager.put("defaultFont", new Font("JetBrains Mono", Font.PLAIN, fontSize));
		SwingUtilities.updateComponentTreeUI(mainFrame);
		saveFontSize(fontSize);
	}

	private void saveWindowState() {
		var settings = settingsStore.loadOrDefault();
		boolean isMaximized = (mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
		int width  = isMaximized ? settings.windowWidth()  : mainFrame.getWidth();
		int height = isMaximized ? settings.windowHeight() : mainFrame.getHeight();
		int x      = isMaximized ? settings.windowX()      : mainFrame.getX();
		int y      = isMaximized ? settings.windowY()      : mainFrame.getY();
		settingsStore.save(new LocalSettingsStore.AppSettings(
				settings.theme(), width, height, x, y, isMaximized,
				settings.lastOpenedPath(), settings.autosaveInterval(),
				dividerRatio, settings.colors(), fontSize));
	}

	private void saveDividerRatio(double ratio) {
		var settings = settingsStore.loadOrDefault();
		boolean isMaximized = (mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
		settingsStore.save(new LocalSettingsStore.AppSettings(
				settings.theme(), settings.windowWidth(), settings.windowHeight(),
				settings.windowX(), settings.windowY(), isMaximized,
				settings.lastOpenedPath(), settings.autosaveInterval(),
				ratio, settings.colors(), fontSize));
	}

	private void saveFontSize(int size) {
		var settings = settingsStore.loadOrDefault();
		boolean isMaximized = (mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
		settingsStore.save(new LocalSettingsStore.AppSettings(
				settings.theme(), settings.windowWidth(), settings.windowHeight(),
				settings.windowX(), settings.windowY(), isMaximized,
				settings.lastOpenedPath(), settings.autosaveInterval(),
				dividerRatio, settings.colors(), size));
	}

	private void toggleFullscreen() {
		if ((mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) {
			mainFrame.setExtendedState(JFrame.NORMAL);
		} else {
			mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		}
		// Reapply the relative divider position once layout settles after the state change.
		SwingUtilities.invokeLater(() -> mainSplitPane.setDividerLocation(dividerRatio));
	}
}
