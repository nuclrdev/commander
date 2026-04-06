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

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Taskbar;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.formdev.flatlaf.FlatDarculaLaf;

import dev.nuclr.commander.Nuclr;
import dev.nuclr.commander.common.LocalSettingsStore;
import dev.nuclr.commander.common.SystemUtils;
import dev.nuclr.commander.common.ThemeSchemeStore;
import dev.nuclr.commander.event.Events;
import dev.nuclr.commander.plugin.PluginRegistry;
import dev.nuclr.commander.service.PanelTransferService;
import dev.nuclr.commander.ui.ConsolePanel;
import dev.nuclr.commander.ui.functionBar.FunctionKeyBar;
import dev.nuclr.commander.ui.pluginManagement.PluginManagementPopup;
import dev.nuclr.platform.Settings;
import dev.nuclr.platform.events.NuclrEventBus;
import dev.nuclr.platform.events.NuclrEventListener;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Main application window. Manages the main UI layout, global keyboard
 * shortcuts, and screen navigation.
 */
@Service
@Slf4j
public class MainWindow implements NuclrEventListener {

	public static final String SettingsNamespace = "MainWindow";	
	
	private static final int MAIN_DIVIDER_STEP_PIXELS = 30;

	private JFrame mainFrame;
	
	private int fontSize;
	
	private boolean shiftDown;
	private boolean ctrlDown;
	private boolean altDown;
	
	@Autowired
	private Settings settings;
	
	@Autowired
	private ConsolePanel consolePanel;

	@Value("${version}")
	private String version;

	@Autowired
	private ThemeSchemeStore themeSchemeStore;

	@Autowired
	private FunctionKeyBar functionKeyBar;

	@Autowired
	private PluginRegistry pluginRegistry;

	@Autowired
	private NuclrEventBus eventBus;

	@Autowired
	private PluginManagementPopup pluginManagementPopup;

	@Autowired
	private PanelTransferService panelTransferService;
	
	@Autowired	
	private SplitPanel splitPane;

	private Timer quickViewRefreshTimer;
	
	private JComponent activeScreenComponent;

	@PostConstruct
	public void init() {

		this.eventBus.subscribe(this);

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

		this.fontSize = settings.getOrDefault(SettingsNamespace, "fontSize", 14);

		FlatDarculaLaf.setup();
		
		applyThemeScheme();
		
		UIManager.put("defaultFont", new Font("JetBrains Mono", Font.PLAIN, fontSize));
		UIManager.put("Button.font", UIManager.getFont("defaultFont"));

		mainFrame = new JFrame("Nuclr Commander (" + version + ")");
		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		mainFrame.setLayout(new CardLayout());

		// Restore window size and position from settings
		mainFrame.setSize(
			this.settings.getOrDefault(SettingsNamespace, "windowWidth", 1024),
			this.settings.getOrDefault(SettingsNamespace, "windowHeight", 768)
		);
		
		// Restore window position if valid coordinates are available, otherwise center on screen
		var windowX = this.settings.getOrDefault(SettingsNamespace, "windowX", -1);
		var windowY = this.settings.getOrDefault(SettingsNamespace, "windowY", -1);

		if (windowX >= 0 && windowY >= 0) {
			mainFrame.setLocation(windowX, windowY);
		} else {
			mainFrame.setLocationRelativeTo(null);
		}

		if (this.settings.getOrDefault(SettingsNamespace, "extendedState", false)) {
			mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		}

		var appIcon = new ImageIcon("data/images/icon-512.png").getImage();

		mainFrame.setIconImage(appIcon);

		if (SystemUtils.isOsMac() && Taskbar.isTaskbarSupported()) {
			Taskbar.getTaskbar().setIconImage(appIcon);
		}


		mainFrame.setJMenuBar(buildMenuBar());
		mainFrame.add(this.splitPane, BorderLayout.CENTER);
		mainFrame.add(functionKeyBar.getPanel(), BorderLayout.SOUTH);

		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(buildKeyDispatcher());

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

		setActiveScreenComponent( this.splitPane);

		mainFrame.setVisible(true);

	}

	protected void saveWindowState() {
		// TODO Auto-generated method stub
		
	}

	private void setActiveScreenComponent(JComponent c) {
		this.activeScreenComponent = c;
	}

	private JMenuBar buildMenuBar() {
		JMenuBar menuBar = new JMenuBar();

		JMenu leftMenu = new JMenu("Left");
		leftMenu.setMnemonic(KeyEvent.VK_L);
		leftMenu.add(item("Change drive", KeyStroke.getKeyStroke(KeyEvent.VK_F1, InputEvent.ALT_DOWN_MASK),
				e -> eventBus.emit(Events.ShowChangeDriveLeftPopup)));
		menuBar.add(leftMenu);

		JMenu commandsMenu = new JMenu("Commands");
		commandsMenu.setMnemonic(KeyEvent.VK_C);
		commandsMenu.add(item("Plugin commands", KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0),
				e -> pluginManagementPopup.show(mainFrame)));
		menuBar.add(commandsMenu);

		JMenu optionsMenu = new JMenu("Options");
		optionsMenu.setMnemonic(KeyEvent.VK_O);
		optionsMenu.add(item("Toggle console", KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK),
				e -> toggleConsole()));
		menuBar.add(optionsMenu);

		JMenu rightMenu = new JMenu("Right");
		rightMenu.setMnemonic(KeyEvent.VK_R);
		rightMenu.add(item("Change drive", KeyStroke.getKeyStroke(KeyEvent.VK_F2, InputEvent.ALT_DOWN_MASK),
				e -> eventBus.emit(Events.ShowChangeDriveRightPopup)));
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

			if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_Q && e.isControlDown()
					&& isVisible(splitPane)) {
				splitPane.toggleQuickView();
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_F1 && e.isAltDown()
					&& isVisible(splitPane)) {
				eventBus.emit(Events.ShowChangeDriveLeftPopup);
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_F2 && e.isAltDown()
					&& isVisible(splitPane)) {
				eventBus.emit(Events.ShowChangeDriveRightPopup);
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && !e.isAltDown() && !e.isControlDown()
					&& e.getKeyCode() == KeyEvent.VK_F10) {
				confirmAndExitApplication();
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && !e.isAltDown() && !e.isControlDown()
					&& e.getKeyCode() >= KeyEvent.VK_F1 && e.getKeyCode() <= KeyEvent.VK_F12) {
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

			if (e.getID() == KeyEvent.KEY_PRESSED && e.isControlDown() && !e.isAltDown()
					&& (e.getKeyCode() == KeyEvent.VK_EQUALS || e.getKeyCode() == KeyEvent.VK_PLUS
							|| e.getKeyCode() == KeyEvent.VK_ADD)) {
				applyFontSize(Math.min(fontSize + 1, 32));
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.isControlDown() && !e.isAltDown()
					&& (e.getKeyCode() == KeyEvent.VK_MINUS || e.getKeyCode() == KeyEvent.VK_SUBTRACT)) {
				applyFontSize(Math.max(fontSize - 1, 8));
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.isControlDown() && !e.isAltDown()
					&& e.getKeyCode() == KeyEvent.VK_0) {
				applyFontSize(LocalSettingsStore.DEFAULT_FONT_SIZE);
				return true;
			}

			if (e.getID() == KeyEvent.KEY_PRESSED && e.getKeyCode() == KeyEvent.VK_TAB && !e.isAltDown()
					&& !e.isControlDown() && isVisible(splitPane)) {
				return transferPanelFocus();
			}

			return false;
		};
	}

	private boolean isVisible(SplitPanel c) {
		return this.activeScreenComponent == c;
	}

	private boolean transferPanelFocus() {
		// TODO Auto-generated method stub
		return false;
	}

	private void toggleConsole() {
		if (isVisible(splitPane)) {
			eventBus.emit(Events.ShowConsoleScreenEvent);
		} else {
			eventBus.emit(Events.ShowFilePanelsViewEvent);
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
		
		if (!isVisible(splitPane)) {
			return;
		}
		
		functionKeyBar.resetDefaultLabels();

//		List<NuclrMenuResource> resources = focusedPanelState.provider().menuItems(focusedPanelState.currentResource());
//		functionKeyBar.setMenuResources(resources, shiftDown, ctrlDown, altDown);
	}

	private void applyFontSize(int size) {
		fontSize = size;
		UIManager.put("defaultFont", new Font("JetBrains Mono", Font.PLAIN, fontSize));
		SwingUtilities.updateComponentTreeUI(mainFrame);
		saveFontSize(fontSize);
	}

	private void saveFontSize(int fontSize) {
		this.settings.set(SettingsNamespace, "fontSize", fontSize);
	}

	private void toggleFullscreen() {
		if ((mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0) {
			mainFrame.setExtendedState(JFrame.NORMAL);
		} else {
			mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
		}
	}

	private void confirmAndExitApplication() {
		Object[] options = { "Yes", "No" };
		int choice = JOptionPane.showOptionDialog(mainFrame, "Exit Nuclr Commander?", "Confirm Exit",
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]);
		if (choice == 0 || choice == JOptionPane.YES_OPTION) {
			Nuclr.exit();
		}
	}

	/*
	private void saveWindowState() {
		var settings = settingsStore.loadOrDefault();
		boolean isMaximized = (mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
		int width = isMaximized ? settings.windowWidth() : mainFrame.getWidth();
		int height = isMaximized ? settings.windowHeight() : mainFrame.getHeight();
		int x = isMaximized ? settings.windowX() : mainFrame.getX();
		int y = isMaximized ? settings.windowY() : mainFrame.getY();
		settingsStore.save(new LocalSettingsStore.AppSettings(settings.theme(), width, height, x, y, isMaximized,
				settings.lastOpenedPath(), settings.autosaveInterval(), dividerRatio, settings.colors(), fontSize));
	}

	private void saveFontSize(int size) {
		var settings = settingsStore.loadOrDefault();
		boolean isMaximized = (mainFrame.getExtendedState() & JFrame.MAXIMIZED_BOTH) != 0;
		settingsStore.save(new LocalSettingsStore.AppSettings(settings.theme(), settings.windowWidth(),
				settings.windowHeight(), settings.windowX(), settings.windowY(), isMaximized, settings.lastOpenedPath(),
				settings.autosaveInterval(), dividerRatio, settings.colors(), size));
	}
	 */

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

	@Override
	public void handleMessage(String source, String type, Map<String, Object> event) {

		if (type.equals(Events.ShowFilePanelsViewEvent)) {
		//	onShowFilePanelsView();
		}
		
		

	}

	@Override
	public boolean isMessageSupported(String type) {
		// TODO Auto-generated method stub
		return false;
	}
	
	public JFrame getMainFrame() {
		return mainFrame;
	}

}
