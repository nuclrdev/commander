package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystem;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JScrollBar;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;

import org.springframework.context.ApplicationEventPublisher;

import dev.nuclr.commander.common.FilePanelColors;
import dev.nuclr.commander.common.FilePanelColors.Defaults;
import dev.nuclr.commander.common.FileUtils;
import dev.nuclr.commander.event.FileSelectedEvent;
import dev.nuclr.commander.event.ListViewFileOpen;
import dev.nuclr.commander.event.ShowEditorScreenEvent;
import dev.nuclr.commander.panel.FilePanelProviderRegistry;
import dev.nuclr.commander.vfs.ArchiveMountProviderRegistry;
import dev.nuclr.commander.vfs.MountRegistry;
import dev.nuclr.plugin.mount.ArchiveMountRequest;
import dev.nuclr.plugin.panel.EntryInfo;
import dev.nuclr.plugin.panel.Operation;
import lombok.extern.slf4j.Slf4j;

/**
 * Single file-browser pane for the dual-panel layout.
 *
 * <p>All filesystem access goes through NIO.2 {@link Path} and
 * {@link Files}. There is no {@code java.io.File} usage. The panel
 * is backend-agnostic: it works identically against local disks,
 * ZIP archives, and any future {@link dev.nuclr.commander.vfs.MountProvider}
 * contributed by a plugin.
 *
 * <h3>Navigation model</h3>
 * <ul>
 *   <li>{@link #navigateTo(Path)} — public, called by external triggers
 *       (drive switcher, bookmarks). Saves and restores per-root history.
 *   <li>{@code enterPath(Path, Path)} — private, used internally for
 *       Enter/double-click navigation. No history manipulation.
 * </ul>
 *
 * <h3>Threading</h3>
 * Directory listing runs on a virtual thread so the EDT is never blocked.
 * Model updates are marshalled back to the EDT via {@link SwingUtilities#invokeLater}.
 */
@Slf4j
public class FilePanel extends JPanel {

	private final ApplicationEventPublisher eventPublisher;
	private final MountRegistry mountRegistry;
	private final ArchiveMountProviderRegistry archiveMountProviderRegistry;
	private final FilePanelProviderRegistry providerRegistry;
	private final FilePanelColors colors;


	private final JTable table;
	private final JScrollPane tableScrollPane;
	private final FileTableModel model;
	private final JLabel topPathTextLabel;
	private final JLabel bottomFileInfoTextLabel;
	private String fullTopPathText = " ";

	/**
	 * Per filesystem-root: the last directory visited on that root.
	 * Keyed by the root {@link Path} (e.g. {@code C:\} on Windows, {@code /} on Linux).
	 * Used to restore position when switching drives.
	 */
	private final Map<Path, Path> lastPathPerRoot = new HashMap<>();
	private final Map<FileSystem, NestedArchiveMount> nestedArchiveMounts = new HashMap<>();
	private final Map<FileSystem, Path> mountedArchivePaths = new HashMap<>();
	private final Map<Path, Path> extractedArchiveRoots = new HashMap<>();

	/** Currently displayed directory. Set on the EDT after a listing completes. */
	private Path currentPath;

	/**
	 * When navigating up to a parent, this is the child directory that should
	 * be highlighted after the parent listing loads.
	 */
	private volatile Path selectAfterLoad;

	/**
	 * Row index to select after a refresh when no specific path is targeted
	 * (e.g. after a deletion to stay at the same cursor position).
	 * Reset to -1 after use.
	 */
	private volatile int selectRowAfterLoad = -1;

	// ── Search popup ─────────────────────────────────────────────────────────

	private StringBuilder searchQuery;
	private Popup searchPopup;
	private JLabel searchLabel;

	// ── Construction ─────────────────────────────────────────────────────────

	public FilePanel(
			ApplicationEventPublisher eventPublisher,
			MountRegistry mountRegistry,
			ArchiveMountProviderRegistry archiveMountProviderRegistry,
			FilePanelProviderRegistry providerRegistry,
			FilePanelColors colors) {

		this.eventPublisher = eventPublisher;
		this.mountRegistry = mountRegistry;
		this.archiveMountProviderRegistry = archiveMountProviderRegistry;
		this.providerRegistry = providerRegistry;
		this.colors = colors;

		setLayout(new BorderLayout());

		// ── Table ───────────────────────────────────────────────────────────
			model = new FileTableModel();
			table = new JTable(model);
			table.setFillsViewportHeight(true);
			table.getTableHeader().setReorderingAllowed(false);
			table.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			table.setRowHeight(table.getRowHeight() + 4);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
		table.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		table.setShowVerticalLines(true);

		// Fixed-width columns for Size / Date / Time
		var cm = table.getColumnModel();
		cm.getColumn(1).setPreferredWidth(80);
		cm.getColumn(1).setMaxWidth(100);
		cm.getColumn(2).setPreferredWidth(80);
		cm.getColumn(2).setMaxWidth(100);
		cm.getColumn(3).setPreferredWidth(60);
		cm.getColumn(3).setMaxWidth(80);

		// Renderer: bold directories; colored files by type.
		// Priority: executable > archive > image > audio > video > pdf > document > default.
		// We always set an explicit foreground when not selected — otherwise
		// DefaultTableCellRenderer caches it in `unselectedForeground` and
		// bleeds the last custom color into every subsequent unselected row.
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			@Override
			public java.awt.Component getTableCellRendererComponent(
					JTable tbl, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				var comp = super.getTableCellRendererComponent(
						tbl, value, isSelected, hasFocus, row, column);
				int modelRow = tbl.convertRowIndexToModel(row);
				var entry = model.getEntryAt(modelRow);
				comp.setFont(entry.directory()
						? comp.getFont().deriveFont(Font.BOLD)
						: comp.getFont().deriveFont(Font.PLAIN));
				if (!isSelected) {
					java.awt.Color fg;
					if (extraFlag(entry, "system")) {
						fg = colors.systemAwtColor();
					} else if (extraFlag(entry, "hidden")) {
						fg = colors.hiddenAwtColor();
					} else if (entry.executable() && !entry.directory()) {
						fg = colors.executableAwtColor();
					} else if (entry.archive()) {
						fg = colors.archiveAwtColor();
					} else if (!entry.directory() && colors.isImage(entry.path())) {
						fg = colors.imageAwtColor();
					} else if (!entry.directory() && colors.isAudio(entry.path())) {
						fg = colors.audioAwtColor();
					} else if (!entry.directory() && colors.isVideo(entry.path())) {
						fg = colors.videoAwtColor();
					} else if (!entry.directory() && colors.isPdf(entry.path())) {
						fg = colors.pdfAwtColor();
					} else if (!entry.directory() && colors.isDocument(entry.path())) {
						fg = colors.documentAwtColor();
					} else {
						fg = tbl.getForeground();
					}
					comp.setForeground(fg);
				}
				return comp;
			}
		});

		tableScrollPane = new JScrollPane(table);
		add(tableScrollPane, BorderLayout.CENTER);

		// ── Path label (top) ────────────────────────────────────────────────
		topPathTextLabel = new JLabel(" ");
		topPathTextLabel.setHorizontalAlignment(JLabel.CENTER);
		topPathTextLabel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				updateTopPathLabel();
			}
		});
		topPathTextLabel.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (!SwingUtilities.isLeftMouseButton(e)) {
					return;
				}
				ChangeDrivePopup.show(FilePanel.this, topPathTextLabel, providerRegistry);
			}
		});
		add(topPathTextLabel, BorderLayout.NORTH);

		// ── Status bar (bottom) ─────────────────────────────────────────────
		bottomFileInfoTextLabel = new JLabel(" ");
		bottomFileInfoTextLabel.setHorizontalAlignment(JLabel.LEFT);
		bottomFileInfoTextLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		add(bottomFileInfoTextLabel, BorderLayout.SOUTH);

		// ── Selection listener ───────────────────────────────────────────────
		table.getSelectionModel().addListSelectionListener(e -> {
			if (e.getValueIsAdjusting()) return;
			int selectedRow = table.getSelectedRow();
			if (selectedRow >= 0) {
				int modelRow = table.convertRowIndexToModel(selectedRow);
				var entry = model.getEntryAt(modelRow);
				if (!entry.isParentEntry()) {
					bottomFileInfoTextLabel.setText(
							entry.displayName() + " | " + FileUtils.byteCountToDisplaySize(entry.size()));
					eventPublisher.publishEvent(new FileSelectedEvent(this, entry.path()));
				} else {
					bottomFileInfoTextLabel.setText(" ");
				}
			} else {
				bottomFileInfoTextLabel.setText(" ");
			}
		});

		// ── Key bindings ─────────────────────────────────────────────────────

		// Enter — open selected entry
		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openRow");
		table.getActionMap().put("openRow", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int row = table.getSelectedRow();
				if (row >= 0) onRowActivated(table.convertRowIndexToModel(row));
			}
		});

		// Shift+Enter — open with system application / file explorer
		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), "openWithSystem");
		table.getActionMap().put("openWithSystem", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				openSelectedWithSystemApplication();
			}
		});

		// F4 — open in editor
		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), "editFile");
		table.getActionMap().put("editFile", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (handleProviderFunctionKey(4, false)) {
					return;
				}
				int row = table.getSelectedRow();
				if (row >= 0) {
					var entry = model.getEntryAt(table.convertRowIndexToModel(row));
					if (!entry.directory() && !entry.isParentEntry()) {
						eventPublisher.publishEvent(new ShowEditorScreenEvent(this, entry.path()));
					}
				}
			}
		});

		// F7 — create new directory
		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), "newFolder");
		table.getActionMap().put("newFolder", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				createNewFolder();
			}
		});

		// F8 — delete selected entry
		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0), "deleteEntry");
		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteEntry");
		table.getActionMap().put("deleteEntry", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				deleteSelected();
			}
		});

		// Left / Right — page up / page down
		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "scrollUpChangeSelection");
		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "scrollDownChangeSelection");

		// Mouse double-click
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
					int viewRow = table.rowAtPoint(e.getPoint());
					if (viewRow >= 0) onRowActivated(table.convertRowIndexToModel(viewRow));
				}
			}
		});

		// Alt+<char> — incremental search
		table.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ALT || e.getKeyCode() == KeyEvent.VK_META) return;
				if (!e.isAltDown() && !e.isMetaDown()) return;

				if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
					if (searchQuery != null && searchQuery.length() > 0) {
						searchQuery.deleteCharAt(searchQuery.length() - 1);
						if (searchQuery.length() == 0) {
							hideSearchPopup();
						} else {
							updateSearchPopup();
							selectFirstMatch(searchQuery.toString());
						}
					}
					e.consume();
					return;
				}

				char c = (char) e.getKeyCode();
				if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == ' ') {
					if (searchQuery == null) searchQuery = new StringBuilder();
					searchQuery.append(Character.toLowerCase(c));
					showSearchPopup();
					selectFirstMatch(searchQuery.toString());
					e.consume();
				}
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ALT || e.getKeyCode() == KeyEvent.VK_META) {
					hideSearchPopup();
				}
			}
		});

		// ── Initial navigation ───────────────────────────────────────────────
		applyThemeColors();
		var roots = providerRegistry.listAllRoots();
		if (!roots.isEmpty()) {
			navigateTo(roots.get(0).path());
		}
	}

	@Override
	public void updateUI() {
		super.updateUI();
		if (table != null) {
			applyThemeColors();
		}
	}

	// ── Public API ───────────────────────────────────────────────────────────

	/**
	 * Navigates to the given directory, saving the current location in the
	 * per-root history and restoring any previously saved location for the
	 * target root (used when switching drives).
	 *
	 * <p>Safe to call from any thread; listing is done off the EDT.
	 */
	public void navigateTo(Path directory) {
		// Save current position under its root
		if (currentPath != null) {
			Path currentRoot = currentPath.getRoot();
			if (currentRoot != null) {
				lastPathPerRoot.put(currentRoot, currentPath);
			}
		}

		// Restore saved position if we're switching to a different root
		Path targetRoot = directory.getRoot();
		if (targetRoot != null && currentPath != null
				&& !targetRoot.equals(currentPath.getRoot())) {
			Path saved = lastPathPerRoot.get(targetRoot);
			if (saved != null && Files.isDirectory(saved)) {
				directory = saved;
			}
		}

		enterPath(directory, null);
	}

	/** Returns the directory currently displayed by this panel. */
	public Path getCurrentPath() {
		return currentPath;
	}

	/**
	 * Returns the root {@link Path} of the current directory's filesystem
	 * (e.g. {@code C:\} on Windows, {@code /} on Linux).
	 */
	public Path getCurrentRoot() {
		return currentPath != null ? currentPath.getRoot() : null;
	}

	/**
	 * Returns the {@link Path} of the currently selected entry,
	 * or {@code null} if nothing is selected or the ".." row is selected.
	 */
	public Path getSelectedPath() {
		int row = table.getSelectedRow();
		if (row < 0) return null;
		var entry = model.getEntryAt(table.convertRowIndexToModel(row));
		return entry.isParentEntry() ? null : entry.path();
	}

	public List<Path> getSelectedPaths() {
		int[] rows = table.getSelectedRows();
		var paths = new ArrayList<Path>();
		for (int row : rows) {
			var entry = model.getEntryAt(table.convertRowIndexToModel(row));
			if (!entry.isParentEntry()) {
				paths.add(entry.path());
			}
		}
		return paths;
	}

	/** Requests keyboard focus on the file table. */
	public void focusFileTable() {
		table.requestFocusInWindow();
	}

	public void refresh() {
		if (currentPath != null) {
			enterPath(currentPath, null);
		}
	}

	public boolean canAcceptCopies() {
		if (currentPath == null) {
			return false;
		}
		var capabilities = mountRegistry.capabilitiesFor(currentPath);
		return capabilities.supports(Operation.COPY)
				|| capabilities.supports(Operation.WRITE)
				|| capabilities.supports(Operation.CREATE_DIRECTORY)
				|| capabilities.supports(Operation.DELETE);
	}

	public void showTransientMessage(String message) {
		bottomFileInfoTextLabel.setText(message);
		Timer timer = new Timer(1800, e -> bottomFileInfoTextLabel.setText(" "));
		timer.setRepeats(false);
		timer.start();
	}

	private void openSelectedWithSystemApplication() {
		int viewRow = table.getSelectedRow();
		if (viewRow < 0) {
			return;
		}

		var entry = model.getEntryAt(table.convertRowIndexToModel(viewRow));
		Path path;
		if (entry.isParentEntry()) {
			if (currentPath == null) {
				return;
			}
			path = currentPath;
		} else {
			path = entry.path();
		}

		if (!path.getFileSystem().equals(FileSystems.getDefault())) {
			showTransientMessage("Cannot open non-local path in system application");
			return;
		}

		eventPublisher.publishEvent(new ListViewFileOpen(this, path));
	}

	private void applyThemeColors() {
		java.awt.Color panelBg = uiColor("Panel.background", getBackground());
		java.awt.Color tableBg = uiColor("Table.background", table.getBackground());
		java.awt.Color tableFg = uiColor("Table.foreground", table.getForeground());
		java.awt.Color viewportBg = uiColor("Viewport.background", tableBg);
		java.awt.Color scrollBg = uiColor("ScrollPane.background", panelBg);
		java.awt.Color scrollTrack = uiColor("ScrollBar.track", viewportBg);
		java.awt.Color scrollThumb = uiColor("ScrollBar.thumb", uiColor("TableHeader.background", tableBg));
		java.awt.Color scrollButton = uiColor("ScrollBar.background", scrollBg);

		setBackground(panelBg);
		table.setBackground(tableBg);
		table.setForeground(tableFg);
		table.setGridColor(uiColor("Table.gridColor", table.getGridColor()));
		table.setSelectionBackground(uiColor("Table.selectionBackground", table.getSelectionBackground()));
		table.setSelectionForeground(uiColor("Table.selectionForeground", table.getSelectionForeground()));

		tableScrollPane.setBackground(scrollBg);
		tableScrollPane.setForeground(tableFg);
		tableScrollPane.getViewport().setBackground(viewportBg);
		if (tableScrollPane.getColumnHeader() != null) {
			tableScrollPane.getColumnHeader().setBackground(uiColor("TableHeader.background", viewportBg));
		}
		if (tableScrollPane.getCorner(JScrollPane.UPPER_RIGHT_CORNER) != null) {
			tableScrollPane.getCorner(JScrollPane.UPPER_RIGHT_CORNER)
					.setBackground(uiColor("TableHeader.background", viewportBg));
		}

		applyScrollBarTheme(tableScrollPane.getVerticalScrollBar(), scrollTrack, scrollThumb, scrollButton);
		applyScrollBarTheme(tableScrollPane.getHorizontalScrollBar(), scrollTrack, scrollThumb, scrollButton);

		topPathTextLabel.setBackground(panelBg);
		topPathTextLabel.setForeground(uiColor("Label.foreground", topPathTextLabel.getForeground()));
		bottomFileInfoTextLabel.setBackground(panelBg);
		bottomFileInfoTextLabel.setForeground(uiColor("Label.foreground", bottomFileInfoTextLabel.getForeground()));
	}

	private static void applyScrollBarTheme(
			JScrollBar bar,
			java.awt.Color track,
			java.awt.Color thumb,
			java.awt.Color button) {
		if (bar == null) {
			return;
		}
		bar.setBackground(track);
		bar.setForeground(thumb);
		for (Component c : bar.getComponents()) {
			c.setBackground(button);
			c.setForeground(thumb);
		}
		applyFlatScrollBarStyle(bar, track, thumb, button);
		bar.repaint();
	}

	private static void applyFlatScrollBarStyle(
			JScrollBar bar,
			java.awt.Color track,
			java.awt.Color thumb,
			java.awt.Color button) {
		String style = "track:" + toHex(track) + ";"
				+ "thumb:" + toHex(thumb) + ";"
				+ "hoverThumbColor:" + toHex(thumb.brighter()) + ";"
				+ "pressedThumbColor:" + toHex(thumb.darker()) + ";"
				+ "hoverTrackColor:" + toHex(track.brighter()) + ";"
				+ "pressedTrackColor:" + toHex(track.darker()) + ";"
				+ "background:" + toHex(button) + ";";
		bar.putClientProperty("JComponent.style", style);
		bar.putClientProperty("FlatLaf.style", style);
	}

	private static String toHex(java.awt.Color c) {
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	private static java.awt.Color uiColor(String key, java.awt.Color fallback) {
		java.awt.Color c = UIManager.getColor(key);
		return c != null ? c : fallback;
	}

	// ── Internal navigation ───────────────────────────────────────────────────

	/**
	 * Activates the entry at {@code modelRow} (Enter / double-click).
	 * <ul>
	 *   <li>".." → navigate to parent, select the child we came from
	 *   <li>directory → enter it
	 *   <li>file → publish {@link ListViewFileOpen} (open with default program)
	 *   <li>.zip file → mount as ZIP and enter
	 * </ul>
	 */
	protected void onRowActivated(int modelRow) {
		var entry = model.getEntryAt(modelRow);

		if (entry.isParentEntry()) {
			Path extractedArchiveSource = extractedArchiveRoots.get(currentPath.normalize());
			if (extractedArchiveSource != null) {
				Path archiveDir = extractedArchiveSource.getParent();
				if (archiveDir == null) {
					archiveDir = extractedArchiveSource.getFileSystem().getPath("/");
				}
				enterPath(archiveDir, extractedArchiveSource);
				return;
			}

			Path parent = currentPath.getParent();
			if (parent != null && !parent.equals(currentPath)) {
				enterPath(parent, currentPath);
			} else {
				var nestedMount = nestedArchiveMounts.get(currentPath.getFileSystem());
				if (nestedMount != null) {
					Path archivePath = nestedMount.archivePath();
					Path archiveDir = archivePath.getParent();
					if (archiveDir == null) {
						archiveDir = archivePath.getFileSystem().getPath("/");
					}
					enterPath(archiveDir, archivePath);
					return;
				}

				// At the root of a non-local FS (e.g. ZIP archive) — navigate back
				// to the local directory that contains the archive and re-select it.
				// currentPath.toUri() for a ZIP root gives jar:file:///path/to/archive.zip!/
				Path archivePath = mountedArchivePaths.get(currentPath.getFileSystem());
				if (archivePath != null) {
					Path archiveDir = archivePath.getParent();
					if (archiveDir != null) {
						enterPath(archiveDir, archivePath);
						return;
					}
					log.warn("Cannot navigate out of archive: no parent directory for {}", archivePath);
					return;
				}
				log.warn("Cannot navigate out of archive: mount source is unknown for {}", currentPath);
			}
			return;
		}

		if (entry.directory()) {
			Path targetPath = resolveProviderEnterPath(entry.path());
			if (targetPath == null) {
				return;
			}
			if (!Files.isReadable(targetPath)) {
				if (tryNavigateViaRealPath(targetPath)) {
					return;
				}
				showTransientMessage("Access denied: " + entry.displayName());
				return;
			}
			log.info("Enter directory: {}", targetPath);
			enterPath(targetPath, null);
		} else {
			if (tryEnterArchive(entry.path())) {
				return;
			}

			log.info("Open file: {}", entry.path());
			eventPublisher.publishEvent(new ListViewFileOpen(this, entry.path()));
		}
	}

	/**
	 * Internal directory navigation — no root-history side effects.
	 * Updates the path label immediately (EDT), then loads the directory
	 * listing on a virtual thread and applies the result on the EDT.
	 *
	 * @param dir         directory to navigate into
	 * @param selectAfter if non-null, this path will be highlighted after loading
	 *                    (used when navigating up to select the child we came from)
	 */
	private void enterPath(Path dir, Path selectAfter) {
		this.selectAfterLoad = selectAfter;
		final Path previousPath = currentPath;

		// Update path label immediately for snappy feedback
		SwingUtilities.invokeLater(() -> setTopPathText(buildDisplayPath(dir)));

		Thread.ofVirtual().start(() -> {
			try {
				var entries = listDirectory(dir);
				final Path target = this.selectAfterLoad;
				this.selectAfterLoad = null;
				final int targetRow = this.selectRowAfterLoad;
				this.selectRowAfterLoad = -1;

				SwingUtilities.invokeLater(() -> {
					model.setEntries(entries);
					currentPath = dir;

					if (target != null) {
						selectInTable(target);
					} else if (targetRow >= 0 && model.getRowCount() > 0) {
						int row = Math.min(targetRow, model.getRowCount() - 1);
						table.setRowSelectionInterval(row, row);
						table.scrollRectToVisible(table.getCellRect(row, 0, true));
					} else if (model.getRowCount() > 0) {
						table.setRowSelectionInterval(0, 0);
					}
				});
			} catch (AccessDeniedException ex) {
				log.warn("Access denied: {}", dir);
				if (tryNavigateViaRealPath(dir)) {
					return;
				}
				SwingUtilities.invokeLater(() -> {
					if (previousPath != null) {
						setTopPathText(buildDisplayPath(previousPath));
					}
					bottomFileInfoTextLabel.setText("Access denied: " + dir);
				});
			} catch (IOException ex) {
				log.error("Cannot list directory: {}", dir, ex);
				SwingUtilities.invokeLater(() -> {
					if (previousPath != null) {
						setTopPathText(buildDisplayPath(previousPath));
					}
					bottomFileInfoTextLabel.setText("Error: " + ex.getMessage());
				});
			}
		});
	}

	private boolean tryNavigateViaRealPath(Path deniedPath) {
		if (!isWindows()) {
			return false;
		}

		try {
			Path resolved = deniedPath.toRealPath();
			if (resolved == null || resolved.equals(deniedPath)) {
				return false;
			}
			if (!Files.isDirectory(resolved) || !Files.isReadable(resolved)) {
				return false;
			}

			log.info("Following reparse target: {} -> {}", deniedPath, resolved);
			enterPath(resolved, null);
			return true;
		} catch (IOException ex) {
			log.debug("Cannot resolve reparse target for {}: {}", deniedPath, ex.getMessage());
			return false;
		}
	}

	private static boolean isWindows() {
		String os = System.getProperty("os.name", "");
		return os.toLowerCase().startsWith("win");
	}

	private boolean tryEnterArchive(Path archivePath) {
		var archiveProvider = archiveMountProviderRegistry.findFor(archivePath);
		if (archiveProvider.isEmpty()) {
			return false;
		}

		try {
			Path mountSource = archivePath;
			if (!archivePath.getFileSystem().equals(FileSystems.getDefault())) {
				mountSource = materializeNestedArchive(archivePath);
			}

			Path archiveRoot;
			try {
				archiveRoot = archiveProvider.get().mountAndGetRoot(mountSource, new ArchiveMountRequest(null));
			} catch (IOException firstError) {
				if (!isPasswordRequiredError(firstError)) {
					throw firstError;
				}
				String password = promptArchivePassword(archivePath);
				if (password == null) {
					return true;
				}
				archiveRoot = archiveProvider.get().mountAndGetRoot(
						mountSource,
						new ArchiveMountRequest(password));
			}
			mountRegistry.registerMount(archiveRoot.getFileSystem(), archiveProvider.get().capabilities());
			mountedArchivePaths.put(archiveRoot.getFileSystem(), archivePath);
			if (archiveRoot.getFileSystem().equals(FileSystems.getDefault())) {
				extractedArchiveRoots.put(archiveRoot.normalize(), archivePath);
			}

			if (!mountSource.equals(archivePath)) {
				nestedArchiveMounts.put(archiveRoot.getFileSystem(), new NestedArchiveMount(archivePath, mountSource));
			}

			log.info("Entering archive: {}", archivePath);
			enterPath(archiveRoot, null);
			return true;
		} catch (IOException ex) {
			log.warn("Cannot mount archive {}: {}", archivePath, ex.getMessage());
			return false;
		}
	}

	private Path materializeNestedArchive(Path archivePath) throws IOException {
		String filename = archivePath.getFileName() != null
				? archivePath.getFileName().toString()
				: "archive.zip";
		String suffix = ".zip";
		int dot = filename.lastIndexOf('.');
		if (dot >= 0 && dot < filename.length() - 1) {
			suffix = filename.substring(dot);
		}

		Path tempArchive = Files.createTempFile("nuclr-nested-archive-", suffix);
		Files.copy(archivePath, tempArchive, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		tempArchive.toFile().deleteOnExit();
		return tempArchive;
	}

	private record NestedArchiveMount(Path archivePath, Path tempArchivePath) {
	}

	private void setTopPathText(String text) {
		fullTopPathText = text != null ? text : " ";
		updateTopPathLabel();
	}

	private void updateTopPathLabel() {
		String text = truncateTopPath(fullTopPathText);
		topPathTextLabel.setText(text);
		topPathTextLabel.setToolTipText(fullTopPathText);
	}

	private String truncateTopPath(String text) {
		if (text == null || text.isEmpty()) {
			return " ";
		}

		if (text.length() <= 32) {
			return text;
		}

		return "..." + text.substring(text.length() - 32);
	}

	private String buildDisplayPath(Path path) {
		if (path.getFileSystem().equals(FileSystems.getDefault())) {
			return path.toAbsolutePath().toString();
		}

		var nestedMount = nestedArchiveMounts.get(path.getFileSystem());
		if (nestedMount != null) {
			return buildArchiveDisplayPath(nestedMount.archivePath(), path);
		}

		try {
			URI uri = path.toUri();
			String ssp = uri.getSchemeSpecificPart();
			int bang = ssp != null ? ssp.indexOf("!/") : -1;
			if (bang >= 0) {
				try {
					Path archivePath = Path.of(URI.create(ssp.substring(0, bang)));
					return buildArchiveDisplayPath(archivePath, path);
				} catch (Exception ex) {
					log.debug("Cannot resolve archive display path for {}: {}", path, ex.getMessage());
				}
			}
		} catch (UnsupportedOperationException ex) {
			// Some provider-specific paths (e.g. Apache SSHD SFTP) do not support
			// toUri()/toFile conversion. Fall back to provider-native path text.
		}

		return path.toString();
	}

	private String buildArchiveDisplayPath(Path archivePath, Path mountedPath) {
		String base = buildDisplayPath(archivePath);
		char separator = base.indexOf('\\') >= 0 ? '\\' : '/';
		String entryPath = mountedPath.toString()
				.replace('\\', separator)
				.replace('/', separator);
		if (entryPath.isEmpty()) {
			entryPath = String.valueOf(separator);
		}
		if (entryPath.charAt(0) != separator) {
			entryPath = separator + entryPath;
		}
		return base + entryPath;
	}

	/**
	 * Reads the contents of {@code dir} using a {@link DirectoryStream},
	 * maps each child to an {@link EntryInfo}, and returns a sorted list:
	 * ".." first, then directories alphabetically, then files alphabetically.
	 *
	 * <p>When the mounted filesystem advertises {@link dev.nuclr.commander.vfs.Capabilities#posixPermissions()},
	 * POSIX owner and permission strings are populated; otherwise they remain {@code null}.
	 */
	private List<EntryInfo> listDirectory(Path dir) throws IOException {
		var entries = new ArrayList<EntryInfo>();

		// ".." entry — always shown inside non-local filesystems (e.g. ZIP archives)
		// so the user can navigate back; on local FS only when a parent exists.
		if (dir.getParent() != null || !dir.getFileSystem().equals(FileSystems.getDefault())) {
			entries.add(EntryInfo.parentEntry(dir));
		}

		// Query capabilities once for the whole listing (O(1) after Fix 1)
		boolean hasPosix = mountRegistry.capabilitiesFor(dir).posixPermissions();

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
			for (Path child : stream) {
				try {
					EntryInfo info;
					if (hasPosix) {
						var attrs = Files.readAttributes(child, PosixFileAttributes.class);
						boolean isDir = attrs.isDirectory();
						boolean exec  = !isDir && attrs.permissions().contains(PosixFilePermission.OWNER_EXECUTE);
						boolean arch  = !isDir && colors.isArchive(child);
						boolean hidden = isHiddenEntry(child);
						boolean system = isSystemEntry(child);
						info = new EntryInfo(
								child,
								child.getFileName().toString(),
								isDir,
								exec,
								arch,
								isDir ? 0L : attrs.size(),
								attrs.lastModifiedTime(),
								attrs.owner().getName(),
								PosixFilePermissions.toString(attrs.permissions()),
								Map.of("hidden", hidden, "system", system));
					} else {
						var attrs = Files.readAttributes(child, BasicFileAttributes.class);
						boolean isDir = attrs.isDirectory();
						boolean exec  = !isDir && colors.isWindowsExecutable(child);
						boolean arch  = !isDir && colors.isArchive(child);
						boolean hidden = isHiddenEntry(child);
						boolean system = isSystemEntry(child);
						info = new EntryInfo(
								child,
								child.getFileName().toString(),
								isDir,
								exec,
								arch,
								isDir ? 0L : attrs.size(),
								attrs.lastModifiedTime(),
								null, null,
								Map.of("hidden", hidden, "system", system));
					}
					entries.add(info);
				} catch (IOException ex) {
					log.warn("Cannot read attributes for {}: {}", child, ex.getMessage());
				}
			}
		}

		// Sort: ".." first, then dirs alphabetically, then files alphabetically
		entries.sort((a, b) -> {
			if (a.isParentEntry()) return -1;
			if (b.isParentEntry()) return 1;
			if (a.directory() != b.directory()) return a.directory() ? -1 : 1;
			return a.displayName().compareToIgnoreCase(b.displayName());
		});

		return entries;
	}

	/** Finds {@code target} in the current model and selects its row. */
	private void selectInTable(Path target) {
		for (int i = 0; i < model.getRowCount(); i++) {
			var entry = model.getEntryAt(i);
			if (!entry.isParentEntry() && entry.path().equals(target)) {
				table.setRowSelectionInterval(i, i);
				table.scrollRectToVisible(table.getCellRect(i, 0, true));
				return;
			}
		}
	}

	// ── Search popup ─────────────────────────────────────────────────────────

	private void showSearchPopup() {
		if (searchPopup != null) searchPopup.hide();
		if (searchLabel == null) {
			searchLabel = new JLabel();
			searchLabel.setBorder(BorderFactory.createCompoundBorder(
					BorderFactory.createLineBorder(searchLabel.getForeground()),
					BorderFactory.createEmptyBorder(4, 8, 4, 8)));
			searchLabel.setOpaque(true);
		}
		searchLabel.setText("Search: " + searchQuery);
		Point loc = table.getLocationOnScreen();
		int x = loc.x + 4;
		int y = loc.y + table.getHeight() - searchLabel.getPreferredSize().height - 4;
		searchPopup = PopupFactory.getSharedInstance().getPopup(table, searchLabel, x, y);
		searchPopup.show();
	}

	private void updateSearchPopup() {
		if (searchLabel != null && searchPopup != null) showSearchPopup();
	}

	private void hideSearchPopup() {
		if (searchPopup != null) {
			searchPopup.hide();
			searchPopup = null;
		}
		searchQuery = null;
	}

	private void selectFirstMatch(String query) {
		String lower = query.toLowerCase();
		for (int i = 0; i < model.getRowCount(); i++) {
			if (model.getEntryAt(i).displayName().toLowerCase().startsWith(lower)) {
				table.setRowSelectionInterval(i, i);
				table.scrollRectToVisible(table.getCellRect(i, 0, true));
				return;
			}
		}
	}

	// ── Deletion (F8) ────────────────────────────────────────────────────────

	/**
	 * Deletes the currently selected entry after user confirmation.
	 *
	 * <ul>
	 *   <li>File → single "Delete file?" confirmation.
	 *   <li>Empty directory → single "Delete folder?" confirmation.
	 *   <li>Non-empty directory → stronger second confirmation showing the
	 *       number of immediate children.
	 * </ul>
	 *
	 * <p>Silently ignored when the current filesystem does not support
	 * {@link Operation#DELETE} (e.g. a read-only remote mount).
	 * Works transparently for ZIP archives via the NIO.2 ZIP filesystem.
	 */
	private void deleteSelected() {
		if (currentPath == null) return;
		if (!mountRegistry.capabilitiesFor(currentPath).supports(Operation.DELETE)) return;

		int[] viewRows = table.getSelectedRows();
		if (viewRows == null || viewRows.length == 0) return;

		var selectedEntries = new ArrayList<EntryInfo>();
		int firstSelectedViewRow = Integer.MAX_VALUE;
		for (int viewRow : viewRows) {
			var candidate = model.getEntryAt(table.convertRowIndexToModel(viewRow));
			if (candidate.isParentEntry()) continue;
			selectedEntries.add(candidate);
			firstSelectedViewRow = Math.min(firstSelectedViewRow, viewRow);
		}
		if (selectedEntries.isEmpty()) return;

		if (selectedEntries.size() > 1) {
			long folderCount = selectedEntries.stream().filter(EntryInfo::directory).count();
			long fileCount = selectedEntries.size() - folderCount;
			String msg =
					"Delete " + selectedEntries.size() + " selected item(s)?\n"
					+ "Folders: " + folderCount + ", Files: " + fileCount + "\n"
					+ "Non-empty folders will be deleted recursively.";
			if (!confirmDelete(msg, "Delete Selected Items")) return;

			selectRowAfterLoad = firstSelectedViewRow == Integer.MAX_VALUE ? -1 : firstSelectedViewRow;
			Path refreshPath = currentPath;
			var toDelete = new ArrayList<>(selectedEntries);
			Thread.ofVirtual().start(() -> performDeleteMany(refreshPath, toDelete));
			return;
		}

		var entry = selectedEntries.get(0);
		int viewRow = firstSelectedViewRow == Integer.MAX_VALUE ? table.getSelectedRow() : firstSelectedViewRow;

		Path target = entry.path();
		String name  = entry.displayName();

		if (!entry.directory()) {
			if (!confirmDelete("Delete file '" + name + "'?", "Delete")) return;
			selectRowAfterLoad = viewRow;
			Path refreshPath = currentPath;
			Thread.ofVirtual().start(() -> performDelete(refreshPath, target, false));
			return;
		}

		// Directory — check if empty using a single DirectoryStream peek
		boolean empty;
		try (var stream = Files.newDirectoryStream(target)) {
			empty = !stream.iterator().hasNext();
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(this,
					"Cannot access folder: " + ex.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		if (empty) {
			if (!confirmDelete("Delete empty folder '" + name + "'?", "Delete")) return;
			selectRowAfterLoad = viewRow;
			Path refreshPath = currentPath;
			Thread.ofVirtual().start(() -> performDelete(refreshPath, target, false));
		} else {
			long count;
			try (var stream = Files.list(target)) {
				count = stream.count();
			} catch (IOException ex) {
				count = -1;
			}
			String msg = count >= 0
					? "'" + name + "' contains " + count + " item(s).\nDelete folder and all its contents?"
					: "'" + name + "' is not empty.\nDelete folder and all its contents?";
			if (!confirmDelete(msg, "Delete Non-Empty Folder")) return;
			selectRowAfterLoad = viewRow;
			Path refreshPath = currentPath;
			Thread.ofVirtual().start(() -> performDelete(refreshPath, target, true));
		}
	}

	private String promptArchivePassword(Path archivePath) {
		JPasswordField field = new JPasswordField(20);
		JOptionPane pane = new JOptionPane(
				new Object[] { "Password for " + archivePath.getFileName() + ":", field },
				JOptionPane.QUESTION_MESSAGE,
				JOptionPane.OK_CANCEL_OPTION);
		JDialog dialog = pane.createDialog(this, "Password Protected Archive");
		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowOpened(java.awt.event.WindowEvent e) {
				SwingUtilities.invokeLater(field::requestFocusInWindow);
			}
		});
		dialog.setVisible(true);
		dialog.dispose();
		Object value = pane.getValue();
		int result = value instanceof Integer i ? i : JOptionPane.CANCEL_OPTION;
		if (result != JOptionPane.OK_OPTION) {
			return null;
		}
		return new String(field.getPassword());
	}

	private static boolean isPasswordRequiredError(IOException error) {
		String msg = error.getMessage();
		if (msg == null) {
			return false;
		}
		String lower = msg.toLowerCase();
		return lower.contains("password")
				|| lower.contains("encrypted")
				|| lower.contains("wrong password");
	}

	/**
	 * Shows a Yes/No confirmation dialog with "No" as the default focused button.
	 * Tab and Left/Right arrow keys navigate between buttons.
	 *
	 * @return {@code true} if the user explicitly chose "Yes"
	 */
	private boolean confirmDelete(String message, String title) {
		Object[] options = {"Yes", "No"};
		var pane = new JOptionPane(message,
				JOptionPane.WARNING_MESSAGE,
				JOptionPane.YES_NO_OPTION,
				null,
				options,
				options[1]);   // "No" has initial focus

		JDialog dialog = pane.createDialog(this, title);

		// Bind Left/Right arrows on every button to cycle focus between buttons.
		List<JButton> buttons = collectButtons(dialog);
		for (int i = 0; i < buttons.size(); i++) {
			JButton btn = buttons.get(i);
			int prev = (i - 1 + buttons.size()) % buttons.size();
			int next = (i + 1) % buttons.size();
			btn.getInputMap(JComponent.WHEN_FOCUSED)
					.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0), "focusPrev");
			btn.getInputMap(JComponent.WHEN_FOCUSED)
					.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "focusNext");
			btn.getActionMap().put("focusPrev",
					new AbstractAction() { public void actionPerformed(ActionEvent e) { buttons.get(prev).requestFocusInWindow(); } });
			btn.getActionMap().put("focusNext",
					new AbstractAction() { public void actionPerformed(ActionEvent e) { buttons.get(next).requestFocusInWindow(); } });
		}

		dialog.setVisible(true);
		dialog.dispose();

		Object value = pane.getValue();
		return value != null
				&& value != JOptionPane.UNINITIALIZED_VALUE
				&& value.equals(options[0]);   // "Yes"
	}

	/** Recursively collects all {@link JButton}s in a container, in traversal order. */
	private static List<JButton> collectButtons(Container container) {
		List<JButton> result = new ArrayList<>();
		for (Component c : container.getComponents()) {
			if (c instanceof JButton btn) {
				result.add(btn);
			} else if (c instanceof Container inner) {
				result.addAll(collectButtons(inner));
			}
		}
		return result;
	}

	/**
	 * Performs the actual deletion on a virtual thread (never called from EDT).
	 * Refreshes the listing on completion; shows an error dialog on failure.
	 *
	 * @param target    path to delete
	 * @param recursive if {@code true}, recursively deletes a non-empty directory
	 */
	private void performDelete(Path refreshPath, Path target, boolean recursive) {
		try {
			deletePath(target, recursive);
			if (refreshPath != null) {
				enterPath(refreshPath, null);
			}
		} catch (IOException ex) {
			log.warn("Cannot delete {}: {}", target, ex.getMessage());
			SwingUtilities.invokeLater(() ->
					JOptionPane.showMessageDialog(this,
							"Cannot delete: " + ex.getMessage(),
							"Error", JOptionPane.ERROR_MESSAGE));
		}
	}

	private void performDeleteMany(Path refreshPath, List<EntryInfo> entries) {
		var failures = new ArrayList<String>();

		entries.sort(Comparator.comparingInt((EntryInfo e) -> e.path().getNameCount()).reversed());
		for (EntryInfo entry : entries) {
			try {
				deletePath(entry.path(), entry.directory());
			} catch (NoSuchFileException ignored) {
				// Can happen when a selected child is already removed via parent recursive delete.
			} catch (IOException ex) {
				log.warn("Cannot delete {}: {}", entry.path(), ex.getMessage());
				failures.add(entry.displayName() + ": " + ex.getMessage());
			}
		}

		if (refreshPath != null) {
			enterPath(refreshPath, null);
		}

		if (!failures.isEmpty()) {
			String details = String.join("\n", failures.stream().limit(12).toList());
			String suffix = failures.size() > 12 ? "\n..." : "";
			SwingUtilities.invokeLater(() ->
					JOptionPane.showMessageDialog(
							this,
							"Some items could not be deleted:\n" + details + suffix,
							"Delete Errors",
							JOptionPane.ERROR_MESSAGE));
		}
	}

	private void deletePath(Path target, boolean recursive) throws IOException {
		if (recursive) {
			Files.walkFileTree(target, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
			return;
		}
		Files.delete(target);
	}

	// ── Folder creation (F7) ─────────────────────────────────────────────────

	/**
	 * Shows an input dialog for a new folder name and creates the directory.
	 * Silently ignored when the current filesystem does not support
	 * {@link Operation#CREATE_DIRECTORY} (e.g. inside a ZIP archive).
	 */
	private void createNewFolder() {
		if (currentPath == null) return;
		if (!mountRegistry.capabilitiesFor(currentPath).supports(Operation.CREATE_DIRECTORY)) return;

		String name = JOptionPane.showInputDialog(
				this,
				"Folder name:",
				"New Folder",
				JOptionPane.PLAIN_MESSAGE);

		if (name == null || name.isBlank()) return;
		name = name.strip();

		if (name.contains("/") || name.contains("\\")) {
			JOptionPane.showMessageDialog(this,
					"Folder name cannot contain path separators.",
					"Invalid Name", JOptionPane.WARNING_MESSAGE);
			return;
		}

		Path newDir = currentPath.resolve(name);
		try {
			Files.createDirectory(newDir);
			enterPath(currentPath, newDir);
		} catch (IOException ex) {
			log.warn("Cannot create directory {}: {}", newDir, ex.getMessage());
			JOptionPane.showMessageDialog(this,
					"Cannot create folder: " + ex.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	/**
	 * Returns {@code true} if {@code path}'s extension is in the
	 * {@link #WINDOWS_EXECUTABLE_EXTENSIONS} set (case-insensitive).
	 * Used when the filesystem does not expose POSIX permissions.
	 */
	private static boolean hasWindowsExecutableExtension(Path path) {
		String name = path.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot >= 0 && Defaults.EXECUTABLE_EXTENSIONS.contains(name.substring(dot).toLowerCase());
	}

	private static boolean isHiddenEntry(Path path) {
		try {
			return Files.isHidden(path);
		} catch (IOException ex) {
			return false;
		}
	}

	private static boolean isSystemEntry(Path path) {
		try {
			DosFileAttributes attrs = Files.readAttributes(path, DosFileAttributes.class);
			return attrs.isSystem();
		} catch (Exception ex) {
			return false;
		}
	}

	private static boolean extraFlag(EntryInfo entry, String key) {
		if (entry.extras() == null) {
			return false;
		}
		Object value = entry.extras().get(key);
		return value instanceof Boolean b && b;
	}

	private boolean handleProviderFunctionKey(int functionKeyNumber, boolean shiftDown) {
		if (currentPath == null) {
			return false;
		}
		return providerRegistry.findProviderFor(currentPath)
				.map(p -> p.functionKeyHandler().handle(functionKeyNumber, shiftDown, currentPath, selectedPathOrNull()))
				.orElse(false);
	}

	private Path selectedPathOrNull() {
		int viewRow = table.getSelectedRow();
		if (viewRow < 0) {
			return null;
		}
		var entry = model.getEntryAt(table.convertRowIndexToModel(viewRow));
		return entry.isParentEntry() ? null : entry.path();
	}

	private Path resolveProviderEnterPath(Path selectedPath) {
		if (currentPath == null) {
			return selectedPath;
		}
		return providerRegistry.findProviderFor(currentPath)
				.map(p -> p.resolveEnter(currentPath, selectedPath))
				.orElse(selectedPath);
	}
}
