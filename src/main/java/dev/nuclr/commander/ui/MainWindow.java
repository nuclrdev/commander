package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Image;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Taskbar;
import java.awt.event.KeyEvent;

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

import dev.nuclr.commander.Nuclr;
import dev.nuclr.commander.common.SystemUtils;
import dev.nuclr.commander.event.FileSelectedEvent;
import dev.nuclr.commander.event.QuickViewEvent;
import dev.nuclr.commander.event.ShowConsoleScreenEvent;
import dev.nuclr.commander.event.ShowEditorScreenEvent;
import dev.nuclr.commander.event.ShowFilePanelsViewEvent;
import dev.nuclr.commander.ui.editor.EditorScreen;
import dev.nuclr.commander.ui.quickView.QuickViewPanel;
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

	@Autowired
	private ConsolePanel consolePanel;

	@Autowired
	private QuickViewPanel quickViewPanel;
	
	@Value("${version}")
	private String version;

	@Autowired
	private ApplicationEventPublisher applicationEventPublisher;
	
	@PostConstruct
	public void init() {

		log.info("Initializing MainWindow");
		
		System.setProperty("apple.laf.useScreenMenuBar", "true");
		System.setProperty("apple.awt.application.name", "Nuclr Commander");
		
		UIManager.put("defaultFont",
			    new Font("JetBrains Mono", Font.PLAIN, 16));
		
		// FlatDarkLaf.setup();
		FlatDarculaLaf.setup();

		mainFrame = new JFrame("Nuclr Commander (" + version + ")");

		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		mainFrame.setSize(1024, 768);

		mainFrame.setLocationRelativeTo(null);

		var appIcon = new ImageIcon("data/images/icon-512.png").getImage();
		mainFrame.setIconImage(appIcon);
		
		// MacOS specific: set the application menu name and icon
		if (SystemUtils.isOsMac()) {
			if (Taskbar.isTaskbarSupported()) {
				Taskbar.getTaskbar().setIconImage(appIcon);
			}
		}
		

		mainFrame.setLayout(new BorderLayout());

		mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

		mainSplitPane.setLeftComponent(new FilePanel(applicationEventPublisher));
		mainSplitPane.setRightComponent(new FilePanel(applicationEventPublisher));

		mainFrame.add(mainSplitPane, BorderLayout.CENTER);

		mainSplitPane.setDividerLocation(512);
//		split.setDividerSize(5);
		
		// Set up the menu bar
		menuBar = new JMenuBar();
		mainFrame.setJMenuBar(menuBar);
		
		// Left
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
		
		// Files
		
		// Commands
		
		// Options
		
		// Right
		
		

		// Ctrl+O toggles between mainSplitPane and consolePanel
		// Use KeyEventDispatcher to intercept before JediTermWidget consumes the key
		KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
			@Override
			public boolean dispatchKeyEvent(KeyEvent e) {
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_O
						&& e.isControlDown()) {
					if (mainSplitPane.isVisible()) {
						applicationEventPublisher.publishEvent(new ShowConsoleScreenEvent(this));
					} else {
						applicationEventPublisher.publishEvent(new ShowFilePanelsViewEvent(this));
					}
					return true; // consume the event
				}
				// Ctrl+Q toggles quick view on the opposite panel
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_Q
						&& e.isControlDown()
						&& mainSplitPane.isVisible()) {
					if (quickViewActive) {
						applicationEventPublisher.publishEvent(new QuickViewEvent(this, null));
					} else {
						var focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
						var leftPanel = (FilePanel) mainSplitPane.getLeftComponent();
						var rightPanel = mainSplitPane.getRightComponent();
						FilePanel activePanel;
						if (rightPanel instanceof FilePanel fp
								&& focusOwner != null
								&& SwingUtilities.isDescendingFrom(focusOwner, fp)) {
							activePanel = fp;
						} else {
							activePanel = leftPanel;
						}
						var selectedFile = activePanel.getSelectedFile();
						if (selectedFile != null && selectedFile.isFile()) {
							applicationEventPublisher.publishEvent(new QuickViewEvent(this, selectedFile));
						}
					}
					return true;
				}
				// Alt+F1 changes drive on left panel
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_F1
						&& e.isAltDown()
						&& mainSplitPane.isVisible()) {
					var leftPanel = (FilePanel) mainSplitPane.getLeftComponent();
					ChangeDrivePopup.show(leftPanel);
					return true;
				}
				// Alt+F2 changes drive on right panel
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_F2
						&& e.isAltDown()
						&& mainSplitPane.isVisible()) {
					var rightPanel = mainSplitPane.getRightComponent();
					if (rightPanel instanceof FilePanel fp) {
						ChangeDrivePopup.show(fp);
					}
					return true;
				}
				// Alt+F4 closes the application
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_F4
						&& e.isAltDown()) {
					Nuclr.exit();
					return true;
				}
				// Alt+Enter toggles fullscreen
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_ENTER
						&& e.isAltDown()) {
					toggleFullscreen();
					return true;
				}
				// Escape closes the editor and returns to file panels
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_ESCAPE
						&& editorScreen != null) {
					applicationEventPublisher.publishEvent(new ShowFilePanelsViewEvent(this));
					return true;
				}
				// Tab switches focus between left and right panels
				if (e.getID() == KeyEvent.KEY_PRESSED
						&& e.getKeyCode() == KeyEvent.VK_TAB
						&& !e.isControlDown()
						&& mainSplitPane.isVisible()) {
					var leftPanel = (FilePanel) mainSplitPane.getLeftComponent();
					var rightPanel = (FilePanel) mainSplitPane.getRightComponent();
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

		mainFrame.setVisible(true);

		// Ensure the left panel's table gets keyboard focus on startup
		SwingUtilities.invokeLater(() -> {
			((FilePanel) mainSplitPane.getLeftComponent()).focusFileTable();
		});

	}

	private boolean maximized = false;
	private void toggleFullscreen() {
		if (maximized) {
			mainFrame.setExtendedState(JFrame.NORMAL);
			maximized = false;
		} else {
			mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
			maximized = true;
		}
	}

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
		editorScreen = new EditorScreen(event.getFile());
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
			quickViewPanel.show(event.getFile());
		}
	}

	@EventListener
	public void onQuickView(QuickViewEvent event) {
		if (quickViewActive) {
			// Restore the original panel
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
			// Replace the opposite panel with quick view
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
			quickViewPanel.show(event.getFile());
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

}
