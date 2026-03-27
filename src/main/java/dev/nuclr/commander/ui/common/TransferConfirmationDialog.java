package dev.nuclr.commander.ui.common;

import java.awt.Color;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;

import dev.nuclr.commander.service.PanelTransferService;
import dev.nuclr.plugin.PluginPathResource;

public final class TransferConfirmationDialog {

	private TransferConfirmationDialog() {
	}

	public static Result show(Component parent, Model model) {
		if (model == null) {
			return null;
		}

		JTextField destinationField = new JTextField(displayPath(model.initialDestination()), 42);
		JComboBox<ConflictChoice> conflictChoice = new JComboBox<>(ConflictChoice.values());
		conflictChoice.setSelectedItem(ConflictChoice.from(model.initialConflictResolution()));

		JRadioButton accessDefault = new JRadioButton("Default", true);
		JRadioButton accessCopy = new JRadioButton("Copy");
		JRadioButton accessInherit = new JRadioButton("Inherit");
		ButtonGroup accessGroup = new ButtonGroup();
		accessGroup.add(accessDefault);
		accessGroup.add(accessCopy);
		accessGroup.add(accessInherit);
		accessDefault.setEnabled(false);
		accessCopy.setEnabled(false);
		accessInherit.setEnabled(false);

		JCheckBox timestampsCheckBox = new JCheckBox("Preserve all timestamps");
		JCheckBox linksCheckBox = new JCheckBox("Copy contents of symbolic links");
		JCheckBox destinationsCheckBox = new JCheckBox("Process multiple destinations");
		JCheckBox filterCheckBox = new JCheckBox("Use filter");
		timestampsCheckBox.setEnabled(false);
		linksCheckBox.setEnabled(false);
		destinationsCheckBox.setEnabled(false);
		filterCheckBox.setEnabled(false);
		JButton filterButton = new JButton("Filter");
		filterButton.setEnabled(false);

		Color dialogBackground = uiColor("OptionPane.background", uiColor("control", new Color(60, 63, 65)));
		Color foreground = uiColor("Label.foreground", Color.WHITE);
		Color fieldBackground = uiColor("TextField.background", dialogBackground.brighter());
		Color fieldForeground = uiColor("TextField.foreground", foreground);
		Color borderColor = uiColor("Separator.foreground", dialogBackground.brighter());

		JPanel content = new JPanel(new BorderLayout(0, 12));
		content.add(buildDestinationPanel(model.destinationLabel(), destinationField), BorderLayout.NORTH);
		content.add(buildOptionsPanel(
				conflictChoice,
				accessDefault,
				accessCopy,
				accessInherit,
				timestampsCheckBox,
				linksCheckBox,
				destinationsCheckBox,
				filterCheckBox,
				filterButton), BorderLayout.CENTER);
		content.add(buildSummaryPanel(model.sources()), BorderLayout.SOUTH);
		styleDialog(
				content,
				destinationField,
				conflictChoice,
				timestampsCheckBox,
				linksCheckBox,
				destinationsCheckBox,
				filterCheckBox,
				accessDefault,
				accessCopy,
				accessInherit,
				filterButton,
				dialogBackground,
				foreground,
				fieldBackground,
				fieldForeground,
				borderColor);

		Object[] options = {model.confirmButtonLabel(), "Cancel"};
		while (true) {
			JOptionPane optionPane = new JOptionPane(
					content,
					JOptionPane.QUESTION_MESSAGE,
					JOptionPane.OK_CANCEL_OPTION,
					null,
					options,
					options[0]);
			optionPane.setBackground(dialogBackground);
			optionPane.setForeground(foreground);
			optionPane.setOpaque(true);
			JDialog dialog = optionPane.createDialog(parent, model.title());
			dialog.getContentPane().setBackground(dialogBackground);
			dialog.setModal(true);
			dialog.setResizable(true);
			dialog.setLocationRelativeTo(parent);
			dialog.setVisible(true);
			dialog.dispose();

			Object selected = optionPane.getValue();
			if (!model.confirmButtonLabel().equals(selected)) {
				return null;
			}

			try {
				Path destinationDirectory = parseDestination(destinationField.getText(), model.initialDestination());
				return new Result(destinationDirectory, ((ConflictChoice) conflictChoice.getSelectedItem()).resolution);
			} catch (InvalidPathException ex) {
				String message = ex.getInput() == null || ex.getInput().isBlank() ? "Destination path cannot be blank." : "Invalid destination path:\n" + ex.getInput();
				Alerts.showMessageDialog(parent, message, "Invalid Destination", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private static JPanel buildDestinationPanel(String destinationLabel, JTextField destinationField) {
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.add(new JLabel(destinationLabel), BorderLayout.NORTH);
		panel.add(destinationField, BorderLayout.CENTER);
		return panel;
	}

	private static JPanel buildSummaryPanel(List<PluginPathResource> sources) {
		JPanel panel = new JPanel(new BorderLayout(0, 6));
		panel.add(new JLabel(sources.size() == 1 ? "Selected item" : "Selected items"), BorderLayout.NORTH);

		JTextArea summary = new JTextArea(String.join(System.lineSeparator(), summarizeEntries(sources)));
		summary.setEditable(false);
		summary.setLineWrap(false);
		summary.setWrapStyleWord(false);
		summary.setFont(UIManager.getFont("TextArea.font"));
		summary.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JScrollPane scrollPane = new JScrollPane(summary);
		scrollPane.setBorder(BorderFactory.createEtchedBorder());
		panel.add(scrollPane, BorderLayout.CENTER);
		return panel;
	}

	private static JPanel buildOptionsPanel(
			JComboBox<ConflictChoice> conflictChoice,
			JRadioButton accessDefault,
			JRadioButton accessCopy,
			JRadioButton accessInherit,
			JCheckBox timestampsCheckBox,
			JCheckBox linksCheckBox,
			JCheckBox destinationsCheckBox,
			JCheckBox filterCheckBox,
			JButton filterButton) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		JPanel accessPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.gridx = 0;
		accessPanel.add(new JLabel("Access rights:"), gbc);
		gbc.gridx = 1;
		accessPanel.add(accessDefault, gbc);
		gbc.gridx = 2;
		accessPanel.add(accessCopy, gbc);
		gbc.gridx = 3;
		accessPanel.add(accessInherit, gbc);
		panel.add(accessPanel);
		panel.add(Box.createVerticalStrut(8));

		JPanel conflictPanel = new JPanel(new BorderLayout(8, 0));
		conflictPanel.add(new JLabel("Already existing files:"), BorderLayout.WEST);
		conflictPanel.add(conflictChoice, BorderLayout.CENTER);
		panel.add(conflictPanel);
		panel.add(Box.createVerticalStrut(8));

		panel.add(timestampsCheckBox);
		panel.add(linksCheckBox);
		panel.add(destinationsCheckBox);
		panel.add(Box.createVerticalStrut(8));

		JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		filterPanel.add(filterCheckBox);
		filterPanel.add(Box.createHorizontalStrut(8));
		filterPanel.add(filterButton);
		panel.add(filterPanel);
		return panel;
	}

	private static List<String> summarizeEntries(List<PluginPathResource> sources) {
		List<String> lines = new ArrayList<>();
		int limit = Math.min(sources.size(), 10);
		for (int i = 0; i < limit; i++) {
			PluginPathResource source = sources.get(i);
			Path path = source != null ? source.getPath() : null;
			boolean directory = path != null && Files.isDirectory(path);
			String prefix = directory ? "[Folder] " : "[File] ";
			lines.add(prefix + (source != null && source.getName() != null ? source.getName() : path != null ? path.toString() : "unknown"));
		}
		if (sources.size() > limit) {
			lines.add("... and " + (sources.size() - limit) + " more");
		}
		return lines;
	}

	private static String displayPath(Path path) {
		return path == null ? "" : path.toString();
	}

	private static Path parseDestination(String rawPath, Path basePath) {
		String trimmed = rawPath == null ? "" : rawPath.trim();
		if (trimmed.isEmpty()) {
			throw new InvalidPathException(trimmed, "Destination path cannot be blank");
		}
		if (basePath != null && !basePath.getFileSystem().equals(FileSystems.getDefault())) {
			return basePath.getFileSystem().getPath(trimmed).normalize();
		}
		return Path.of(trimmed).normalize();
	}

	public record Model(
			String title,
			String destinationLabel,
			Path initialDestination,
			List<PluginPathResource> sources,
			PanelTransferService.ConflictResolution initialConflictResolution,
			String confirmButtonLabel) {
	}

	public record Result(
			Path destinationDirectory,
			PanelTransferService.ConflictResolution conflictResolution) {
	}

	private enum ConflictChoice {
		ASK("Ask", PanelTransferService.ConflictResolution.ASK),
		OVERWRITE("Overwrite", PanelTransferService.ConflictResolution.OVERWRITE),
		SKIP("Skip", PanelTransferService.ConflictResolution.SKIP),
		RENAME("Rename", PanelTransferService.ConflictResolution.RENAME);

		private final String label;
		private final PanelTransferService.ConflictResolution resolution;

		ConflictChoice(String label, PanelTransferService.ConflictResolution resolution) {
			this.label = label;
			this.resolution = resolution;
		}

		private static ConflictChoice from(PanelTransferService.ConflictResolution resolution) {
			if (resolution == null) {
				return ASK;
			}
			for (ConflictChoice choice : values()) {
				if (choice.resolution == resolution) {
					return choice;
				}
			}
			return ASK;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private static void styleDialog(
			JPanel content,
			JTextField destinationField,
			JComboBox<?> conflictChoice,
			JCheckBox timestampsCheckBox,
			JCheckBox linksCheckBox,
			JCheckBox destinationsCheckBox,
			JCheckBox filterCheckBox,
			JRadioButton accessDefault,
			JRadioButton accessCopy,
			JRadioButton accessInherit,
			JButton filterButton,
			Color dialogBackground,
			Color foreground,
			Color fieldBackground,
			Color fieldForeground,
			Color borderColor) {
		applyContainerColors(content, dialogBackground, foreground);
		content.setOpaque(true);
		content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		destinationField.setBackground(fieldBackground);
		destinationField.setForeground(fieldForeground);
		destinationField.setCaretColor(fieldForeground);
		destinationField.setBorder(BorderFactory.createLineBorder(borderColor));

		conflictChoice.setBackground(fieldBackground);
		conflictChoice.setForeground(fieldForeground);

		timestampsCheckBox.setBackground(dialogBackground);
		timestampsCheckBox.setForeground(foreground);
		linksCheckBox.setBackground(dialogBackground);
		linksCheckBox.setForeground(foreground);
		destinationsCheckBox.setBackground(dialogBackground);
		destinationsCheckBox.setForeground(foreground);
		filterCheckBox.setBackground(dialogBackground);
		filterCheckBox.setForeground(foreground);

		accessDefault.setBackground(dialogBackground);
		accessDefault.setForeground(foreground);
		accessCopy.setBackground(dialogBackground);
		accessCopy.setForeground(foreground);
		accessInherit.setBackground(dialogBackground);
		accessInherit.setForeground(foreground);

		filterButton.setBackground(uiColor("Button.background", fieldBackground));
		filterButton.setForeground(uiColor("Button.foreground", fieldForeground));
		filterButton.setBorder(BorderFactory.createLineBorder(borderColor));
	}

	private static void applyContainerColors(Container container, Color background, Color foreground) {
		container.setBackground(background);
		container.setForeground(foreground);
		for (Component child : container.getComponents()) {
			if (child instanceof JScrollPane scrollPane) {
				scrollPane.setBackground(background);
				scrollPane.getViewport().setBackground(background);
				scrollPane.setForeground(foreground);
				continue;
			}
			if (child instanceof JTextArea textArea) {
				textArea.setBackground(background);
				textArea.setForeground(foreground);
				textArea.setCaretColor(foreground);
				continue;
			}
			child.setBackground(background);
			child.setForeground(foreground);
			if (child instanceof Container nested) {
				applyContainerColors(nested, background, foreground);
			}
		}
	}

	private static Color uiColor(String key, Color fallback) {
		Color color = UIManager.getColor(key);
		return color != null ? color : fallback;
	}
}
