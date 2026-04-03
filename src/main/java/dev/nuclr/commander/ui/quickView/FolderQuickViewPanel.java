package dev.nuclr.commander.ui.quickView;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.nuclr.commander.common.FileUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class FolderQuickViewPanel extends JPanel {

	private static final int INFO_PANEL_WIDTH = 420;
	private static final DateTimeFormatter DATE_FMT =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

	private JLabel iconLabel;
	private JLabel nameLabel;
	private JLabel subfoldersValue;
	private JLabel filesValue;
	private JLabel sizeValue;
	private JLabel lastModifiedValue;
	private JLabel hiddenValue;
	private JPanel centerPanel;
	private JPanel infoPanel;
	private JPanel separatorPanel;
	private JPanel hiddenRow;

	private volatile Thread scanThread;

	private boolean uiBuilt = false;

	@Inject
	public FolderQuickViewPanel() {
	}

	@Override
	public void updateUI() {
		super.updateUI();
		if (uiBuilt) {
			SwingUtilities.invokeLater(this::applyTheme);
		}
	}

	private void buildUI() {
		setLayout(new BorderLayout());
		setBackground(uiColor("Panel.background", getBackground()));

		centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
		centerPanel.setBackground(uiColor("Panel.background", centerPanel.getBackground()));
		centerPanel.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

		iconLabel = new JLabel("\uD83D\uDCC1", SwingConstants.CENTER);
		iconLabel.setFont(uiFontRelative(Font.PLAIN, 4.0f, 20));
		iconLabel.setForeground(uiColor("Label.disabledForeground", iconLabel.getForeground()));
		iconLabel.setAlignmentX(CENTER_ALIGNMENT);

		nameLabel = new JLabel(" ");
		nameLabel.setFont(uiFontRelative(Font.BOLD, 1.3f, 14));
		nameLabel.setForeground(uiColor("Label.foreground", nameLabel.getForeground()));
		nameLabel.setAlignmentX(CENTER_ALIGNMENT);
		nameLabel.setHorizontalAlignment(SwingConstants.CENTER);

		separatorPanel = new JPanel();
		separatorPanel.setBackground(uiColor("Separator.foreground", separatorPanel.getBackground()));
		separatorPanel.setMaximumSize(new Dimension(INFO_PANEL_WIDTH, 1));
		separatorPanel.setPreferredSize(new Dimension(INFO_PANEL_WIDTH, 1));
		separatorPanel.setAlignmentX(CENTER_ALIGNMENT);

		subfoldersValue  = new JLabel("Scanning\u2026");
		filesValue       = new JLabel("Scanning\u2026");
		sizeValue        = new JLabel("Scanning\u2026");
		lastModifiedValue = new JLabel(" ");
		hiddenValue      = new JLabel("0");

		infoPanel = new JPanel();
		infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
		infoPanel.setBackground(uiColor("Panel.background", infoPanel.getBackground()));
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
		centerPanel.add(separatorPanel);
		centerPanel.add(Box.createVerticalStrut(12));
		centerPanel.add(infoPanel);
		centerPanel.add(Box.createVerticalGlue());

		add(centerPanel, BorderLayout.CENTER);
		uiBuilt = true;
	}

	private JPanel makeRow(String key, JLabel valueLabel) {
		JPanel row = new JPanel(new GridLayout(1, 2, 8, 0));
		row.setBackground(uiColor("Panel.background", row.getBackground()));
		row.setAlignmentX(CENTER_ALIGNMENT);
		row.setMaximumSize(new Dimension(INFO_PANEL_WIDTH, 18));
		row.setPreferredSize(new Dimension(INFO_PANEL_WIDTH, 18));

		JLabel keyLabel = new JLabel(key);
		keyLabel.setFont(uiFontRelative(Font.PLAIN, 1.0f, 11));
		keyLabel.setForeground(uiColor("Label.disabledForeground", keyLabel.getForeground()));

		valueLabel.setFont(uiFontRelative(Font.PLAIN, 1.0f, 11));
		valueLabel.setForeground(uiColor("Label.foreground", valueLabel.getForeground()));

		row.add(keyLabel);
		row.add(valueLabel);
		return row;
	}

	public void show(Path path) {
		if (!uiBuilt) buildUI();
		applyTheme();

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
		long[] lastUiUpdateNanos = {System.nanoTime()};
		final long uiUpdateIntervalNanos = 250_000_000L; // 250 ms

		try {
			Files.walkFileTree(root, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (Thread.currentThread().isInterrupted()) return FileVisitResult.TERMINATE;
					if (!dir.equals(root)) {
						counts[0]++;
						isHiddenCount(dir, counts);
						maybePublishProgress(counts, lastUiUpdateNanos, uiUpdateIntervalNanos);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (Thread.currentThread().isInterrupted()) return FileVisitResult.TERMINATE;
					counts[1]++;
					counts[2] += attrs.size();
					isHiddenCount(file, counts);
					maybePublishProgress(counts, lastUiUpdateNanos, uiUpdateIntervalNanos);
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

		publishProgress(finalSubfolders, finalFiles, finalSize, finalHidden, false);
	}

	private static void isHiddenCount(Path path, long[] counts) {
		try {
			String name = path.getFileName() != null ? path.getFileName().toString() : "";
			if (name.startsWith(".") || Files.isHidden(path)) counts[3]++;
		} catch (IOException ignored) {}
	}

	private void maybePublishProgress(long[] counts, long[] lastUiUpdateNanos, long intervalNanos) {
		long now = System.nanoTime();
		if (now - lastUiUpdateNanos[0] < intervalNanos) {
			return;
		}
		lastUiUpdateNanos[0] = now;
		long subfolders = counts[0];
		long files = counts[1];
		long totalSize = counts[2];
		long hidden = counts[3];
		publishProgress(subfolders, files, totalSize, hidden, true);
	}

	private void publishProgress(long subfolders, long files, long totalSize, long hidden, boolean inProgress) {
		SwingUtilities.invokeLater(() -> {
			subfoldersValue.setText(formatCount(subfolders));
			filesValue.setText(formatCount(files));
			String sizeText = FileUtils.byteCountToDisplaySize(totalSize);
			sizeValue.setText(inProgress ? sizeText + " (scanning…)" : sizeText);
			hiddenValue.setText(String.valueOf(hidden));
			hiddenRow.setVisible(hidden > 0);
			revalidate();
			repaint();
		});
	}

	private static String formatCount(long value) {
		return NumberFormat.getIntegerInstance().format(value);
	}

	private void applyTheme() {
		Color panelBackground = uiColor("Panel.background", getBackground());
		Color primaryText = uiColor("Label.foreground", Color.LIGHT_GRAY);
		Color secondaryText = uiColor("Label.disabledForeground", primaryText);

		setBackground(panelBackground);
		centerPanel.setBackground(panelBackground);
		infoPanel.setBackground(panelBackground);
		separatorPanel.setBackground(uiColor("Separator.foreground", uiColor("Table.gridColor", separatorPanel.getBackground())));

		iconLabel.setFont(uiFontRelative(Font.PLAIN, 4.0f, 20));
		nameLabel.setFont(uiFontRelative(Font.BOLD, 1.3f, 14));
		subfoldersValue.setFont(uiFontRelative(Font.PLAIN, 1.0f, 11));
		filesValue.setFont(uiFontRelative(Font.PLAIN, 1.0f, 11));
		sizeValue.setFont(uiFontRelative(Font.PLAIN, 1.0f, 11));
		lastModifiedValue.setFont(uiFontRelative(Font.PLAIN, 1.0f, 11));
		hiddenValue.setFont(uiFontRelative(Font.PLAIN, 1.0f, 11));

		iconLabel.setForeground(secondaryText);
		nameLabel.setForeground(primaryText);
		subfoldersValue.setForeground(primaryText);
		filesValue.setForeground(primaryText);
		sizeValue.setForeground(primaryText);
		lastModifiedValue.setForeground(primaryText);
		hiddenValue.setForeground(primaryText);

		for (var component : infoPanel.getComponents()) {
			if (component instanceof JPanel row) {
				row.setBackground(panelBackground);
				if (row.getComponentCount() > 0 && row.getComponent(0) instanceof JLabel keyLabel) {
					keyLabel.setFont(uiFontRelative(Font.PLAIN, 1.0f, 11));
					keyLabel.setForeground(secondaryText);
				}
			}
		}
	}

	private static Color uiColor(String key, Color fallback) {
		Color color = UIManager.getColor(key);
		return color != null ? color : fallback;
	}

	private static Font uiFontRelative(int style, float scale, int minSize) {
		Font base = UIManager.getFont("defaultFont");
		if (base == null) {
			return new Font(Font.MONOSPACED, style, minSize);
		}
		int size = Math.max(minSize, Math.round(base.getSize2D() * scale));
		return base.deriveFont(style, (float) size);
	}
}
