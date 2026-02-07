package dev.nuclr.commander.ui;

import org.apache.commons.io.FileUtils;

public class FileTableModel extends javax.swing.table.AbstractTableModel {

	private java.io.File[] files;

	public FileTableModel(java.io.File[] files) {
		this.files = files;
	}

	@Override
	public int getRowCount() {
		return files.length;
	}

	@Override
	public int getColumnCount() {
		return 2;
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {

		if (columnIndex == 0) {
			
			return files[rowIndex].getName();
			
		} else {
			
			if (files[rowIndex].isDirectory()) {
				return FileUtils.byteCountToDisplaySize(files[rowIndex].length());
			} else {
				return "Folder";
			}
			
		}

	}

}
