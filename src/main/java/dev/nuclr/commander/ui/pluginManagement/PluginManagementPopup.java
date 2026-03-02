package dev.nuclr.commander.ui.pluginManagement;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.nuclr.commander.service.PluginRegistry;
import dev.nuclr.plugin.PluginInfo;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PluginManagementPopup {

	@Autowired
	private PluginRegistry pluginRegistry;

	public void show(JFrame parent) {
		var dialog = new JDialog(parent, "Plugin Management", true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		JPanel root = new JPanel(new BorderLayout(0, 10));
		root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JPanel listPanel = new JPanel();
		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

		var plugins = pluginRegistry.getLoadedPlugins();
		if (plugins.isEmpty()) {
			var emptyLabel = new JLabel("No plugins loaded.");
			emptyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
			listPanel.add(emptyLabel);
		} else {
			for (var plugin : plugins) {
				listPanel.add(buildPluginCard(plugin.getInfo()));
				listPanel.add(Box.createVerticalStrut(8));
			}
		}

		JScrollPane scrollPane = new JScrollPane(listPanel);
		scrollPane.setBorder(BorderFactory.createEmptyBorder());
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		root.add(scrollPane, BorderLayout.CENTER);

		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		JButton updateAllButton = new JButton("Update all");
		JButton closeButton = new JButton("Close");
		closeButton.addActionListener(e -> dialog.dispose());
		footer.add(updateAllButton);
		footer.add(closeButton);
		root.add(footer, BorderLayout.SOUTH);

		dialog.setContentPane(root);
		dialog.setSize(860, 520);
		dialog.setLocationRelativeTo(parent);
		dialog.setVisible(true);
	}

	private JPanel buildPluginCard(PluginInfo info) {
		String name = info != null && info.getName() != null ? info.getName() : "Unnamed Plugin";
		String version = info != null && info.getVersion() != null ? info.getVersion() : "unknown";
		String author = info != null && info.getAuthor() != null ? info.getAuthor() : "Unknown author";
		String type = info != null && info.getType() != null ? info.getType().toString() : "Unknown type";
		String license = info != null && info.getLicense() != null ? info.getLicense() : "Unknown license";
		String url = info != null && info.getPageUrl() != null && !info.getPageUrl().isBlank()
				? info.getPageUrl()
				: info != null && info.getWebsite() != null && !info.getWebsite().isBlank()
						? info.getWebsite()
						: "No URL provided";
		String description = info != null && info.getDescription() != null
				? info.getDescription()
				: "No description provided.";

		JPanel card = new JPanel(new BorderLayout(12, 0));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createEtchedBorder(),
				BorderFactory.createEmptyBorder(10, 10, 10, 10)));

		JPanel infoPanel = new JPanel();
		infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
		JLabel titleLabel = new JLabel(name + "  v" + version);
		titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize2D() + 2f));
		infoPanel.add(titleLabel);
		infoPanel.add(Box.createVerticalStrut(4));
		String typeHtml = escapeHtml(type);
		if ("Official".equalsIgnoreCase(type)) {
			typeHtml += " <span style='color:#32c766;'>&#10003;</span>";
		}
		infoPanel.add(new JLabel("<html><b>Author:</b> " + escapeHtml(author)
				+ "    <b>Type:</b> " + typeHtml + "</html>"));
		infoPanel.add(Box.createVerticalStrut(4));
		infoPanel.add(createInfoLabel("License:", license));
		infoPanel.add(Box.createVerticalStrut(4));
		infoPanel.add(new JLabel("<html><body style='width:520px'><b>Description:</b> "
				+ escapeHtml(description) + "</body></html>"));
		infoPanel.add(Box.createVerticalStrut(4));
		infoPanel.add(createLinkLabel("URL:", url));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		actions.add(new JButton("Remove"));
		actions.add(new JButton("Update"));

		card.add(infoPanel, BorderLayout.CENTER);
		card.add(actions, BorderLayout.EAST);
		return card;
	}

	private JLabel createInfoLabel(String label, String value) {
		return new JLabel("<html><b>" + escapeHtml(label) + "</b> " + escapeHtml(value) + "</html>");
	}

	private JLabel createLinkLabel(String label, String url) {
		String safeUrl = escapeHtml(url);
		JLabel linkLabel = new JLabel("<html><b>" + escapeHtml(label) + "</b> "
				+ "<span style='color:#4ea1ff;text-decoration:underline;'>" + safeUrl + "</span></html>");

		if (!"No URL provided".equals(url)) {
			linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			linkLabel.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					try {
						Desktop.getDesktop().browse(URI.create(url));
					} catch (Exception ex) {
						log.warn("Failed to open plugin URL {}: {}", url, ex.getMessage());
					}
				}
			});
		}

		return linkLabel;
	}

	private String escapeHtml(String text) {
		return text
				.replace("&", "&amp;")
				.replace("<", "&lt;")
				.replace(">", "&gt;");
	}
}
