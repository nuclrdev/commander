package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;

import org.springframework.context.ApplicationEventPublisher;

import dev.nuclr.commander.common.FilePanelColors;
import dev.nuclr.commander.common.FilePanelColors.Defaults;
import dev.nuclr.commander.common.FileUtils;
import dev.nuclr.commander.event.FileSelectedEvent;
import dev.nuclr.commander.event.ListViewFileOpen;
import dev.nuclr.commander.event.ShowEditorScreenEvent;
import dev.nuclr.commander.vfs.EntryInfo;
import dev.nuclr.commander.vfs.MountRegistry;
import dev.nuclr.commander.vfs.Operation;
import dev.nuclr.commander.vfs.ZipMountProvider;
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
	private final ZipMountProvider zipMountProvider;
	private final FilePanelColors colors;


	private final JTable table;
	private final FileTableModel model;
	private final JLabel topPathTextLabel;
	private final JLabel bottomFileInfoTextLabel;

	/**
	 * Per filesystem-root: the last directory visited on that root.
	 * Keyed by the root {@link Path} (e.g. {@code C:\} on Windows, {@code /} on Linux).
	 * Used to restore position when switching drives.
	 */
	private final Map<Path, Path> lastPathPerRoot = new HashMap<>();

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
			ZipMountProvider zipMountProvider,
			FilePanelColors colors) {

		this.eventPublisher = eventPublisher;
		this.mountRegistry = mountRegistry;
		this.zipMountProvider = zipMountProvider;
		this.colors = colors;

		setLayout(new BorderLayout());

		// ── Table ───────────────────────────────────────────────────────────
		model = new FileTableModel();
		table = new JTable(model);
		table.setFillsViewportHeight(true);
		table.getTableHeader().setReorderingAllowed(false);
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
					if (entry.executable() && !entry.directory()) {
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

		add(new JScrollPane(table), BorderLayout.CENTER);

		// ── Path label (top) ────────────────────────────────────────────────
		topPathTextLabel = new JLabel(" ");
		topPathTextLabel.setHorizontalAlignment(JLabel.CENTER);
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

		// F4 — open in editor
		table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), "editFile");
		table.getActionMap().put("editFile", new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
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
		List<Path> roots = mountRegistry.listLocalRoots();
		if (!roots.isEmpty()) {
			navigateTo(roots.get(0));
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

	/** Requests keyboard focus on the file table. */
	public void focusFileTable() {
		table.requestFocusInWindow();
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
			Path parent = currentPath.getParent();
			if (parent != null) {
				enterPath(parent, currentPath);
			} else {
				// At the root of a non-local FS (e.g. ZIP archive) — navigate back
				// to the local directory that contains the archive and re-select it.
				// currentPath.toUri() for a ZIP root gives jar:file:///path/to/archive.zip!/
				try {
					URI zipUri = currentPath.toUri();
					String ssp = zipUri.getSchemeSpecificPart(); // file:///path/to/archive.zip!/
					int bang = ssp != null ? ssp.indexOf("!/") : -1;
					if (bang >= 0) {
						URI fileUri = URI.create(ssp.substring(0, bang));
						Path archivePath = Path.of(fileUri);
						Path archiveDir = archivePath.getParent();
						if (archiveDir != null) {
							enterPath(archiveDir, archivePath);
						}
					}
				} catch (Exception ex) {
					log.warn("Cannot navigate out of archive: {}", ex.getMessage());
				}
			}
			return;
		}

		if (entry.directory()) {
			log.info("Enter directory: {}", entry.path());
			enterPath(entry.path(), null);
		} else {
			// Check if it's a ZIP/JAR archive — offer to browse inside
			String name = entry.displayName().toLowerCase();
			if (name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith(".war") || name.endsWith(".ear")) {
				try {
					Path archiveRoot = zipMountProvider.mountAndGetRoot(entry.path());
					log.info("Entering ZIP archive: {}", entry.path());
					enterPath(archiveRoot, null);
					return;
				} catch (IOException ex) {
					log.warn("Cannot mount archive {}: {}", entry.path(), ex.getMessage());
					// fall through to default open
				}
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

		// Update path label immediately for snappy feedback
		SwingUtilities.invokeLater(() -> topPathTextLabel.setText(dir.toAbsolutePath().toString()));

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
			} catch (IOException ex) {
				log.error("Cannot list directory: {}", dir, ex);
				SwingUtilities.invokeLater(() ->
						bottomFileInfoTextLabel.setText("Error: " + ex.getMessage()));
			}
		});
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
								Map.of());
					} else {
						var attrs = Files.readAttributes(child, BasicFileAttributes.class);
						boolean isDir = attrs.isDirectory();
						boolean exec  = !isDir && colors.isWindowsExecutable(child);
						boolean arch  = !isDir && colors.isArchive(child);
						info = new EntryInfo(
								child,
								child.getFileName().toString(),
								isDir,
								exec,
								arch,
								isDir ? 0L : attrs.size(),
								attrs.lastModifiedTime(),
								null, null,
								Map.of());
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

		int viewRow = table.getSelectedRow();
		if (viewRow < 0) return;
		var entry = model.getEntryAt(table.convertRowIndexToModel(viewRow));
		if (entry.isParentEntry()) return;

		Path target = entry.path();
		String name  = entry.displayName();

		if (!entry.directory()) {
			int confirm = JOptionPane.showConfirmDialog(this,
					"Delete file '" + name + "'?",
					"Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (confirm != JOptionPane.YES_OPTION) return;
			selectRowAfterLoad = viewRow;
			Thread.ofVirtual().start(() -> performDelete(target, false));
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
			int confirm = JOptionPane.showConfirmDialog(this,
					"Delete empty folder '" + name + "'?",
					"Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (confirm != JOptionPane.YES_OPTION) return;
			selectRowAfterLoad = viewRow;
			Thread.ofVirtual().start(() -> performDelete(target, false));
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
			int confirm = JOptionPane.showConfirmDialog(this, msg,
					"Delete Non-Empty Folder", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (confirm != JOptionPane.YES_OPTION) return;
			selectRowAfterLoad = viewRow;
			Thread.ofVirtual().start(() -> performDelete(target, true));
		}
	}

	/**
	 * Performs the actual deletion on a virtual thread (never called from EDT).
	 * Refreshes the listing on completion; shows an error dialog on failure.
	 *
	 * @param target    path to delete
	 * @param recursive if {@code true}, recursively deletes a non-empty directory
	 */
	private void performDelete(Path target, boolean recursive) {
		try {
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
			} else {
				Files.delete(target);
			}
			enterPath(currentPath, null);
		} catch (IOException ex) {
			log.warn("Cannot delete {}: {}", target, ex.getMessage());
			SwingUtilities.invokeLater(() ->
					JOptionPane.showMessageDialog(this,
							"Cannot delete: " + ex.getMessage(),
							"Error", JOptionPane.ERROR_MESSAGE));
		}
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
}
