package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyEvent;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JSplitPane;
import javax.swing.UIManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.formdev.flatlaf.FlatDarculaLaf;

import dev.nuclr.commander.common.AppVersion;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MainWindow {

	private JFrame mainFrame;
	
	private JMenuBar menuBar;
	
	private JSplitPane mainSplitPane;

	private Component lastFocusedInSplitPane;
	
	@Autowired
	private ConsolePanel consolePanel;
	
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

		mainFrame = new JFrame("Nuclr Commander (" + AppVersion.get() + ")");

		mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		mainFrame.setSize(1024, 768);

		mainFrame.setLocationRelativeTo(null);

		mainFrame.setIconImage(new ImageIcon("data/images/icon-512.png").getImage());

		mainFrame.setLayout(new BorderLayout());

		mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

		mainSplitPane.setLeftComponent(new FilePanel(applicationEventPublisher));
		mainSplitPane.setRightComponent(new FilePanel(applicationEventPublisher));

		mainFrame.add(mainSplitPane, BorderLayout.CENTER);

		mainSplitPane.setDividerLocation(400);
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
						lastFocusedInSplitPane = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
						mainFrame.remove(mainSplitPane);
						mainFrame.add(consolePanel.getConsolePanel(), BorderLayout.CENTER);
						mainSplitPane.setVisible(false);
						consolePanel.getConsolePanel().setVisible(true);
						consolePanel.getTermWidget().requestFocusInWindow();
					} else {
						mainFrame.remove(consolePanel.getConsolePanel());
						mainFrame.add(mainSplitPane, BorderLayout.CENTER);
						consolePanel.getConsolePanel().setVisible(false);
						mainSplitPane.setVisible(true);
						if (lastFocusedInSplitPane != null) {
							lastFocusedInSplitPane.requestFocusInWindow();
						}
					}
					mainFrame.revalidate();
					mainFrame.repaint();
					return true; // consume the event
				}
				return false;
			}
		});

		mainFrame.setVisible(true);

	}
	
}
