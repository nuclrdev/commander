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
package dev.nuclr.commander.ui.main;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Taskbar;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.PaintEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.nuclr.commander.Nuclr;
import dev.nuclr.commander.common.LocalSettingsStore;
import dev.nuclr.commander.common.SystemUtils;
import dev.nuclr.commander.common.ThemeSchemeStore;
import dev.nuclr.commander.config.AppProperties;
import dev.nuclr.commander.event.AppEventBus;
import dev.nuclr.commander.event.FunctionKeyCommandEvent;
import dev.nuclr.commander.event.ShowConsoleScreenEvent;
import dev.nuclr.commander.event.ShowEditorScreenEvent;
import dev.nuclr.commander.event.ShowFilePanelsViewEvent;
import dev.nuclr.commander.plugin.PluginRegistry;
import dev.nuclr.commander.service.PanelTransferService;
import dev.nuclr.commander.ui.ChangeDrivePopup;
import dev.nuclr.commander.ui.ConsolePanel;
import dev.nuclr.commander.ui.common.Alerts;
import dev.nuclr.commander.ui.functionBar.FunctionKeyBar;
import dev.nuclr.commander.ui.pluginManagement.PluginManagementPopup;
import dev.nuclr.commander.ui.quickView.PathQuickViewItem;
import dev.nuclr.commander.ui.quickView.QuickViewPanel;
import dev.nuclr.platform.events.NuclrEventBus;
import dev.nuclr.platform.events.NuclrEventListener;
import dev.nuclr.plugin.MenuResource;
import dev.nuclr.plugin.PluginPathResource;
import dev.nuclr.plugin.ResourceContentPlugin;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class MainWindow implements NuclrEventListener {

	private static final int MAIN_DIVIDER_STEP_PIXELS = 30;

	private JFrame mainFrame;
	private JSplitPane mainSplitPane;
	private Component lastFocusedInSplitPane;
	private Component activeScreenComponent;
	private double dividerRatio = 0.5;
	private int fontSize = LocalSettingsStore.DEFAULT_FONT_SIZE;
	private boolean shiftDown;
	private boolean ctrlDown;
	private boolean altDown;
	private boolean quickViewActive;
	private boolean quickViewShowingLeft;
	private Path quickViewCurrentPath;
	private PanelState focusedPanelState;
	private PanelState quickViewSourceState;
	private Component quickViewReplacedComponent;

	private final PanelState leftPanelState = new PanelState();
	private final PanelState rightPanelState = new PanelState();

	private final ConsolePanel consolePanel;

	private final QuickViewPanel quickViewPanel;

	private final String version;

	private final AppEventBus appEventBus;

	private final LocalSettingsStore settingsStore;

	private final ThemeSchemeStore themeSchemeStore;

	private final FunctionKeyBar functionKeyBar;

	private final PluginRegistry pluginRegistry;
	
	private final NuclrEventBus eventBus;

	private final PluginManagementPopup pluginManagementPopup;

	private final PanelTransferService panelTransferService;

	private Timer quickViewRefreshTimer;
	private final AtomicBoolean startupLogged = new AtomicBoolean();

	@Inject
	public MainWindow(
			ConsolePanel consolePanel,
			QuickViewPanel quickViewPanel,
			AppProperties appProperties,
			AppEventBus appEventBus,
			LocalSettingsStore settingsStore,
			ThemeSchemeStore themeSchemeStore,
			FunctionKeyBar functionKeyBar,
			PluginRegistry pluginRegistry,
			NuclrEventBus eventBus,
			PluginManagementPopup pluginManagementPopup,
			PanelTransferService panelTransferService) {
		this.consolePanel = consolePanel;
		this.quickViewPanel = quickViewPanel;
		this.version = appProperties.getRequired("version");
		this.appEventBus = appEventBus;
		this.settingsStore = settingsStore;
		this.themeSchemeStore = themeSchemeStore;
		this.functionKeyBar = functionKeyBar;
		this.pluginRegistry = pluginRegistry;
		this.eventBus = eventBus;
		this.pluginManagementPopup = pluginManagementPopup;
		this.panelTransferService = panelTransferService;

		this.eventBus.subscribe(this);
		this.appEventBus.subscribe(ShowConsoleScreenEvent.class, this::onShowConsoleScreen);
		this.appEventBus.subscribe(ShowEditorScreenEvent.class, this::onShowEditorScreen);
		this.appEventBus.subscribe(ShowFilePanelsViewEvent.class, this::onShowFilePanelsView);
		this.appEventBus.subscribe(FunctionKeyCommandEvent.class, this::onFunctionKeyCommand);

		if (SwingUtilities.isEventDispatchThread()) {
			initOnEdt();
		} else {
			SwingUtilities.invokeLater(this::initOnEdt);
		}
	}

	private void initOnEdt() {
		
		  // Disable custom window decorations
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        
		if (SystemUtils.isOsMac()) {
			System.setProperty("apple.laf.useScreenMenuBar", "true");
			System.setProperty("apple.awt.application.name", "Nuclr Commander");
		}

		var savedSettings = settingsStore.loadOrDefault();
		fontSize = savedSettings.fontSize();
		dividerRatio = savedSettings.dividerRatio();

		FlatDarculaLaf.setup();
		applyThemeScheme();
		UIManager.put("defaultFont", new Font("JetBrains Mono", Font.PLAIN, fontSize));
		UIManager.put("Button.font", UIManager.getFont("defaultFont"));	

		mainFrame = new JFrame("Nuclr Commander (" + version + ")");
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		mainFrame.setLayout(new BorderLayout());
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
		if (SystemUtils.isOsMac() && Taskbar.isTaskbarSupported()) {
			Taskbar.getTaskbar().setIconImage(appIcon);
		}

		mainSplitPane = new JSplitPane(
				JSplitPane.HORIZONTAL_SPLIT,
				placeholder("Loading plugins..."),
				placeholder("Loading plugins..."));
		mainSplitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
			int loc = (int) evt.getNewValue();
			int paneWidth = mainSplitPane.getWidth();
			if (paneWidth > 0) {
				dividerRatio = Math.max(0.01, Math.min(0.99, loc / (double) paneWidth));
				saveDividerRatio(dividerRatio);
			}
		});

		mainFrame.setJMenuBar(buildMenuBar());
		mainFrame.add(mainSplitPane, BorderLayout.CENTER);
		mainFrame.add(functionKeyBar.getPanel(), BorderLayout.SOUTH);
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(buildKeyDispatcher());
		KeyboardFocusManager
				.getCurrentKeyboardFocusManager()
				.addPropertyChangeListener("focusOwner", evt -> onFocusOwnerChanged((Component) evt.getNewValue()));
		mainFrame.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if ((mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0) {
					saveWindowState();
				}
			}

			@Override
			public void componentMoved(ComponentEvent e) {
				if ((mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == 0) {
					saveWindowState();
				}
			}
		});
		
		mainFrame.addWindowStateListener(e -> saveWindowState());
		installFirstPaintStartupProbe();
		
		startQuickViewRefreshTimer();
		
		mainFrame.setVisible(true);
		restoreMainDividerLocation();
	}

	private void logStartupComplete(String message) {
		if (!startupLogged.compareAndSet(false, true)) {
			return;
		}
		long elapsed = System.currentTimeMillis() - Nuclr.startTime;
		log.info("{} in {} ms", message, elapsed);
	}

	private void installFirstPaintStartupProbe() {
		AWTEventListener listener = new AWTEventListener() {
			@Override
			public void eventDispatched(java.awt.AWTEvent event) {
				if (!(event instanceof PaintEvent paintEvent)) {
					return;
				}
				if (!isPaintForMainWindow(paintEvent.getSource())) {
					return;
				}
				Toolkit.getDefaultToolkit().removeAWTEventListener(this);
				SwingUtilities.invokeLater(() -> logStartupComplete("Main window first paint"));
			}
		};
		Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.PAINT_EVENT_MASK);
	}

	private boolean isPaintForMainWindow(Object source) {
		if (!(source instanceof Component component)) {
			return false;
		}
		if (component == mainFrame) {
			return true;
		}
		Container parent = component.getParent();
		while (parent != null) {
			if (parent == mainFrame) {
				return true;
			}
			parent = parent.getParent();
		}
		return false;
	}

	private JMenuBar buildMenuBar() {
		JMenuBar menuBar = new JMenuBar();

		JMenu leftMenu = new JMenu("Left");
		leftMenu.setMnemonic(KeyEvent.VK_L);
		leftMenu.add(item("Change drive", KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.ALT_DOWN_MASK), e -> showChangeDrive(true)));
		menuBar.add(leftMenu);

		JMenu commandsMenu = new JMenu("Commands");
		commandsMenu.setMnemonic(KeyEvent.VK_C);
		commandsMenu.add(item("Plugin commands", KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), e -> pluginManagementPopup.show(mainFrame)));
		menuBar.add(commandsMenu);

		JMenu optionsMenu = new JMenu("Options");
		optionsMenu.setMnemonic(KeyEvent.VK_O);
		optionsMenu.add(item("Toggle console", KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), e -> toggleConsole()));
		menuBar.add(optionsMenu);

		JMenu rightMenu = new JMenu("Right");
		rightMenu.setMnemonic(KeyEvent.VK_R);
		rightMenu.add(item("Change drive", KeyStroke.getKeyStroke(KeyEvent.VK_F2, InputEvent.ALT_DOWN_MASK), e -> showChangeDrive(false)));
		menuBar.add(rightMenu);

		return menuBar;
	}

	private JMenuItem item(String text, KeyStroke stroke, ActionListener action) {
		JMenuItem item = new JMenuItem(text);
		item.setAccelerator(stroke);
		item.addActionListener(action);
		return item;
	}

	private KeyEventDispatcher buildKeyDispatcher() {
		
		return e -> {
			
			if (KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != mainFrame) {
				return false;
			}

			updateModifierState(e);

			if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_O && e.isControlDown()) {
				toggleConsole();
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_Q && e.isControlDown() && mainSplitPane.isVisible()) {
				toggleQuickView();
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED
					&& e.isControlDown()
					&& !e.isAltDown()
					&& mainSplitPane.isVisible()
					&& (e.getKeyCode() == KeyEvent.VK_LEFT || e.getKeyCode() == KeyEvent.VK_RIGHT)) {
				moveMainDivider(e.getKeyCode() == KeyEvent.VK_LEFT ? -MAIN_DIVIDER_STEP_PIXELS : MAIN_DIVIDER_STEP_PIXELS);
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_F1 && e.isAltDown() && mainSplitPane.isVisible()) {
				showChangeDrive(true);
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_F2 && e.isAltDown() && mainSplitPane.isVisible()) {
				showChangeDrive(false);
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && !e.isAltDown() && !e.isControlDown() && e.getKeyCode() == KeyEvent.VK_F10) {
				confirmAndExitApplication();
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED
					&& !e.isAltDown()
					&& !e.isControlDown()
					&& e.getKeyCode() >= KeyEvent.VK_F1
					&& e.getKeyCode() <= KeyEvent.VK_F12) {
				functionKeyBar.publish(e.getKeyCode() - KeyEvent.VK_F1 + 1);
				return false;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_F4 && e.isAltDown()) {
				Nuclr.exit();
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ENTER && e.isAltDown()) {
				toggleFullscreen();
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED
					&& e.isControlDown()
					&& !e.isAltDown()
					&& (e.getKeyCode() == KeyEvent.VK_EQUALS || e.getKeyCode() == KeyEvent.VK_PLUS || e.getKeyCode() == KeyEvent.VK_ADD)) {
				applyFontSize(Math.min(fontSize + 1, 32));
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED
					&& e.isControlDown()
					&& !e.isAltDown()
					&& (e.getKeyCode() == KeyEvent.VK_MINUS || e.getKeyCode() == KeyEvent.VK_SUBTRACT)) {
				applyFontSize(Math.max(fontSize - 1, 8));
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.isControlDown() && !e.isAltDown() && e.getKeyCode() == KeyEvent.VK_0) {
				applyFontSize(LocalSettingsStore.DEFAULT_FONT_SIZE);
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_ESCAPE && activeScreenComponent != null) {
				appEventBus.publish(new ShowFilePanelsViewEvent(this));
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED
					&& e.getKeyCode() == KeyEvent.VK_TAB
					&& !e.isAltDown()
					&& !e.isControlDown()
					&& mainSplitPane.isVisible()) {
				return transferPanelFocus();
			}

			return false;
		};
	}

	private boolean transferPanelFocus() {
		// TODO Auto-generated method stub
		return false;
	}

	private void startQuickViewRefreshTimer() {
		quickViewRefreshTimer = new Timer(150, e -> refreshQuickViewPreview());
		quickViewRefreshTimer.start();
	}

	private void configurePanel(PanelState state, ResourceContentPlugin template, boolean leftSide) {
		try {
			ResourceContentPlugin provider = pluginRegistry.createPanelProviderInstance(template);
			List<PluginPathResource> roots = provider.getChangeDriveResources();
			if (roots.isEmpty()) {
				safeUnload(provider);
				return;
			}

			PanelLayer layer = openPanelLayer(provider, roots.get(0));
			if (layer == null) {
				return;
			}

			state.push(layer);
			renderActivePanel(state);

			if (leftSide && rightPanelState.isEmpty() ) {
				
				SwingUtilities.invokeLater(()->{
					var plugin = layer.provider;
					plugin.panel().requestFocusInWindow();
				});
				focusedPanelState = state;
				rebuildFunctionBar();
			}

			mainFrame.revalidate();
			mainFrame.repaint();
		} catch (Exception ex) {
			log.error("Failed to initialize panel provider [{}]: {}", template.getClass().getName(), ex.getMessage(), ex);
			Alerts.showMessageDialog(mainFrame, "Cannot initialize panel plugin:\n" + ex.getMessage(), "Plugin Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void renderActivePanel(PanelState state) {
		// TODO Auto-generated method stub
		
	}

	private PanelLayer openPanelLayer(ResourceContentPlugin provider, PluginPathResource pluginPathResource) {
		// TODO Auto-generated method stub
		return null;
	}

	private void safeUnload(ResourceContentPlugin provider) {
		// TODO Auto-generated method stub
		
	}

	private void showChangeDrive(boolean leftSide) {
		PanelState state = leftSide ? leftPanelState : rightPanelState;
		PanelLayer base = state.bottom();
		if (base == null) {
			return;
		}
		ChangeDrivePopup.show(
				leftSide ? mainSplitPane.getLeftComponent() : mainSplitPane.getRightComponent(),
				base.provider.getChangeDriveResources(),
				base.currentResource,
				resource -> openPanelResource(state, resource));
	}

	private Object openPanelResource(PanelState state, PluginPathResource resource) {
		return null;
	}

	private JLabel placeholder(String text) {
		JLabel label = new JLabel(text, JLabel.CENTER);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 14f));
		return label;
	}

	private void toggleQuickView() {
		if (quickViewActive) {
			closeQuickView();
			return;
		}

		PanelState sourceState = focusedPanelState;
		if (sourceState == null) {
			return;
		}

		Path selectedPath = resolveSelectedPath(sourceState);
		if (selectedPath == null) {
			return;
		}

		boolean sourceIsRight = sourceState == rightPanelState;
		quickViewShowingLeft = sourceIsRight;
		quickViewSourceState = sourceState;
		quickViewCurrentPath = selectedPath;
		quickViewReplacedComponent = quickViewShowingLeft ? mainSplitPane.getLeftComponent() : mainSplitPane.getRightComponent();

		if (quickViewShowingLeft) {
			mainSplitPane.setLeftComponent(quickViewPanel.getPanel());
		} else {
			mainSplitPane.setRightComponent(quickViewPanel.getPanel());
		}

		quickViewPanel.show(selectedPath);
		quickViewActive = true;
		mainSplitPane.setDividerLocation(0.5);
		mainFrame.revalidate();
		mainFrame.repaint();
	}

	private void closeQuickView() {
		if (!quickViewActive) {
			return;
		}

		quickViewPanel.stop();
		if (quickViewReplacedComponent != null) {
			if (quickViewShowingLeft) {
				mainSplitPane.setLeftComponent(quickViewReplacedComponent);
			} else {
				mainSplitPane.setRightComponent(quickViewReplacedComponent);
			}
		}

		quickViewReplacedComponent = null;
		quickViewSourceState = null;
		quickViewCurrentPath = null;
		quickViewActive = false;
		restoreMainDividerLocation();
		mainFrame.revalidate();
		mainFrame.repaint();
	}

	private void refreshQuickViewPreview() {
		if (!quickViewActive || quickViewSourceState == null) {
			return;
		}

		Path selectedPath = resolveSelectedPath(quickViewSourceState);
		if (selectedPath == null || selectedPath.equals(quickViewCurrentPath)) {
			return;
		}

		quickViewCurrentPath = selectedPath;
		quickViewPanel.show(selectedPath);
	}

	private Path resolveSelectedPath(PanelState state) {
		if (state == null || state.component() == null) {
			return null;
		}

		Path path = trySelectedPathMethod(state.component(), "getSelectedPath");
		if (path != null) {
			return path;
		}

		PluginPathResource selectedResource = trySelectedResourceMethod(state.component(), "getSelectedResource");
		if (selectedResource != null && selectedResource.getPath() != null) {
			return selectedResource.getPath();
		}

		Path reflectedTableSelection = tryResolveTableSelection(state.component());
		if (reflectedTableSelection != null) {
			return reflectedTableSelection;
		}

		return state.currentResource() != null ? state.currentResource().getPath() : null;
	}

	private Path trySelectedPathMethod(Object target, String methodName) {
		try {
			Method method = target.getClass().getMethod(methodName);
			Object value = method.invoke(target);
			return value instanceof Path path ? path : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private PluginPathResource trySelectedResourceMethod(Object target, String methodName) {
		try {
			Method method = target.getClass().getMethod(methodName);
			Object value = method.invoke(target);
			return value instanceof PluginPathResource resource ? resource : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private Path tryResolveTableSelection(Object panel) {
		try {
			Field tableField = panel.getClass().getDeclaredField("table");
			tableField.setAccessible(true);
			Object tableObject = tableField.get(panel);
			if (!(tableObject instanceof javax.swing.JTable table)) {
				return null;
			}

			int selectedRow = table.getSelectedRow();
			if (selectedRow < 0) {
				return null;
			}

			int modelRow = table.convertRowIndexToModel(selectedRow);
			Object model = table.getModel();
			Method getEntryAt = model.getClass().getMethod("getEntryAt", int.class);
			Object entry = getEntryAt.invoke(model, modelRow);
			if (entry == null) {
				return null;
			}

			try {
				Method pathMethod = entry.getClass().getMethod("path");
				Object path = pathMethod.invoke(entry);
				if (path instanceof Path selectedPath) {
					return selectedPath;
				}
			} catch (NoSuchMethodException ignored) {
				Method getPathMethod = entry.getClass().getMethod("getPath");
				Object path = getPathMethod.invoke(entry);
				if (path instanceof Path selectedPath) {
					return selectedPath;
				}
			}
		} catch (Exception ignored) {
			return null;
		}

		return null;
	}
	
	private void onFocusGained(ResourceContentPlugin plugin) {
		plugin.panel().requestFocusInWindow();
	}

	public void onShowConsoleScreen(ShowConsoleScreenEvent event) {
		functionKeyBar.resetDefaultLabels();
		lastFocusedInSplitPane = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		mainFrame.remove(mainSplitPane);
		mainFrame.add(consolePanel.getConsolePanel(), BorderLayout.CENTER);
		mainSplitPane.setVisible(false);
		consolePanel.getConsolePanel().setVisible(true);
		consolePanel.getTermWidget().requestFocusInWindow();
		mainFrame.revalidate();
		mainFrame.repaint();
	}

	public void onShowEditorScreen(ShowEditorScreenEvent event) {
		lastFocusedInSplitPane = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		PluginPathResource resource = new PathQuickViewItem(event.getPath());
		var provider = pluginRegistry.getPluginByResource(resource);
		if (provider == null) {
			Alerts.showMessageDialog(mainFrame, "No screen plugin can open:\n" + event.getPath().getFileName(), "No Screen Provider", JOptionPane.WARNING_MESSAGE);
			return;
		}

		activeScreenComponent = provider.panel();
		mainFrame.remove(mainSplitPane);
		mainSplitPane.setVisible(false);
		mainFrame.add(activeScreenComponent, BorderLayout.CENTER);
		activeScreenComponent.setVisible(true);
		activeScreenComponent.requestFocusInWindow();
		functionKeyBar.setLabels(Map.of(1, "Help", 10, "Exit"));
		mainFrame.revalidate();
		mainFrame.repaint();
	}

	public void onShowFilePanelsView(ShowFilePanelsViewEvent event) {
		if (activeScreenComponent != null) {
			mainFrame.remove(activeScreenComponent);
			activeScreenComponent = null;
		}
		mainFrame.remove(consolePanel.getConsolePanel());
		mainFrame.add(mainSplitPane, BorderLayout.CENTER);
		consolePanel.getConsolePanel().setVisible(false);
		mainSplitPane.setVisible(true);
		if (lastFocusedInSplitPane != null) {
			lastFocusedInSplitPane.requestFocusInWindow();
		}
		rebuildFunctionBar();
		mainFrame.revalidate();
		mainFrame.repaint();
	}

	public void onFunctionKeyCommand(FunctionKeyCommandEvent event) {
		MenuResource menuResource = event.getMenuResource();
		if (menuResource != null) {
			Map<String, Object> payload = new java.util.HashMap<>();
			payload.put("functionKeyNumber", event.getFunctionKeyNumber());
			payload.put("label", event.getLabel());
			if (focusedPanelState != null) {
				payload.put("sourceProvider", focusedPanelState.provider());
				payload.put("resource", focusedPanelState.currentResource());
			}
			eventBus.emit(menuResource.getEventType(), payload);
			return;
		}

		if (event.getFunctionKeyNumber() == 10) {
			if (activeScreenComponent != null) {
				appEventBus.publish(new ShowFilePanelsViewEvent(this));
			} else {
				confirmAndExitApplication();
			}
			return;
		}

		if (event.getFunctionKeyNumber() == 11) {
			pluginManagementPopup.show(mainFrame);
		}
	}

	private void toggleConsole() {
		if (mainSplitPane.isVisible()) {
			appEventBus.publish(new ShowConsoleScreenEvent(this));
		} else {
			appEventBus.publish(new ShowFilePanelsViewEvent(this));
		}
	}

	private void onFocusOwnerChanged(Component focusOwner) {
		if (focusOwner == null || !mainSplitPane.isVisible()) {
			return;
		}

		PanelState newFocused = null;
		if (leftPanelState.component() != null && SwingUtilities.isDescendingFrom(focusOwner, leftPanelState.component())) {
			newFocused = leftPanelState;
		} else if (rightPanelState.component() != null && SwingUtilities.isDescendingFrom(focusOwner, rightPanelState.component())) {
			newFocused = rightPanelState;
		}

		if (newFocused != null && newFocused != focusedPanelState) {
			focusedPanelState = newFocused;
			rebuildFunctionBar();
		}
	}

	private void updateModifierState(KeyEvent event) {
		boolean previousShift = shiftDown;
		boolean previousCtrl = ctrlDown;
		boolean previousAlt = altDown;

		int keyCode = event.getKeyCode();
		if (keyCode == KeyEvent.VK_SHIFT) {
			shiftDown = event.getID() == KeyEvent.KEY_PRESSED;
		}
		if (keyCode == KeyEvent.VK_CONTROL) {
			ctrlDown = event.getID() == KeyEvent.KEY_PRESSED;
		}
		if (keyCode == KeyEvent.VK_ALT) {
			altDown = event.getID() == KeyEvent.KEY_PRESSED;
		}

		if (previousShift != shiftDown || previousCtrl != ctrlDown || previousAlt != altDown) {
			rebuildFunctionBar();
		}
	}

	private void rebuildFunctionBar() {
		if (!mainSplitPane.isVisible()) {
			return;
		}
		if (focusedPanelState == null || focusedPanelState.provider() == null) {
			functionKeyBar.resetDefaultLabels();
			return;
		}

		List<MenuResource> resources = focusedPanelState.provider().menuItems(focusedPanelState.currentResource());
		functionKeyBar.setMenuResources(resources, shiftDown, ctrlDown, altDown);
	}

	private void applyFontSize(int size) {
		fontSize = size;
		UIManager.put("defaultFont", new Font("JetBrains Mono", Font.PLAIN, fontSize));
		SwingUtilities.updateComponentTreeUI(mainFrame);
		saveFontSize(fontSize);
	}

	private void toggleFullscreen() {
		if ((mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) {
			mainFrame.setExtendedState(JFrame.NORMAL);
		} else {
			mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		}
		restoreMainDividerLocation();
	}

	private void moveMainDivider(int deltaPixels) {
		int currentLocation = mainSplitPane.getDividerLocation();
		int minimumLocation = mainSplitPane.getMinimumDividerLocation();
		int maximumLocation = mainSplitPane.getMaximumDividerLocation();
		int targetLocation = Math.max(minimumLocation, Math.min(maximumLocation, currentLocation + deltaPixels));
		if (targetLocation != currentLocation) {
			mainSplitPane.setDividerLocation(targetLocation);
		}
	}

	private void restoreMainDividerLocation() {
		SwingUtilities.invokeLater(() -> {
			if (mainSplitPane.getLeftComponent() == null || mainSplitPane.getRightComponent() == null) {
				return;
			}
			mainSplitPane.setDividerLocation(dividerRatio);
		});
	}

	private void confirmAndExitApplication() {
		Object[] options = {"Yes", "No"};
		int choice = JOptionPane.showOptionDialog(
				mainFrame,
				"Exit Nuclr Commander?",
				"Confirm Exit",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[1]);
		if (choice == 0 || choice == JOptionPane.YES_OPTION) {
			Nuclr.exit();
		}
	}

	private void saveWindowState() {
		var settings = settingsStore.loadOrDefault();
		boolean isMaximized = (mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
		int width = isMaximized ? settings.windowWidth() : mainFrame.getWidth();
		int height = isMaximized ? settings.windowHeight() : mainFrame.getHeight();
		int x = isMaximized ? settings.windowX() : mainFrame.getX();
		int y = isMaximized ? settings.windowY() : mainFrame.getY();
		settingsStore.save(new LocalSettingsStore.AppSettings(
				settings.theme(),
				width,
				height,
				x,
				y,
				isMaximized,
				settings.lastOpenedPath(),
				settings.autosaveInterval(),
				dividerRatio,
				settings.colors(),
				fontSize));
	}

	private void saveDividerRatio(double ratio) {
		var settings = settingsStore.loadOrDefault();
		boolean isMaximized = (mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
		settingsStore.save(new LocalSettingsStore.AppSettings(
				settings.theme(),
				settings.windowWidth(),
				settings.windowHeight(),
				settings.windowX(),
				settings.windowY(),
				isMaximized,
				settings.lastOpenedPath(),
				settings.autosaveInterval(),
				ratio,
				settings.colors(),
				fontSize));
	}

	private void saveFontSize(int size) {
		var settings = settingsStore.loadOrDefault();
		boolean isMaximized = (mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
		settingsStore.save(new LocalSettingsStore.AppSettings(
				settings.theme(),
				settings.windowWidth(),
				settings.windowHeight(),
				settings.windowX(),
				settings.windowY(),
				isMaximized,
				settings.lastOpenedPath(),
				settings.autosaveInterval(),
				dividerRatio,
				settings.colors(),
				size));
	}

	private void applyThemeScheme() {
		var scheme = themeSchemeStore.loadOrDefault().activeThemeScheme();
		for (var entry : scheme.uiDefaults().entrySet()) {
			try {
				UIManager.put(entry.getKey(), Color.decode(entry.getValue()));
			} catch (Exception ex) {
				log.warn("Invalid theme color for {}: {}", entry.getKey(), entry.getValue());
			}
		}
	}

	private static final class PanelLayer {
		private final ResourceContentPlugin provider;
		private final JComponent component;
		private PluginPathResource currentResource;

		private PanelLayer(ResourceContentPlugin provider, JComponent component, PluginPathResource currentResource) {
			this.provider = provider;
			this.component = component;
			this.currentResource = currentResource;
		}
	}

	private static final class PanelState {
		private final ArrayDeque<PanelLayer> stack = new ArrayDeque<>();

		private boolean isEmpty() {
			return stack.isEmpty();
		}

		private int stackSize() {
			return stack.size();
		}

		private void push(PanelLayer layer) {
			stack.addLast(layer);
		}

		private PanelLayer pop() {
			return stack.removeLast();
		}

		private boolean contains(ResourceContentPlugin provider) {
			return stack.stream().anyMatch(layer -> layer.provider == provider);
		}

		private PanelLayer bottom() {
			return stack.peekFirst();
		}

		private PanelLayer top() {
			return stack.peekLast();
		}

		private ResourceContentPlugin provider() {
			PanelLayer layer = top();
			return layer != null ? layer.provider : null;
		}

		private JComponent component() {
			PanelLayer layer = top();
			return layer != null ? layer.component : null;
		}

		private PluginPathResource currentResource() {
			PanelLayer layer = top();
			return layer != null ? layer.currentResource : null;
		}

		private void setCurrentResource(PluginPathResource resource) {
			PanelLayer layer = top();
			if (layer != null) {
				layer.currentResource = resource;
			}
		}
	}

	@Override
	public void handleMessage(String type, Map<String, Object> event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isMessageSupported(String type) {
		// TODO Auto-generated method stub
		return false;
	}
}
