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
import javax.swing.UIManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.nuclr.commander.common.FilenameUtils;
import dev.nuclr.commander.service.PluginMarketplaceService;
import dev.nuclr.platform.Settings;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class NoQuickViewAvailablePanel extends JPanel {

	private static final String GITHUB_ISSUES_URL = "https://github.com/nuclrdev/commander/issues";

	@Autowired
	private Settings systemSettings;

	@Autowired
	private PluginMarketplaceService pluginMarketplaceService;

	private JLabel iconLabel;
	private JLabel titleLabel;
	private JLabel extensionLabel;
	private JLabel fileNameLabel;
	private JPanel actionsPanel;
	private JPanel centerPanel;

	private boolean uiBuilt = false;

	private void buildUI() {
		setLayout(new BorderLayout());
		setBackground(uiColor("Panel.background", getBackground()));

		centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		centerPanel.setBackground(uiColor("Panel.background", centerPanel.getBackground()));
		centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

		iconLabel = new JLabel("?", SwingConstants.CENTER);
		iconLabel.setFont(uiFont(Font.PLAIN, 64));
		iconLabel.setForeground(uiColor("Label.disabledForeground", iconLabel.getForeground()));
		iconLabel.setAlignmentX(CENTER_ALIGNMENT);

		titleLabel = new JLabel("No Quick View Available");
		titleLabel.setFont(uiFont(Font.BOLD, 18));
		titleLabel.setForeground(uiColor("Label.foreground", titleLabel.getForeground()));
		titleLabel.setAlignmentX(CENTER_ALIGNMENT);
		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

		extensionLabel = new JLabel(" ");
		extensionLabel.setFont(uiFont(Font.PLAIN, 15));
		extensionLabel.setForeground(uiColor("Label.foreground", extensionLabel.getForeground()));
		extensionLabel.setAlignmentX(CENTER_ALIGNMENT);
		extensionLabel.setHorizontalAlignment(SwingConstants.CENTER);

		fileNameLabel = new JLabel(" ");
		fileNameLabel.setFont(uiFont(Font.PLAIN, 14));
		fileNameLabel.setForeground(uiColor("Label.disabledForeground", fileNameLabel.getForeground()));
		fileNameLabel.setAlignmentX(CENTER_ALIGNMENT);
		fileNameLabel.setHorizontalAlignment(SwingConstants.CENTER);

		actionsPanel = new JPanel();
		actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.Y_AXIS));
		actionsPanel.setBackground(uiColor("Panel.background", actionsPanel.getBackground()));
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
		applyTheme();
		if (path == null) {
			extensionLabel.setText("No item selected");
			fileNameLabel.setText(" ");
			rebuildActions("", systemSettings.isDeveloperModeOn());
			revalidate();
			repaint();
			return;
		}

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
			devHint.setFont(uiFont(Font.ITALIC, 13));
			devHint.setForeground(uiColor("Component.warning.focusedBorderColor", uiColor("Label.foreground", devHint.getForeground())));
			devHint.setAlignmentX(CENTER_ALIGNMENT);
			devHint.setHorizontalAlignment(SwingConstants.CENTER);
			actionsPanel.add(devHint);
			actionsPanel.add(Box.createVerticalStrut(8));
		}

		JPanel separator = new JPanel();
		separator.setBackground(uiColor("Separator.foreground", uiColor("Table.gridColor", separator.getBackground())));
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
		btn.setFont(uiFont(Font.PLAIN, 15));
		Color defaultBg = uiColor("Button.background", btn.getBackground());
		Color hoverBg = uiColor("Button.hoverBackground", defaultBg.brighter());
		btn.setForeground(uiColor("Button.foreground", btn.getForeground()));
		btn.setBackground(defaultBg);
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setContentAreaFilled(true);
		btn.setAlignmentX(CENTER_ALIGNMENT);
		btn.setMaximumSize(new Dimension(300, 38));
		btn.setPreferredSize(new Dimension(300, 38));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.addMouseListener(new MouseAdapter() {
			@Override public void mouseEntered(MouseEvent e) { btn.setBackground(hoverBg); }
			@Override public void mouseExited(MouseEvent e)  { btn.setBackground(defaultBg); }
		});
		return btn;
	}

	private JLabel createLinkLabel(String text, String url) {
		JLabel label = new JLabel(text);
		label.setFont(uiFont(Font.PLAIN, 14));
		label.setForeground(uiColor("Component.linkColor", label.getForeground()));
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

	private void applyTheme() {
		Color panelBackground = uiColor("Panel.background", getBackground());
		setBackground(panelBackground);
		if (centerPanel != null) centerPanel.setBackground(panelBackground);
		if (actionsPanel != null) actionsPanel.setBackground(panelBackground);

		if (iconLabel != null) iconLabel.setForeground(uiColor("Label.disabledForeground", iconLabel.getForeground()));
		if (titleLabel != null) titleLabel.setForeground(uiColor("Label.foreground", titleLabel.getForeground()));
		if (extensionLabel != null) extensionLabel.setForeground(uiColor("Label.foreground", extensionLabel.getForeground()));
		if (fileNameLabel != null) fileNameLabel.setForeground(uiColor("Label.disabledForeground", fileNameLabel.getForeground()));
	}

	private static Color uiColor(String key, Color fallback) {
		Color color = UIManager.getColor(key);
		return color != null ? color : fallback;
	}

	private static Font uiFont(int style, int size) {
		Font base = UIManager.getFont("defaultFont");
		if (base == null) {
			return new Font(Font.MONOSPACED, style, size);
		}
		return base.deriveFont(style, (float) size);
	}
}
