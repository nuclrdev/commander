package dev.nuclr.commander.ui.quickView;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.common.FilenameUtils;
import dev.nuclr.commander.service.PluginMarketplaceService;
import dev.nuclr.commander.service.SystemSettings;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NoQuickViewAvailablePanel extends JPanel {

	private static final Color BG_COLOR = new Color(0x2B, 0x2B, 0x2B);
	private static final Color ACCENT_COLOR = new Color(0x4E, 0x9A, 0xE1);
	private static final Color TEXT_PRIMARY = new Color(0xBB, 0xBB, 0xBB);
	private static final Color TEXT_SECONDARY = new Color(0x78, 0x78, 0x78);
	private static final Color TEXT_MUTED = new Color(0x5A, 0x5A, 0x5A);
	private static final Color BUTTON_BG = new Color(0x3C, 0x3F, 0x41);
	private static final Color BUTTON_HOVER = new Color(0x4C, 0x50, 0x52);
	private static final Color LINK_COLOR = new Color(0x58, 0x9D, 0xF6);
	private static final Color WARNING_COLOR = new Color(0xBB, 0x86, 0x3B);

	private static final String GITHUB_ISSUES_URL = "https://github.com/nuclrdev/commander/issues";

	@Autowired
	private SystemSettings systemSettings;

	@Autowired
	private PluginMarketplaceService pluginMarketplaceService;

	private JLabel iconLabel;
	private JLabel titleLabel;
	private JLabel extensionLabel;
	private JLabel fileNameLabel;
	private JPanel actionsPanel;

	private boolean uiBuilt = false;

	private void buildUI() {
		setLayout(new BorderLayout());
		setBackground(BG_COLOR);

		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		centerPanel.setBackground(BG_COLOR);
		centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

		iconLabel = new JLabel("?", SwingConstants.CENTER);
		iconLabel.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 64));
		iconLabel.setForeground(TEXT_MUTED);
		iconLabel.setAlignmentX(CENTER_ALIGNMENT);

		titleLabel = new JLabel("No Quick View Available");
		titleLabel.setFont(new Font("JetBrains Mono", Font.BOLD, 18));
		titleLabel.setForeground(TEXT_PRIMARY);
		titleLabel.setAlignmentX(CENTER_ALIGNMENT);
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

		extensionLabel = new JLabel(" ");
		extensionLabel.setFont(new Font("JetBrains Mono", Font.PLAIN, 15));
		extensionLabel.setForeground(TEXT_SECONDARY);
		extensionLabel.setAlignmentX(CENTER_ALIGNMENT);
		extensionLabel.setHorizontalAlignment(SwingConstants.CENTER);

		fileNameLabel = new JLabel(" ");
		fileNameLabel.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
		fileNameLabel.setForeground(TEXT_MUTED);
		fileNameLabel.setAlignmentX(CENTER_ALIGNMENT);
		fileNameLabel.setHorizontalAlignment(SwingConstants.CENTER);

		actionsPanel = new JPanel();
		actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.Y_AXIS));
		actionsPanel.setBackground(BG_COLOR);
		actionsPanel.setAlignmentX(CENTER_ALIGNMENT);

		centerPanel.add(Box.createVerticalGlue());
		centerPanel.add(iconLabel);
		centerPanel.add(Box.createVerticalStrut(14));
		centerPanel.add(titleLabel);
		centerPanel.add(Box.createVerticalStrut(6));
		centerPanel.add(extensionLabel);
		centerPanel.add(Box.createVerticalStrut(2));
		centerPanel.add(fileNameLabel);
		centerPanel.add(Box.createVerticalStrut(20));
		centerPanel.add(actionsPanel);
		centerPanel.add(Box.createVerticalGlue());

		add(centerPanel, BorderLayout.CENTER);
		uiBuilt = true;
	}

	public void setPath(Path path) {
		if (!uiBuilt) buildUI();

		var fn = path.getFileName();
		String filename = fn != null ? fn.toString() : path.toString();
		String ext = FilenameUtils.getExtension(filename).toLowerCase();
		String displayExt = ext.isEmpty() ? "unknown" : "." + ext;

		extensionLabel.setText("No viewer plugin for " + displayExt + " files");
		fileNameLabel.setText(filename);

		rebuildActions(ext, systemSettings.isDeveloperModeOn());
		revalidate();
		repaint();
	}

	private void rebuildActions(String extension, boolean developerMode) {
		actionsPanel.removeAll();

		JButton searchButton = createActionButton(
				"\u2315  Search Marketplace",
				"Find a plugin for this file type");
		searchButton.addActionListener(e -> pluginMarketplaceService.searchQuickViewPlugins(extension));
		actionsPanel.add(searchButton);
		actionsPanel.add(Box.createVerticalStrut(8));

		if (developerMode) {
			JButton installLocalButton = createActionButton(
					"\u2397  Install from Local File",
					"Select a plugin file to install");
			installLocalButton.addActionListener(e -> {
				JFileChooser chooser = new JFileChooser();
				chooser.setDialogTitle("Select Plugin File");
				int result = chooser.showOpenDialog(SwingUtilities.getWindowAncestor(this));
				if (result == JFileChooser.APPROVE_OPTION) {
					File selected = chooser.getSelectedFile();
					log.info("User selected plugin file: {}", selected.getAbsolutePath());
				}
			});
			actionsPanel.add(installLocalButton);
			actionsPanel.add(Box.createVerticalStrut(8));
		} else {
			JLabel devHint = new JLabel(
					"<html><center>Enable Developer Mode in settings<br>to install local plugins</center></html>");
			devHint.setFont(new Font("JetBrains Mono", Font.ITALIC, 13));
			devHint.setForeground(WARNING_COLOR);
			devHint.setAlignmentX(CENTER_ALIGNMENT);
			devHint.setHorizontalAlignment(SwingConstants.CENTER);
			actionsPanel.add(devHint);
			actionsPanel.add(Box.createVerticalStrut(8));
		}

		JPanel separator = new JPanel();
		separator.setBackground(new Color(0x3C, 0x3F, 0x41));
		separator.setMaximumSize(new Dimension(200, 1));
		separator.setPreferredSize(new Dimension(200, 1));
		separator.setAlignmentX(CENTER_ALIGNMENT);
		actionsPanel.add(separator);
		actionsPanel.add(Box.createVerticalStrut(10));

		JLabel requestLabel = createLinkLabel(
				"Request support for this file type",
				GITHUB_ISSUES_URL + "/new?title=Quick+View+support+for+." + extension);
		actionsPanel.add(requestLabel);
	}

	private JButton createActionButton(String text, String tooltip) {
		JButton btn = new JButton(text);
		btn.setToolTipText(tooltip);
		btn.setFont(new Font("JetBrains Mono", Font.PLAIN, 15));
		btn.setForeground(TEXT_PRIMARY);
		btn.setBackground(BUTTON_BG);
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(true);
		btn.setAlignmentX(CENTER_ALIGNMENT);
		btn.setMaximumSize(new Dimension(300, 38));
		btn.setPreferredSize(new Dimension(300, 38));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) { btn.setBackground(BUTTON_HOVER); }
			@Override public void mouseExited(MouseEvent e)  { btn.setBackground(BUTTON_BG); }
		});
		return btn;
	}

	private JLabel createLinkLabel(String text, String url) {
		JLabel label = new JLabel(text);
		label.setFont(new Font("JetBrains Mono", Font.PLAIN, 14));
		label.setForeground(LINK_COLOR);
		label.setAlignmentX(CENTER_ALIGNMENT);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				try { Desktop.getDesktop().browse(new URI(url)); }
				catch (Exception ex) { log.error("Failed to open URL: {}", url, ex); }
			}
			@Override public void mouseEntered(MouseEvent e) { label.setText("<html><u>" + text + "</u></html>"); }
			@Override public void mouseExited(MouseEvent e)  { label.setText(text); }
		});
		return label;
	}
}
