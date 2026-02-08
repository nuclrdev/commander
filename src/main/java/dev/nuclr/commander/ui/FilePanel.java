package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;

import org.springframework.context.ApplicationEventPublisher;

import dev.nuclr.commander.event.ListViewFileOpen;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FilePanel extends JPanel {

	private ApplicationEventPublisher applicationEventPublisher;

	private JTable fileTable;

	private JLabel fileLabel;

	private JLabel pathLabel;

	public FilePanel(ApplicationEventPublisher applicationEventPublisher) {

		this.applicationEventPublisher = applicationEventPublisher;

		this.setLayout(new BorderLayout());

		fileTable = new JTable();
		fileTable.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

		this.add(new JScrollPane(fileTable), BorderLayout.CENTER);

		var root = new File("c://");

		pathLabel = new JLabel(root.getAbsolutePath());
		pathLabel.setHorizontalAlignment(JLabel.CENTER);
		this.add(pathLabel, BorderLayout.NORTH);

		var files = List.of(root.listFiles());

		// Populate the table with the files
		var model = new FileTableModel(root, files);

		fileTable.setModel(model);

		fileTable.setShowVerticalLines(true);

		fileTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
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

		fileLabel = new JLabel(" ");
		fileLabel.setHorizontalAlignment(JLabel.LEFT);
		fileLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		this.add(fileLabel, BorderLayout.SOUTH);

		fileTable.getSelectionModel().addListSelectionListener(e -> {
			int selectedRow = fileTable.getSelectedRow();
			if (selectedRow >= 0) {
				var file = model.getFileAt(selectedRow);

				fileLabel.setText(file.getName() + " | " + file.length() + " bytes");
			} else {
				fileLabel.setText("");
			}
		});

		fileTable.setRowSelectionInterval(0, 0);

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

		fileTable
				.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openRow");

		fileTable.getActionMap().put("openRow", openRowAction);

		// Left/Right arrow keys act as Page Up/Page Down
		fileTable
				.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "scrollUpChangeSelection");
		fileTable
				.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "scrollDownChangeSelection");

		// Catch Mouse double click to open the selected row
		fileTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
					int viewRow = fileTable.rowAtPoint(e.getPoint());
					if (viewRow >= 0) {
						int modelRow = fileTable.convertRowIndexToModel(viewRow);
						onRowActivated(modelRow);
					}
				}
			}
		});

	}

	protected void onRowActivated(int modelRow) {

		log.info("Open row: {}", modelRow);

		FileTableModel model = (FileTableModel) fileTable.getModel();

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
			fileTable.setRowSelectionInterval(0, 0);
			selectInTable(selectedFolder);
		} else {
			log.info("Open file: {}", file.getAbsolutePath());
			applicationEventPublisher.publishEvent(new ListViewFileOpen(this, file));
		}
		
		

	}

	public void focusFileTable() {
		fileTable.requestFocusInWindow();
	}

	private void selectInTable(File selectedFolder) {

		if (selectedFolder == null) {
			return;
		}

		FileTableModel model = (FileTableModel) fileTable.getModel();

		for (int i = 0; i < model.getRowCount(); i++) {
			var file = model.getFileAt(i);
			if (file.equals(selectedFolder)) {
				fileTable.setRowSelectionInterval(i, i);
				fileTable.scrollRectToVisible(fileTable.getCellRect(i, 0, true));
				break;
			}
		}
	}

}
