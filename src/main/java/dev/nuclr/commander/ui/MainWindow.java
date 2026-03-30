package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Taskbar;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
import javax.swing.SwingWorker;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.formdev.flatlaf.FlatDarculaLaf;

import dev.nuclr.commander.Nuclr;
import dev.nuclr.commander.common.LocalSettingsStore;
import dev.nuclr.commander.common.SystemUtils;
import dev.nuclr.commander.common.ThemeSchemeStore;
import dev.nuclr.commander.event.FunctionKeyCommandEvent;
import dev.nuclr.commander.event.ShowConsoleScreenEvent;
import dev.nuclr.commander.event.ShowEditorScreenEvent;
import dev.nuclr.commander.event.ShowFilePanelsViewEvent;
import dev.nuclr.commander.service.PanelTransferService;
import dev.nuclr.commander.service.PanelTransferService.AccessPolicy;
import dev.nuclr.commander.service.PanelTransferService.ConflictResolution;
import dev.nuclr.commander.service.PanelTransferService.TransferProgress;
import dev.nuclr.commander.service.PanelTransferService.TransferOptions;
import dev.nuclr.commander.service.PluginRegistry.DelegateAwarePanelProvider;
import dev.nuclr.commander.service.PluginRegistry;
import dev.nuclr.commander.ui.common.Alerts;
import dev.nuclr.commander.ui.common.TransferConfirmationDialog;
import dev.nuclr.commander.ui.common.TransferProgressDialog;
import dev.nuclr.commander.ui.functionBar.FunctionKeyBar;
import dev.nuclr.commander.ui.pluginManagement.PluginManagementPopup;
import dev.nuclr.commander.ui.quickView.PathQuickViewItem;
import dev.nuclr.commander.ui.quickView.QuickViewPanel;
import dev.nuclr.plugin.FocusablePlugin;
import dev.nuclr.plugin.MenuResource;
import dev.nuclr.plugin.PanelProviderPlugin;
import dev.nuclr.plugin.PluginPathResource;
import dev.nuclr.plugin.PluginTheme;
import dev.nuclr.plugin.ScreenProviderPlugin;
import dev.nuclr.plugin.event.PluginClosePanelEvent;
import dev.nuclr.plugin.event.PluginCopyEvent;
import dev.nuclr.plugin.event.PluginEvent;
import dev.nuclr.plugin.event.PluginMoveEvent;
import dev.nuclr.plugin.event.PluginOpenItemEvent;
import dev.nuclr.plugin.event.bus.PluginEventListener;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MainWindow implements PluginEventListener {

	private static final String LOCAL_FILE_PANEL_PROVIDER_CLASS = "dev.nuclr.plugin.core.panel.fs.LocalFilePanelProvider";
	private static final String PANEL_STACK_PROVIDER_CLASS_METADATA = "commander.panelStack.providerClass";
	private static final String OPEN_RESOURCE_EVENT_TYPE = "dev.nuclr.platform.resource.open";
	private static final String COPY_RESOURCES_EVENT_TYPE = "dev.nuclr.platform.resources.copy";
	private static final String MOVE_RESOURCES_EVENT_TYPE = "dev.nuclr.platform.resources.move";
	private static final int MAIN_DIVIDER_STEP_PIXELS = 30;

	private JFrame mainFrame;
	private JSplitPane mainSplitPane;
	private Component lastFocusedInSplitPane;
	private Component activeScreenComponent;
	private ScreenProviderPlugin activeScreenProvider;
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
	private ThemeSchemeStore themeSchemeStore;

	@Autowired
	private FunctionKeyBar functionKeyBar;

	@Autowired
	private PluginRegistry pluginRegistry;

	@Autowired
	private PluginManagementPopup pluginManagementPopup;

	@Autowired
	private PanelTransferService panelTransferService;

	private Timer quickViewRefreshTimer;

	@PostConstruct
	public void init() {
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
		startQuickViewRefreshTimer();
		pluginRegistry.getPluginContext().getEventBus().subscribe(this);

		mainFrame.setVisible(true);
		restoreMainDividerLocation();
		startPanelInitializationPoll();
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
				applicationEventPublisher.publishEvent(new ShowFilePanelsViewEvent(this));
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

	private void startPanelInitializationPoll() {
		Timer timer = new Timer(500, e -> {
			if (initializePanelsIfPossible()) {
				((Timer) e.getSource()).stop();
			}
		});
		timer.setInitialDelay(0);
		timer.start();
	}

	private void startQuickViewRefreshTimer() {
		quickViewRefreshTimer = new Timer(150, e -> refreshQuickViewPreview());
		quickViewRefreshTimer.start();
	}

	private boolean initializePanelsIfPossible() {
		List<PanelProviderPlugin> templates = pluginRegistry.getPanelProviders();
		if (templates.isEmpty()) {
			return false;
		}

		PanelProviderPlugin initialTemplate = findInitialPanelTemplate(templates);

		if (leftPanelState.isEmpty()) {
			configurePanel(leftPanelState, initialTemplate, true);
		}
		if (rightPanelState.isEmpty()) {
			configurePanel(rightPanelState, initialTemplate, false);
		}
		return !leftPanelState.isEmpty() && !rightPanelState.isEmpty();
	}

	private PanelProviderPlugin findInitialPanelTemplate(List<PanelProviderPlugin> templates) {
		for (PanelProviderPlugin template : templates) {
			if (LOCAL_FILE_PANEL_PROVIDER_CLASS.equals(template.getClass().getName())) {
				return template;
			}
		}
		return templates.get(0);
	}

	private void configurePanel(PanelState state, PanelProviderPlugin template, boolean leftSide) {
		try {
			PanelProviderPlugin provider = pluginRegistry.createPanelProviderInstance(template);
			applyTheme(provider);
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

			if (leftSide && rightPanelState.isEmpty() && layer.provider instanceof FocusablePlugin focusable) {
				SwingUtilities.invokeLater(focusable::onFocusGained);
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

	private void openPanelResource(PanelState state, PluginPathResource resource) {
		try {
			// If quick view is covering this panel side, close it so the panel can be restored
			if (quickViewActive && quickViewShowingLeft == (state == leftPanelState)) {
				closeQuickView();
			}
			// Pop any stacked plugin layers (e.g. ZIP archive) back to the base provider
			if (state.stackSize() > 1 && state == focusedPanelState
					&& state.provider() instanceof FocusablePlugin focusable) {
				focusable.onFocusLost();
			}
			while (state.stackSize() > 1) {
				safeUnload(state.pop().provider);
			}
			PanelLayer base = state.bottom();
			if (base == null || !base.provider.openItem(resource, new AtomicBoolean(false))) {
				return;
			}
			base.currentResource = resource;
			renderActivePanel(state);
			if (state == focusedPanelState) {
				if (base.provider instanceof FocusablePlugin focusable) {
					focusable.onFocusGained();
					base.component.requestFocusInWindow();
				}
				rebuildFunctionBar();
			}
			onPanelStateChanged(state);
		} catch (Exception ex) {
			log.error("Failed to open panel resource [{}]: {}", resource.getName(), ex.getMessage(), ex);
			Alerts.showMessageDialog(mainFrame, "Cannot open resource:\n" + ex.getMessage(), "Navigation Error", JOptionPane.ERROR_MESSAGE);
		}
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

	private boolean transferPanelFocus() {
		if (!(leftPanelState.provider() instanceof FocusablePlugin leftFocusable)
				|| !(rightPanelState.provider() instanceof FocusablePlugin rightFocusable)
				|| leftPanelState.component() == null
				|| rightPanelState.component() == null) {
			return false;
		}

		Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		boolean focusInRight = focusOwner != null && SwingUtilities.isDescendingFrom(focusOwner, rightPanelState.component());

		if (focusInRight) {
			SwingUtilities.invokeLater(() -> {
				rightFocusable.onFocusLost();
				leftFocusable.onFocusGained();
				focusedPanelState = leftPanelState;
				rebuildFunctionBar();
			});
		} else {
			SwingUtilities.invokeLater(() -> {
				leftFocusable.onFocusLost();
				rightFocusable.onFocusGained();
				focusedPanelState = rightPanelState;
				rebuildFunctionBar();
			});
		}

		return true;
	}

	@EventListener
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

	@EventListener
	public void onShowEditorScreen(ShowEditorScreenEvent event) {
		lastFocusedInSplitPane = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
		PluginPathResource resource = new PathQuickViewItem(event.getPath());
		ScreenProviderPlugin provider = pluginRegistry.getScreenProviderByPath(resource);
		if (provider == null) {
			Alerts.showMessageDialog(mainFrame, "No screen plugin can open:\n" + event.getPath().getFileName(), "No Screen Provider", JOptionPane.WARNING_MESSAGE);
			return;
		}

		applyTheme(provider);
		activeScreenProvider = provider;
		activeScreenComponent = provider.getPanel();
		mainFrame.remove(mainSplitPane);
		mainSplitPane.setVisible(false);
		mainFrame.add(activeScreenComponent, BorderLayout.CENTER);
		activeScreenComponent.setVisible(true);
		activeScreenComponent.requestFocusInWindow();
		functionKeyBar.setLabels(Map.of(1, "Help", 10, "Exit"));
		mainFrame.revalidate();
		mainFrame.repaint();
	}

	@EventListener
	public void onShowFilePanelsView(ShowFilePanelsViewEvent event) {
		if (activeScreenComponent != null) {
			mainFrame.remove(activeScreenComponent);
			activeScreenComponent = null;
			activeScreenProvider = null;
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

	@EventListener
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
			pluginRegistry.getPluginContext().getEventBus().emit(menuResource.getEventType(), payload);
			return;
		}

		if (event.getFunctionKeyNumber() == 10) {
			if (activeScreenComponent != null) {
				applicationEventPublisher.publishEvent(new ShowFilePanelsViewEvent(this));
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
			applicationEventPublisher.publishEvent(new ShowConsoleScreenEvent(this));
		} else {
			applicationEventPublisher.publishEvent(new ShowFilePanelsViewEvent(this));
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

		List<MenuResource> resources = focusedPanelState.provider().getMenuItems(focusedPanelState.currentResource());
		functionKeyBar.setMenuResources(resources, shiftDown, ctrlDown, altDown);
	}

	private boolean pushPanelLayer(PanelProviderPlugin caller, PluginPathResource resource) {
		if (caller == null || resource == null) {
			return false;
		}

		PanelState state = findPanelState(caller);
		if (state == null || state.provider() != caller) {
			return false;
		}

		PanelLayer nextLayer = createStackLayer(resource);
		if (nextLayer == null) {
			return false;
		}

		if (state == focusedPanelState && caller instanceof FocusablePlugin focusable) {
			focusable.onFocusLost();
		}

		state.push(nextLayer);
		renderActivePanel(state);
		if (state == focusedPanelState && nextLayer.provider instanceof FocusablePlugin focusable) {
			focusable.onFocusGained();
			nextLayer.component.requestFocusInWindow();
		}
		onPanelStateChanged(state);
		log.info("Pushed panel provider [{}] on top of [{}] stack",
				nextLayer.provider.getClass().getName(),
				caller.getClass().getName());
		return true;
	}

	private boolean popPanelLayer(PanelProviderPlugin caller) {
		if (caller == null) {
			return false;
		}

		PanelState state = findPanelState(caller);
		if (state == null || state.provider() != caller || state.stackSize() <= 1) {
			return false;
		}

		if (state == focusedPanelState && caller instanceof FocusablePlugin focusable) {
			focusable.onFocusLost();
		}

		PanelLayer removed = state.pop();
		safeUnload(removed.provider);
		renderActivePanel(state);
		restoreSelectionAfterPop(state, removed);

		if (state == focusedPanelState && state.provider() instanceof FocusablePlugin focusable) {
			focusable.onFocusGained();
		}

		onPanelStateChanged(state);
		log.info("Popped panel provider [{}] from panel stack", caller.getClass().getName());
		return true;
	}

	private void restoreSelectionAfterPop(PanelState state, PanelLayer removed) {
		if (state == null || removed == null || removed.currentResource == null || removed.currentResource.getPath() == null) {
			return;
		}
		Path selectedPath = removed.currentResource.getPath();
		Path parentPath = selectedPath.getParent();
		if (parentPath == null || state.component() == null) {
			return;
		}
		try {
			Method method = state.component().getClass().getMethod("showDirectory", Path.class, Path.class);
			method.invoke(state.component(), parentPath, selectedPath);
		} catch (NoSuchMethodException ignored) {
			// Panel does not support restoring selection by path.
		} catch (Exception ex) {
			log.debug("Could not restore selection [{}] after popping panel layer", selectedPath, ex);
		}
	}

	private PanelLayer createStackLayer(PluginPathResource resource) {
		for (PanelProviderPlugin template : orderedPanelTemplates(resource)) {
			PanelProviderPlugin provider = null;
			try {
				provider = pluginRegistry.createPanelProviderInstance(template);
				if (!provider.canSupport(resource)) {
					safeUnload(provider);
					continue;
				}
				applyTheme(provider);
				PanelLayer layer = openPanelLayer(provider, resource);
				if (layer != null) {
					return layer;
				}
			} catch (Exception ex) {
				log.warn("Failed to open resource [{}] with panel provider [{}]: {}",
						resource.getName(),
						template.getClass().getName(),
						ex.getMessage());
			}
			safeUnload(provider);
		}
		return null;
	}

	private List<PanelProviderPlugin> orderedPanelTemplates(PluginPathResource resource) {
		String preferredProviderClassName = preferredPanelProviderClass(resource);
		List<PanelProviderPlugin> templates = pluginRegistry.getPanelProviders();
		List<PanelProviderPlugin> ordered = new ArrayList<>(templates.size());
		for (PanelProviderPlugin template : templates) {
			if (Objects.equals(template.getClass().getName(), preferredProviderClassName)) {
				ordered.add(template);
				break;
			}
		}
		for (PanelProviderPlugin template : templates) {
			if (!ordered.contains(template)) {
				ordered.add(template);
			}
		}
		return ordered;
	}

	private String preferredPanelProviderClass(PluginPathResource resource) {
		if (resource == null || resource.getMetadata() == null) {
			return null;
		}
		String providerClassName = resource.getMetadata().get(PANEL_STACK_PROVIDER_CLASS_METADATA);
		return providerClassName == null || providerClassName.isBlank() ? null : providerClassName;
	}

	private PanelLayer openPanelLayer(PanelProviderPlugin provider, PluginPathResource resource) {
		try {
			JComponent component = provider.getPanel();
			if (!provider.openItem(resource, new AtomicBoolean(false))) {
				return null;
			}
			return new PanelLayer(provider, component, resource);
		} catch (Exception ex) {
			log.warn("Failed to open resource [{}] with panel provider [{}]: {}",
					resource != null ? resource.getName() : null,
					provider != null ? provider.getClass().getName() : null,
					ex.getMessage());
			return null;
		}
	}

	private void renderActivePanel(PanelState state) {
		if (state.component() == null) {
			return;
		}
		if (state == leftPanelState) {
			mainSplitPane.setLeftComponent(state.component());
		} else if (state == rightPanelState) {
			mainSplitPane.setRightComponent(state.component());
		}
		restoreMainDividerLocation();
	}

	private void onPanelStateChanged(PanelState state) {
		if (state == focusedPanelState) {
			rebuildFunctionBar();
		}
		if (quickViewActive && quickViewSourceState == state) {
			refreshQuickViewPreview();
		}
		mainFrame.revalidate();
		mainFrame.repaint();
	}

	private PanelState oppositePanelState(PanelState state) {
		if (state == leftPanelState) {
			return rightPanelState;
		}
		if (state == rightPanelState) {
			return leftPanelState;
		}
		return null;
	}

	private PanelState findPanelState(PanelProviderPlugin provider) {
		if (provider == null) {
			return null;
		}
		if (leftPanelState.contains(provider)) {
			return leftPanelState;
		}
		if (rightPanelState.contains(provider)) {
			return rightPanelState;
		}
		return null;
	}

	private Path resolveCurrentDirectory(PanelState state) {
		if (state == null || state.component() == null) {
			return null;
		}
		try {
			Method method = state.component().getClass().getMethod("getCurrentDirectory");
			Object value = method.invoke(state.component());
			return value instanceof Path path ? path : null;
		} catch (Exception ignored) {
			return null;
		}
	}

	private void refreshPanel(PanelState state) {
		Path currentDirectory = resolveCurrentDirectory(state);
		if (state == null || state.component() == null || currentDirectory == null) {
			return;
		}
		try {
			Method method = state.component().getClass().getMethod("showDirectory", Path.class);
			method.invoke(state.component(), currentDirectory);
			onPanelStateChanged(state);
		} catch (Exception ex) {
			log.warn(
					"Failed to refresh panel [{}]: {}",
					state.provider() != null ? state.provider().getClass().getName() : "unknown",
					ex.getMessage());
		}
	}

	private void refreshPanel(PanelState state, Path directory) {
		if (state == null || state.component() == null || directory == null) {
			return;
		}
		try {
			Method method = state.component().getClass().getMethod("showDirectory", Path.class);
			method.invoke(state.component(), directory);
			onPanelStateChanged(state);
		} catch (Exception ex) {
			log.warn(
					"Failed to refresh panel [{}]: {}",
					state.provider() != null ? state.provider().getClass().getName() : "unknown",
					ex.getMessage());
		}
	}

	private boolean runOnEdt(BooleanSupplier action) {
		if (SwingUtilities.isEventDispatchThread()) {
			return action.getAsBoolean();
		}

		AtomicReference<Boolean> result = new AtomicReference<>(false);
		try {
			SwingUtilities.invokeAndWait(() -> result.set(action.getAsBoolean()));
		} catch (Exception ex) {
			log.warn("Failed to execute panel stack action on EDT: {}", ex.getMessage(), ex);
			return false;
		}
		return Boolean.TRUE.equals(result.get());
	}

	private void safeUnload(PanelProviderPlugin provider) {
		if (provider == null) {
			return;
		}
		try {
			provider.unload();
		} catch (Exception ex) {
			log.warn("Failed to unload panel provider [{}]: {}", provider.getClass().getName(), ex.getMessage());
		}
	}

	@Override
	public boolean isMessageSupported(PluginEvent msg) {
		return msg instanceof PluginOpenItemEvent
				|| msg instanceof PluginClosePanelEvent
				|| msg instanceof PluginCopyEvent
				|| msg instanceof PluginMoveEvent;
	}

	@Override
	public void handleMessage(PluginEvent event) {
		if (event instanceof PluginOpenItemEvent openEvent) {
			runOnEdt(() -> handlePanelOpenRequest(openEvent));
			return;
		}
		if (event instanceof PluginClosePanelEvent closeEvent) {
			runOnEdt(() -> handlePanelCloseRequest(closeEvent));
			return;
		}
		if (event instanceof PluginCopyEvent copyEvent) {
			runOnEdt(() -> handleCopyRequest(copyEvent));
			return;
		}
		if (event instanceof PluginMoveEvent moveEvent) {
			runOnEdt(() -> handleMoveRequest(moveEvent));
		}
	}

	private boolean handlePanelOpenRequest(PluginOpenItemEvent event) {
		if (event == null || event.isHandled()) {
			return false;
		}
		boolean handled = pushPanelLayer(event.getSourceProvider(), event.getResource());
		event.setHandled(handled);
		return handled;
	}

	private boolean handlePanelCloseRequest(PluginClosePanelEvent event) {
		if (event == null || event.isHandled()) {
			return false;
		}
		boolean handled = popPanelLayer(event.getSourceProvider());
		event.setHandled(handled);
		return handled;
	}

	private boolean handleCopyRequest(PluginCopyEvent event) {
		return handleTransferRequest(event.getSourceProvider(), event.getSources(), false, event::setHandled);
	}

	private boolean handleMoveRequest(PluginMoveEvent event) {
		return handleTransferRequest(event.getSourceProvider(), event.getSources(), true, event::setHandled);
	}

	private boolean handleTransferRequest(
			PanelProviderPlugin sourceProvider,
			List<PluginPathResource> sources,
			boolean move,
			java.util.function.Consumer<Boolean> handledCallback) {
		if (sourceProvider == null || sources == null || sources.isEmpty()) {
			handledCallback.accept(false);
			return false;
		}

		PanelState sourceState = findPanelState(sourceProvider);
		PanelState destinationState = oppositePanelState(sourceState);
		Path destinationDirectory = resolveCurrentDirectory(destinationState);
		if (sourceState == null || destinationState == null || destinationDirectory == null) {
			handledCallback.accept(false);
			return false;
		}

		try {
			TransferOptions transferOptions = prepareTransferOptions(sourceState, destinationState, sources, destinationDirectory, move);
			if (transferOptions == null) {
				restorePanelFocus(sourceState);
				handledCallback.accept(false);
				return false;
			}
			Path effectiveDestination = transferOptions.destinationDirectory();
			ensureDestinationDirectoryExists(effectiveDestination);
			startTransferWorker(sourceState, destinationState, sources, transferOptions, move, handledCallback);
			return true;
		} catch (Exception ex) {
			log.error("Failed to {} items to [{}]: {}", move ? "move" : "copy", destinationDirectory, ex.getMessage(), ex);
			Alerts.showMessageDialog(
					mainFrame,
					"Cannot " + (move ? "move" : "copy") + " files:\n" + describeException(ex),
					move ? "Move Error" : "Copy Error",
					JOptionPane.ERROR_MESSAGE);
			restorePanelFocus(sourceState);
			handledCallback.accept(false);
			return false;
		}
	}

	@Override
	public boolean isMessageSupported(String type) {
		return OPEN_RESOURCE_EVENT_TYPE.equals(type)
				|| COPY_RESOURCES_EVENT_TYPE.equals(type)
				|| MOVE_RESOURCES_EVENT_TYPE.equals(type);
	}

	@Override
	public void handleMessage(String type, Map<String, Object> event) {
		if (OPEN_RESOURCE_EVENT_TYPE.equals(type)) {
			PanelProviderPlugin provider = resolvePanelProviderReference(event != null ? event.get("sourceProvider") : null);
			PluginPathResource resource = event != null && event.get("resource") instanceof PluginPathResource pathResource
					? pathResource
					: null;
			if (provider != null && resource != null) {
				runOnEdt(() -> pushPanelLayer(provider, resource));
			}
			return;
		}
		if (COPY_RESOURCES_EVENT_TYPE.equals(type) || MOVE_RESOURCES_EVENT_TYPE.equals(type)) {
			PanelProviderPlugin provider = resolvePanelProviderReference(event != null ? event.get("sourceProvider") : null);
			List<PluginPathResource> resources = toPluginResources(event != null ? event.get("resources") : null);
			boolean move = MOVE_RESOURCES_EVENT_TYPE.equals(type);
			if (provider != null && !resources.isEmpty()) {
				runOnEdt(() -> handleTransferRequest(provider, resources, move, handled -> {
				}));
			}
		}
	}

	private List<PluginPathResource> toPluginResources(Object payload) {
		if (!(payload instanceof List<?> list)) {
			return List.of();
		}
		return list.stream()
				.filter(PluginPathResource.class::isInstance)
				.map(PluginPathResource.class::cast)
				.toList();
	}

	private PanelProviderPlugin resolvePanelProviderReference(Object providerRef) {
		if (providerRef instanceof PanelProviderPlugin pluginProvider) {
			return pluginProvider;
		}
		return resolvePanelProviderInState(leftPanelState, providerRef, rightPanelState);
	}

	private PanelProviderPlugin resolvePanelProviderInState(PanelState primary, Object providerRef, PanelState secondary) {
		PanelProviderPlugin resolved = findWrappedProvider(primary, providerRef);
		return resolved != null ? resolved : findWrappedProvider(secondary, providerRef);
	}

	private PanelProviderPlugin findWrappedProvider(PanelState state, Object providerRef) {
		if (state == null || providerRef == null) {
			return null;
		}
		for (PanelLayer layer : state.stack) {
			if (layer.provider == providerRef) {
				return layer.provider;
			}
			if (layer.provider instanceof DelegateAwarePanelProvider delegateAware
					&& delegateAware.wrapsDelegate(providerRef)) {
				return layer.provider;
			}
		}
		return null;
	}

	private TransferOptions prepareTransferOptions(
			PanelState sourceState,
			PanelState destinationState,
			List<PluginPathResource> sources,
			Path destinationDirectory,
			boolean move) {
		boolean archiveSource = isArchiveSource(sourceState, sources);
		boolean archiveDestination = isArchiveDestination(destinationState, destinationDirectory);
		TransferConfirmationDialog.Result result = TransferConfirmationDialog.show(
				mainFrame,
				new TransferConfirmationDialog.Model(
						transferDialogTitle(archiveSource, archiveDestination, move, sources.size()),
						transferDestinationLabel(archiveSource, archiveDestination, move, sources.size()),
						destinationDirectory,
						sources,
						defaultConflictResolution(archiveSource, archiveDestination),
						move ? "Move" : "Copy",
						AccessPolicy.DEFAULT,
						false,
						false,
						false,
						null));
		if (result == null) {
			return null;
		}
		return new TransferOptions(
				result.destinationDirectories().isEmpty() ? destinationDirectory : result.destinationDirectories().get(0),
				result.destinationDirectories(),
				result.conflictResolution(),
				(sourcePath, targetPath, directory) -> showConflictResolutionDialog(sourcePath, targetPath, directory, move),
				null,
				null,
				result.accessPolicy(),
				result.preserveTimestamps(),
				result.followSymbolicLinks(),
				result.filterExpression(),
				null);
	}

	private void startTransferWorker(
			PanelState sourceState,
			PanelState destinationState,
			List<PluginPathResource> sources,
			TransferOptions transferOptions,
			boolean move,
			java.util.function.Consumer<Boolean> handledCallback) {
		TransferProgressDialog progressDialog = new TransferProgressDialog(mainFrame, move);
		AtomicBoolean transferStarted = new AtomicBoolean(false);
		TransferOptions workerOptions = new TransferOptions(
				transferOptions.destinationDirectory(),
				transferOptions.destinationDirectories(),
				transferOptions.conflictResolution(),
				transferOptions.conflictResolver(),
				progressDialog::updateProgress,
				progressDialog::isCancelRequested,
				transferOptions.accessPolicy(),
				transferOptions.preserveTimestamps(),
				transferOptions.followSymbolicLinks(),
				transferOptions.filterExpression(),
				transferOptions.filterMatchers());

		SwingWorker<Void, TransferProgress> worker = new SwingWorker<>() {
			@Override
			protected Void doInBackground() throws Exception {
				transferStarted.set(true);
				if (move) {
					panelTransferService.move(sources, workerOptions);
				} else {
					panelTransferService.copy(sources, workerOptions);
				}
				return null;
			}

			@Override
			protected void done() {
				progressDialog.closeDialog();
				try {
					get();
					refreshPanel(sourceState);
					refreshPanel(destinationState, workerOptions.destinationDirectory());
				} catch (ExecutionException ex) {
					Throwable cause = ex.getCause();
					if (cause instanceof Exception exception && "Transfer cancelled".equals(exception.getMessage())) {
						restorePanelFocus(sourceState);
						handledCallback.accept(false);
						return;
					}
					Exception exception = cause instanceof Exception known ? known : new Exception(cause);
					log.error(
							"Failed to {} items to [{}]: {}",
							move ? "move" : "copy",
							workerOptions.destinationDirectory(),
							exception.getMessage(),
							exception);
					Alerts.showMessageDialog(
							mainFrame,
							"Cannot " + (move ? "move" : "copy") + " files:\n" + describeException(exception),
							move ? "Move Error" : "Copy Error",
							JOptionPane.ERROR_MESSAGE);
					restorePanelFocus(sourceState);
					handledCallback.accept(false);
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					restorePanelFocus(sourceState);
					handledCallback.accept(false);
					return;
				}
				restorePanelFocus(sourceState);
			}
		};

		worker.execute();
		progressDialog.showDialog();
		handledCallback.accept(transferStarted.get() || !sources.isEmpty());
	}

	private void restorePanelFocus(PanelState state) {
		if (state == null || state.component() == null || !mainSplitPane.isVisible()) {
			return;
		}
		focusedPanelState = state;
		rebuildFunctionBar();
		SwingUtilities.invokeLater(() -> {
			if (state.component() != null) {
				state.component().requestFocusInWindow();
			}
		});
	}

	private ConflictResolution showConflictResolutionDialog(Path sourcePath, Path targetPath, boolean directory, boolean move) {
		if (!SwingUtilities.isEventDispatchThread()) {
			AtomicReference<ConflictResolution> result = new AtomicReference<>(ConflictResolution.SKIP);
			try {
				SwingUtilities.invokeAndWait(() -> result.set(showConflictResolutionDialog(sourcePath, targetPath, directory, move)));
			} catch (Exception ex) {
				log.warn("Failed to show transfer conflict dialog: {}", ex.getMessage(), ex);
			}
			return result.get();
		}
		StringBuilder message = new StringBuilder();
		message.append(directory ? "Folder already exists:" : "File already exists:")
				.append('\n')
				.append(targetPath)
				.append('\n')
				.append('\n')
				.append(move ? "Move source:" : "Copy source:")
				.append('\n')
				.append(sourcePath);
		Object[] options = {"Overwrite", "Skip", "Rename", "Cancel"};
		int choice = JOptionPane.showOptionDialog(
				mainFrame,
				message.toString(),
				move ? "Move Conflict" : "Copy Conflict",
				JOptionPane.DEFAULT_OPTION,
				JOptionPane.QUESTION_MESSAGE,
				null,
				options,
				options[0]);
		return switch (choice) {
			case 0 -> ConflictResolution.OVERWRITE;
			case 1 -> ConflictResolution.SKIP;
			case 2 -> ConflictResolution.RENAME;
			default -> ConflictResolution.SKIP;
		};
	}

	private boolean isZipRelatedTransfer(
			PanelState sourceState,
			PanelState destinationState,
			List<PluginPathResource> sources,
			Path destinationDirectory) {
		return isArchiveSource(sourceState, sources) || isArchiveDestination(destinationState, destinationDirectory);
	}

	private boolean isArchiveSource(PanelState sourceState, List<PluginPathResource> sources) {
		if (sourceState != null && isZipProvider(sourceState.provider())) {
			return true;
		}
		return sources.stream()
				.filter(Objects::nonNull)
				.map(PluginPathResource::getPath)
				.filter(Objects::nonNull)
				.anyMatch(path -> !path.getFileSystem().equals(FileSystems.getDefault()));
	}

	private boolean isArchiveDestination(PanelState destinationState, Path destinationDirectory) {
		if (destinationState != null && isZipProvider(destinationState.provider())) {
			return true;
		}
		return destinationDirectory != null && !destinationDirectory.getFileSystem().equals(FileSystems.getDefault());
	}

	private boolean isZipProvider(PanelProviderPlugin provider) {
		return provider != null && provider.getClass().getName().contains(".mount.zip.");
	}

	private String transferDialogTitle(boolean archiveSource, boolean archiveDestination, boolean move) {
		return transferDialogTitle(archiveSource, archiveDestination, move, 0);
	}

	private String transferDialogTitle(boolean archiveSource, boolean archiveDestination, boolean move, int sourceCount) {
		if (archiveDestination && !archiveSource) {
			return move ? "Move To Archive" : "Copy To Archive";
		}
		if (archiveSource && !archiveDestination) {
			return move ? "Move From Archive" : "Extract Files";
		}
		String base = move ? "Move" : "Copy";
		return sourceCount > 0 ? base + " " + sourceCount + (sourceCount == 1 ? " item" : " items") : base;
	}

	private String transferDestinationLabel(boolean archiveSource, boolean archiveDestination, boolean move) {
		return transferDestinationLabel(archiveSource, archiveDestination, move, 0);
	}

	private String transferDestinationLabel(boolean archiveSource, boolean archiveDestination, boolean move, int sourceCount) {
		if (archiveDestination && !archiveSource) {
			return (move ? "Move " : "Copy ") + itemCountLabel(sourceCount) + " to archive:";
		}
		if (archiveSource && !archiveDestination) {
			return (move ? "Move " : "Extract ") + itemCountLabel(sourceCount) + " to:";
		}
		return (move ? "Move " : "Copy ") + itemCountLabel(sourceCount) + " to:";
	}

	private ConflictResolution defaultConflictResolution(boolean archiveSource, boolean archiveDestination) {
		return ConflictResolution.ASK;
	}

	private String itemCountLabel(int sourceCount) {
		if (sourceCount <= 0) {
			return "items";
		}
		return sourceCount == 1 ? "1 item" : sourceCount + " items";
	}

	private void ensureDestinationDirectoryExists(Path destinationDirectory) throws Exception {
		if (destinationDirectory == null) {
			throw new IllegalArgumentException("Destination directory is not available");
		}
		if (Files.exists(destinationDirectory) && !Files.isDirectory(destinationDirectory)) {
			throw new IllegalArgumentException("Destination path is not a directory: " + destinationDirectory);
		}
		if (!Files.exists(destinationDirectory)) {
			Files.createDirectories(destinationDirectory);
		}
	}

	private String describeException(Exception ex) {
		if (ex == null) {
			return "Unknown error";
		}
		String message = ex.getMessage();
		return message != null && !message.isBlank() ? message : ex.getClass().getSimpleName();
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

	private void applyTheme(Object plugin) {
		PluginTheme theme = currentPluginTheme();
		try {
			plugin.getClass().getMethod("applyTheme", PluginTheme.class).invoke(plugin, theme);
		} catch (NoSuchMethodException ignored) {
			// Theme support is optional.
		} catch (Exception ex) {
			log.warn("Failed to apply theme to plugin [{}]: {}", plugin.getClass().getName(), ex.getMessage());
		}
	}

	private PluginTheme currentPluginTheme() {
		var scheme = themeSchemeStore.loadOrDefault().activeThemeScheme();
		Font defaultFont = UIManager.getFont("defaultFont");
		String fontFamily = defaultFont != null ? defaultFont.getFamily() : Font.MONOSPACED;
		int themeFontSize = defaultFont != null ? defaultFont.getSize() : 12;
		return new PluginTheme(scheme.name(), scheme.uiDefaults(), fontFamily, themeFontSize);
	}

	@FunctionalInterface
	private interface BooleanSupplier {
		boolean getAsBoolean();
	}

	private static final class PanelLayer {
		private final PanelProviderPlugin provider;
		private final JComponent component;
		private PluginPathResource currentResource;

		private PanelLayer(PanelProviderPlugin provider, JComponent component, PluginPathResource currentResource) {
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

		private boolean contains(PanelProviderPlugin provider) {
			return stack.stream().anyMatch(layer -> layer.provider == provider);
		}

		private PanelLayer bottom() {
			return stack.peekFirst();
		}

		private PanelLayer top() {
			return stack.peekLast();
		}

		private PanelProviderPlugin provider() {
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
}
