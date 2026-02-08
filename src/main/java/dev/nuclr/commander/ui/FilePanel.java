package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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

	private JTable table;

	private JLabel bottomFileInfoTextLabel;

	private JLabel topPathTextLabel;

	public FilePanel(ApplicationEventPublisher applicationEventPublisher) {

		this.applicationEventPublisher = applicationEventPublisher;

		this.setLayout(new BorderLayout());

		table = new JTable();

		table.setFillsViewportHeight(true);
		table.getTableHeader().setReorderingAllowed(false);
		table.setRowHeight(table.getRowHeight() + 4);
		
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

}
