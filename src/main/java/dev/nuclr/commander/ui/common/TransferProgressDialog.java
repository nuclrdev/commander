package dev.nuclr.commander.ui.common;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import dev.nuclr.commander.service.PanelTransferService.TransferProgress;

public final class TransferProgressDialog {

	private final JDialog dialog;
	private final JLabel sourceLabel = new JLabel(" ");
	private final JLabel targetLabel = new JLabel(" ");
	private final JLabel filesLabel = new JLabel("Files: 0 / 0");
	private final JLabel bytesLabel = new JLabel("Bytes: 0 B / 0 B");
	private final JProgressBar fileProgressBar = new JProgressBar();
	private final JProgressBar byteProgressBar = new JProgressBar();
	private final AtomicBoolean cancelRequested = new AtomicBoolean(false);

	public TransferProgressDialog(Component parent, boolean move) {
		dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), move ? "Move" : "Copy");
		dialog.setModal(false);
		dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		dialog.setResizable(false);

		JPanel content = new JPanel(new BorderLayout(0, 12));
		content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JPanel labels = new JPanel();
		labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
		labels.add(new JLabel(move ? "Moving to:" : "Copying to:"));
		labels.add(sourceLabel);
		labels.add(targetLabel);
		content.add(labels, BorderLayout.NORTH);

		JPanel progressPanel = new JPanel();
		progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));
		progressPanel.add(createRow("Files", filesLabel, fileProgressBar));
		progressPanel.add(createRow("Bytes", bytesLabel, byteProgressBar));
		content.add(progressPanel, BorderLayout.CENTER);

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e -> cancelRequested.set(true));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		actions.add(cancelButton);
		content.add(actions, BorderLayout.SOUTH);

		dialog.setContentPane(content);
		dialog.pack();
		dialog.setLocationRelativeTo(parent);
	}

	public void showDialog() {
		if (SwingUtilities.isEventDispatchThread()) {
			dialog.setVisible(true);
		} else {
			SwingUtilities.invokeLater(() -> dialog.setVisible(true));
		}
	}

	public void closeDialog() {
		if (SwingUtilities.isEventDispatchThread()) {
			dialog.dispose();
		} else {
			SwingUtilities.invokeLater(dialog::dispose);
		}
	}

	public boolean isCancelRequested() {
		return cancelRequested.get();
	}

	public void updateProgress(TransferProgress progress) {
		if (progress == null) {
			return;
		}
		SwingUtilities.invokeLater(() -> applyProgress(progress));
	}

	private JPanel createRow(String title, JLabel valueLabel, JProgressBar progressBar) {
		progressBar.setStringPainted(true);

		JPanel panel = new JPanel(new BorderLayout(8, 4));
		panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
		panel.add(new JLabel(title + ":", SwingConstants.LEFT), BorderLayout.NORTH);
		panel.add(progressBar, BorderLayout.CENTER);
		panel.add(valueLabel, BorderLayout.SOUTH);
		return panel;
	}

	private void applyProgress(TransferProgress progress) {
		sourceLabel.setText(ellipsize(progress.currentSource()));
		targetLabel.setText(ellipsize(progress.currentTarget()));

		long totalFiles = Math.max(0L, progress.totalFiles());
		long completedFiles = Math.max(0L, progress.completedFiles());
		filesLabel.setText("Files: " + completedFiles + " / " + totalFiles);
		updateBar(fileProgressBar, completedFiles, totalFiles);

		long totalBytes = Math.max(0L, progress.totalBytes());
		long transferredBytes = Math.max(0L, progress.transferredBytes());
		bytesLabel.setText("Bytes: " + formatBytes(transferredBytes) + " / " + formatBytes(totalBytes));
		updateBar(byteProgressBar, transferredBytes, totalBytes);
	}

	private void updateBar(JProgressBar progressBar, long current, long total) {
		if (total <= 0L) {
			progressBar.setIndeterminate(true);
			progressBar.setString(null);
			return;
		}
		progressBar.setIndeterminate(false);
		progressBar.setMinimum(0);
		progressBar.setMaximum(10_000);
		int value = (int) Math.min(10_000L, (current * 10_000L) / total);
		progressBar.setValue(value);
		progressBar.setString((value / 100) + "%");
	}

	private String ellipsize(Path path) {
		if (path == null) {
			return " ";
		}
		String text = path.toString();
		if (text.length() <= 96) {
			return text;
		}
		return "..." + text.substring(text.length() - 93);
	}

	private String formatBytes(long bytes) {
		if (bytes <= 0L) {
			return "0 B";
		}
		String[] units = {"B", "KB", "MB", "GB", "TB"};
		double value = bytes;
		int unitIndex = 0;
		while (value >= 1024 && unitIndex < units.length - 1) {
			value /= 1024.0;
			unitIndex++;
		}
		return new DecimalFormat(value >= 10 || unitIndex == 0 ? "0" : "0.0").format(value) + " " + units[unitIndex];
	}
}
