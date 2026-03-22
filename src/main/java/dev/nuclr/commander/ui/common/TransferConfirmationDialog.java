package dev.nuclr.commander.ui.common;

import java.awt.BorderLayout;
import java.awt.Component;
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
import javax.swing.JDialog;
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
		JRadioButton askButton = new JRadioButton("Ask", true);
		JRadioButton overwriteButton = new JRadioButton("Overwrite");
		JRadioButton skipButton = new JRadioButton("Skip");
		JRadioButton renameButton = new JRadioButton("Rename");
		ButtonGroup overwriteGroup = new ButtonGroup();
		overwriteGroup.add(askButton);
		overwriteGroup.add(overwriteButton);
		overwriteGroup.add(skipButton);
		overwriteGroup.add(renameButton);

		JPanel content = new JPanel(new BorderLayout(0, 12));
		content.add(buildDestinationPanel(model.destinationLabel(), destinationField), BorderLayout.NORTH);
		content.add(buildSummaryPanel(model.sources()), BorderLayout.CENTER);
		content.add(buildOverwritePanel(askButton, overwriteButton, skipButton, renameButton), BorderLayout.SOUTH);

		Object[] options = {"OK", "Cancel"};
		while (true) {
			JOptionPane optionPane = new JOptionPane(
					content,
					JOptionPane.QUESTION_MESSAGE,
					JOptionPane.OK_CANCEL_OPTION,
					null,
					options,
					options[0]);
			JDialog dialog = optionPane.createDialog(parent, model.title());
			dialog.setModal(true);
			dialog.setResizable(true);
			dialog.setLocationRelativeTo(parent);
			dialog.setVisible(true);
			dialog.dispose();

			Object selected = optionPane.getValue();
			if (!"OK".equals(selected)) {
				return null;
			}

			try {
				Path destinationDirectory = parseDestination(destinationField.getText(), model.initialDestination());
				return new Result(destinationDirectory, selectedPolicy(askButton, overwriteButton, skipButton, renameButton));
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
		summary.setBackground(UIManager.getColor("Panel.background"));
		summary.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		JScrollPane scrollPane = new JScrollPane(summary);
		scrollPane.setBorder(BorderFactory.createEtchedBorder());
		panel.add(scrollPane, BorderLayout.CENTER);
		return panel;
	}

	private static JPanel buildOverwritePanel(
			JRadioButton askButton,
			JRadioButton overwriteButton,
			JRadioButton skipButton,
			JRadioButton renameButton) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createTitledBorder("When destination already exists"));

		JPanel row = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.anchor = GridBagConstraints.WEST;
		gbc.gridx = 0;
		row.add(askButton, gbc);
		gbc.gridx = 1;
		row.add(overwriteButton, gbc);
		gbc.gridx = 2;
		row.add(skipButton, gbc);
		gbc.gridx = 3;
		row.add(renameButton, gbc);
		panel.add(row);
		panel.add(Box.createVerticalStrut(2));
		panel.add(new JLabel("Choose how conflicts should be handled during archive copy or extraction."));
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

	private static PanelTransferService.ConflictResolution selectedPolicy(
			JRadioButton askButton,
			JRadioButton overwriteButton,
			JRadioButton skipButton,
			JRadioButton renameButton) {
		if (overwriteButton.isSelected()) {
			return PanelTransferService.ConflictResolution.OVERWRITE;
		}
		if (skipButton.isSelected()) {
			return PanelTransferService.ConflictResolution.SKIP;
		}
		if (renameButton.isSelected()) {
			return PanelTransferService.ConflictResolution.RENAME;
		}
		return PanelTransferService.ConflictResolution.ASK;
	}

	public record Model(
			String title,
			String destinationLabel,
			Path initialDestination,
			List<PluginPathResource> sources) {
	}

	public record Result(
			Path destinationDirectory,
			PanelTransferService.ConflictResolution conflictResolution) {
	}
}
