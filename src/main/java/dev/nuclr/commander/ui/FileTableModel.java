package dev.nuclr.commander.ui;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.FileUtils;

public class FileTableModel extends javax.swing.table.AbstractTableModel {

	private static final long serialVersionUID = 1L;
	
	private List<File> files;
	
	private boolean isRoot;

	public FileTableModel(File folder, List<File> files) {
		
		this.files = new ArrayList<File>(files);
		
		isRoot = folder.getParent() == null;
		
		if (isRoot == false) {
			this.files.add(0, new File(".."));
		}
		
		
	}
	
	private void init() {
		
	}

	@Override
	public int getRowCount() {
		return files.size();
	}

	@Override
	public int getColumnCount() {
		return 4;
	}

	private static final DateFormat date = DateFormat .getDateInstance(DateFormat.SHORT, Locale.getDefault());
	private static final DateFormat time = new SimpleDateFormat("HH:mm");

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		
		var file = files.get(rowIndex);
		
		if (columnIndex == 0) {

			return files.get(rowIndex).getName();
			
		}

		
		if (columnIndex == 1) {
			
//			if (rowIndex == 0) {
//				return "Up";
//			}			

			if (files.get(rowIndex).isDirectory()) {
				return FileUtils.byteCountToDisplaySize(files.get(rowIndex).length());
			} else {
				return "Folder";
			}

		} else if (columnIndex == 2) {
			return date.format(files.get(rowIndex).lastModified());
		} else if (columnIndex == 3) {
			return time.format(files.get(rowIndex).lastModified());
		} else {
			return "-";
		}

	}

	public File getFileAt(int selectedRow) {
		return files.get(selectedRow);
	}

}
