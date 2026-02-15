package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;

import org.springframework.context.ApplicationEventPublisher;

import dev.nuclr.commander.event.FileSelectedEvent;
import dev.nuclr.commander.event.ListViewFileOpen;
import dev.nuclr.commander.event.ShowEditorScreenEvent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FilePanel extends JPanel {

	private ApplicationEventPublisher applicationEventPublisher;

	private JTable table;

	private JLabel bottomFileInfoTextLabel;

	private JLabel topPathTextLabel;

	private final Map<File, File> lastFolderPerDrive = new HashMap<>();

	private StringBuilder searchQuery;
	private Popup searchPopup;
	private JLabel searchLabel;

	public FilePanel(ApplicationEventPublisher applicationEventPublisher) {

		this.applicationEventPublisher = applicationEventPublisher;

		this.setLayout(new BorderLayout());

		table = new JTable();

		table.setFillsViewportHeight(true);
		table.getTableHeader().setReorderingAllowed(false);
		table.setRowHeight(table.getRowHeight() + 4);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

		table.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		
		this.add(new JScrollPane(table), BorderLayout.CENTER);

		var root = new File("c://");

		topPathTextLabel = new JLabel(root.getAbsolutePath());
		topPathTextLabel.setHorizontalAlignment(JLabel.CENTER);
		this.add(topPathTextLabel, BorderLayout.NORTH);

		var files = List.of(root.listFiles());

		// Populate the table with the files
		var model = new FileTableModel(root, files);

		table.setModel(model);

		// Column sizing: Name stretches, Size/Date/Time are fixed-width
		var columnModel = table.getColumnModel();
		columnModel.getColumn(1).setPreferredWidth(80);  // Size
		columnModel.getColumn(1).setMaxWidth(100);
		columnModel.getColumn(2).setPreferredWidth(80);  // Date
		columnModel.getColumn(2).setMaxWidth(100);
		columnModel.getColumn(3).setPreferredWidth(60);  // Time
		columnModel.getColumn(3).setMaxWidth(80);

		table.setShowVerticalLines(true);

		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			@Override
			public java.awt.Component getTableCellRendererComponent(
					JTable table,
					Object value,
					boolean isSelected,
					boolean hasFocus,
					int row,
					int column) {
				var comp = super.getTableCellRendererComponent(
						table,
						value,
						isSelected,
						hasFocus,
						row,
						column);
				int modelRow = table.convertRowIndexToModel(row);
				var file = model.getFileAt(modelRow);
				if (file.isDirectory()) {
					comp.setFont(comp.getFont().deriveFont(java.awt.Font.BOLD));
				} else {
					comp.setFont(comp.getFont().deriveFont(java.awt.Font.PLAIN));
				}
				return comp;
			}
		});

		bottomFileInfoTextLabel = new JLabel(" ");
		bottomFileInfoTextLabel.setHorizontalAlignment(JLabel.LEFT);
		bottomFileInfoTextLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		this.add(bottomFileInfoTextLabel, BorderLayout.SOUTH);

		table.getSelectionModel().addListSelectionListener(e -> {
			int selectedRow = table.getSelectedRow();
			if (selectedRow >= 0) {
				var file = model.getFileAt(selectedRow);
				bottomFileInfoTextLabel.setText(file.getName() + " | " + file.length() + " bytes");
				applicationEventPublisher.publishEvent(new FileSelectedEvent(this, file));
			} else {
				bottomFileInfoTextLabel.setText("");
			}
		});

		table.setRowSelectionInterval(0, 0);

		// Catch "Enter" key to open the selected row
		var openRowAction = new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JTable table = (JTable) e.getSource();
				int row = table.getSelectedRow();
				if (row >= 0) {
					int modelRow = table.convertRowIndexToModel(row);
					onRowActivated(modelRow);

				}
			}
		};

		table
				.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openRow");

		table.getActionMap().put("openRow", openRowAction);

		// F4 opens the selected file in the editor
		var editFileAction = new AbstractAction() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JTable table = (JTable) e.getSource();
				int row = table.getSelectedRow();
				if (row >= 0) {
					int modelRow = table.convertRowIndexToModel(row);
					var file = model.getFileAt(modelRow);
					if (file.isFile()) {
						applicationEventPublisher.publishEvent(new ShowEditorScreenEvent(this, file));
					}
				}
			}
		};

		table
				.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_F4, 0), "editFile");

		table.getActionMap().put("editFile", editFileAction);

		// Left/Right arrow keys act as Page Up/Page Down
		table
				.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "scrollUpChangeSelection");
		table
				.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "scrollDownChangeSelection");

		// Catch Mouse double click to open the selected row
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
					int viewRow = table.rowAtPoint(e.getPoint());
					if (viewRow >= 0) {
						int modelRow = table.convertRowIndexToModel(viewRow);
						onRowActivated(modelRow);
					}
				}
			}
		});

		// Alt+typing quick search
		table.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ALT) {
					return;
				}
				if (!e.isAltDown()) {
					return;
				}
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
					if (searchQuery == null) {
						searchQuery = new StringBuilder();
					}
					searchQuery.append(Character.toLowerCase(c));
					showSearchPopup();
					selectFirstMatch(searchQuery.toString());
					e.consume();
				}
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ALT) {
					hideSearchPopup();
				}
			}
		});

	}

	protected void onRowActivated(int modelRow) {

		log.info("Open row: {}", modelRow);

		FileTableModel model = (FileTableModel) table.getModel();

		var file = model.getFileAt(modelRow);

		File selectedFolder = null;

		if (file.getName().equals(FileTableModel.ParentFolderName)) {
			selectedFolder = model.getFolder();
			file = model.getFolder().getParentFile();
		}

		if (file.isDirectory()) {
			log.info("Open directory: {}", file.getAbsolutePath());
			model.init(file, List.of(file.listFiles()));
			model.fireTableDataChanged();
			table.setRowSelectionInterval(0, 0);
			selectInTable(selectedFolder);
		} else {
			log.info("Open file: {}", file.getAbsolutePath());
			applicationEventPublisher.publishEvent(new ListViewFileOpen(this, file));
		}

	}

	public File getSelectedFile() {
		int row = table.getSelectedRow();
		if (row < 0) {
			return null;
		}
		int modelRow = table.convertRowIndexToModel(row);
		return ((FileTableModel) table.getModel()).getFileAt(modelRow);
	}

	public File getCurrentRoot() {
		FileTableModel model = (FileTableModel) table.getModel();
		return model.getFolder().toPath().getRoot().toFile();
	}

	public void navigateTo(File directory) {
		FileTableModel model = (FileTableModel) table.getModel();

		// Save current folder under its drive root
		File currentFolder = model.getFolder();
		if (currentFolder != null) {
			File currentRoot = currentFolder.toPath().getRoot().toFile();
			lastFolderPerDrive.put(currentRoot, currentFolder);
		}

		// Restore saved folder for the target drive, if available
		File targetRoot = directory.toPath().getRoot().toFile();
		File savedFolder = lastFolderPerDrive.get(targetRoot);
		if (savedFolder != null && savedFolder.isDirectory()) {
			directory = savedFolder;
		}

		model.init(directory, List.of(directory.listFiles()));
		model.fireTableDataChanged();
		topPathTextLabel.setText(directory.getAbsolutePath());
		table.setRowSelectionInterval(0, 0);
	}

	public void focusFileTable() {
		table.requestFocusInWindow();
	}

	private void selectInTable(File selectedFolder) {

		if (selectedFolder == null) {
			return;
		}

		FileTableModel model = (FileTableModel) table.getModel();

		for (int i = 0; i < model.getRowCount(); i++) {
			var file = model.getFileAt(i);
			if (file.equals(selectedFolder)) {
				table.setRowSelectionInterval(i, i);
				table.scrollRectToVisible(table.getCellRect(i, 0, true));
				break;
			}
		}
	}

	private void showSearchPopup() {
		if (searchPopup != null) {
			searchPopup.hide();
		}
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
		if (searchLabel != null && searchPopup != null) {
			searchLabel.setText("Search: " + searchQuery);
			// Recreate to update size/position
			showSearchPopup();
		}
	}

	private void hideSearchPopup() {
		if (searchPopup != null) {
			searchPopup.hide();
			searchPopup = null;
		}
		searchQuery = null;
	}

	private void selectFirstMatch(String query) {
		FileTableModel model = (FileTableModel) table.getModel();
		String lowerQuery = query.toLowerCase();
		for (int i = 0; i < model.getRowCount(); i++) {
			var file = model.getFileAt(i);
			if (file.getName().toLowerCase().startsWith(lowerQuery)) {
				table.setRowSelectionInterval(i, i);
				table.scrollRectToVisible(table.getCellRect(i, 0, true));
				return;
			}
		}
	}

}
