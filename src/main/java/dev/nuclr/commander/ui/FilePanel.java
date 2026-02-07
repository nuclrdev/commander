package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTable;

public class FilePanel extends JPanel {

	private JTable fileTable;

	public FilePanel() {

		this.setLayout(new BorderLayout());

		fileTable = new JTable();
		fileTable .setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
		

		this.add(fileTable, BorderLayout.CENTER);

		var root = new File("c://");

		var files = root.listFiles();

		// Populate the table with the files
		var model = new FileTableModel(files);

		fileTable.setModel(model);

	}

}
