package dev.nuclr.commander.ui.quickView;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.springframework.stereotype.Component;

import dev.nuclr.commander.common.FileUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FolderQuickViewPanel extends JPanel {

	private static final Color BG_COLOR      = new Color(0x2B, 0x2B, 0x2B);
	private static final Color TEXT_PRIMARY   = new Color(0xBB, 0xBB, 0xBB);
	private static final Color TEXT_SECONDARY = new Color(0x78, 0x78, 0x78);
	private static final Color TEXT_MUTED     = new Color(0x5A, 0x5A, 0x5A);
	private static final Color BUTTON_BG      = new Color(0x3C, 0x3F, 0x41);

	private static final int KEY_LABEL_WIDTH = 110;
	private static final DateTimeFormatter DATE_FMT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private JLabel nameLabel;
	private JLabel subfoldersValue;
	private JLabel filesValue;
	private JLabel sizeValue;
	private JLabel lastModifiedValue;
	private JLabel hiddenValue;
	private JPanel hiddenRow;

	private volatile Thread scanThread;

	private boolean uiBuilt = false;

	private void buildUI() {
		setLayout(new BorderLayout());
		setBackground(BG_COLOR);

		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		centerPanel.setBackground(BG_COLOR);
		centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

		JLabel iconLabel = new JLabel("\uD83D\uDCC1", SwingConstants.CENTER);
		iconLabel.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 52));
		iconLabel.setForeground(TEXT_MUTED);
		iconLabel.setAlignmentX(CENTER_ALIGNMENT);

		nameLabel = new JLabel(" ");
		nameLabel.setFont(new Font("JetBrains Mono", Font.BOLD, 16));
		nameLabel.setForeground(TEXT_PRIMARY);
		nameLabel.setAlignmentX(CENTER_ALIGNMENT);
		nameLabel.setHorizontalAlignment(SwingConstants.CENTER);

		JPanel separator = new JPanel();
		separator.setBackground(BUTTON_BG);
		separator.setMaximumSize(new Dimension(260, 1));
		separator.setPreferredSize(new Dimension(260, 1));
		separator.setAlignmentX(CENTER_ALIGNMENT);

		subfoldersValue  = new JLabel("Scanning\u2026");
		filesValue       = new JLabel("Scanning\u2026");
		sizeValue        = new JLabel("Scanning\u2026");
		lastModifiedValue = new JLabel(" ");
		hiddenValue      = new JLabel("0");

		JPanel infoPanel = new JPanel();
		infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
		infoPanel.setBackground(BG_COLOR);
		infoPanel.setAlignmentX(CENTER_ALIGNMENT);

		infoPanel.add(makeRow("Subfolders",    subfoldersValue));
		infoPanel.add(makeRow("Files",         filesValue));
		infoPanel.add(makeRow("Total size",    sizeValue));
		infoPanel.add(makeRow("Last modified", lastModifiedValue));
		hiddenRow = makeRow("Hidden", hiddenValue);
		hiddenRow.setVisible(false);
		infoPanel.add(hiddenRow);

		centerPanel.add(Box.createVerticalGlue());
		centerPanel.add(iconLabel);
		centerPanel.add(Box.createVerticalStrut(10));
		centerPanel.add(nameLabel);
		centerPanel.add(Box.createVerticalStrut(12));
		centerPanel.add(separator);
		centerPanel.add(Box.createVerticalStrut(12));
		centerPanel.add(infoPanel);
		centerPanel.add(Box.createVerticalGlue());

		add(centerPanel, BorderLayout.CENTER);
		uiBuilt = true;
	}

	private JPanel makeRow(String key, JLabel valueLabel) {
		JPanel row = new JPanel(new GridLayout(1, 2, 8, 0));
		row.setBackground(BG_COLOR);
		row.setAlignmentX(CENTER_ALIGNMENT);
		row.setMaximumSize(new Dimension(260, 18));
		row.setPreferredSize(new Dimension(260, 18));

		JLabel keyLabel = new JLabel(key);
		keyLabel.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
		keyLabel.setForeground(TEXT_SECONDARY);

		valueLabel.setFont(new Font("JetBrains Mono", Font.PLAIN, 13));
		valueLabel.setForeground(TEXT_PRIMARY);

		row.add(keyLabel);
		row.add(valueLabel);
		return row;
	}

	public void show(Path path) {
		if (!uiBuilt) buildUI();

		stopScan();

		var fn = path.getFileName();
		nameLabel.setText(fn != null ? fn.toString() : path.toString());

		try {
			FileTime lastMod = Files.getLastModifiedTime(path);
			lastModifiedValue.setText(DATE_FMT.format(lastMod.toInstant()));
		} catch (IOException e) {
			lastModifiedValue.setText("N/A");
		}

		subfoldersValue.setText("Scanning\u2026");
		filesValue.setText("Scanning\u2026");
		sizeValue.setText("Scanning\u2026");
		hiddenRow.setVisible(false);

		revalidate();
		repaint();

		scanThread = Thread.ofVirtual().start(() -> scanFolder(path));
	}

	public void stopScan() {
		Thread t = scanThread;
		if (t != null) {
			t.interrupt();
			scanThread = null;
		}
	}

	private void scanFolder(Path root) {
		long[] counts = {0, 0, 0, 0}; // subfolders, files, totalSize, hidden

		try {
			Files.walkFileTree(root, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (Thread.currentThread().isInterrupted()) return FileVisitResult.TERMINATE;
					if (!dir.equals(root)) {
						counts[0]++;
						isHiddenCount(dir, counts);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (Thread.currentThread().isInterrupted()) return FileVisitResult.TERMINATE;
					counts[1]++;
					counts[2] += attrs.size();
					isHiddenCount(file, counts);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					log.debug("Skipping inaccessible path {}: {}", file, exc.getMessage());
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			log.warn("Error scanning folder {}: {}", root, e.getMessage());
			final String msg = "Error: " + e.getMessage();
			SwingUtilities.invokeLater(() -> {
				subfoldersValue.setText(msg);
				filesValue.setText("\u2014");
				sizeValue.setText("\u2014");
			});
			return;
		}

		if (Thread.currentThread().isInterrupted()) return;

		final long finalSubfolders = counts[0];
		final long finalFiles      = counts[1];
		final long finalSize       = counts[2];
		final long finalHidden     = counts[3];

		SwingUtilities.invokeLater(() -> {
			subfoldersValue.setText(String.valueOf(finalSubfolders));
			filesValue.setText(String.valueOf(finalFiles));
			sizeValue.setText(FileUtils.byteCountToDisplaySize(finalSize));
			hiddenValue.setText(String.valueOf(finalHidden));
			hiddenRow.setVisible(finalHidden > 0);
			revalidate();
			repaint();
		});
	}

	private static void isHiddenCount(Path path, long[] counts) {
		try {
			String name = path.getFileName() != null ? path.getFileName().toString() : "";
			if (name.startsWith(".") || Files.isHidden(path)) counts[3]++;
		} catch (IOException ignored) {}
	}
}
