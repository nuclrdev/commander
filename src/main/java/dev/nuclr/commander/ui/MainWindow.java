package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JSplitPane;
import javax.swing.UIManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;

import dev.nuclr.commander.common.AppVersion;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MainWindow {

	private JFrame mainFrame;
	
	private JMenuBar menuBar;
	
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

		JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

		split.setLeftComponent(new FilePanel(applicationEventPublisher));
		split.setRightComponent(new FilePanel(applicationEventPublisher));

		mainFrame.add(split, BorderLayout.CENTER);

		split.setDividerLocation(400);
		split.setDividerSize(10);
		
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
		
		

		mainFrame.setVisible(true);

	}
	
}
