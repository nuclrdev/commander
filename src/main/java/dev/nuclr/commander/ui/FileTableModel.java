package dev.nuclr.commander.ui;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.nuclr.commander.common.FileUtils;
import dev.nuclr.plugin.panel.EntryInfo;

/**
 * Swing table model for the file panel.
 *
 * <p>Works exclusively with {@link EntryInfo} records — no {@code java.io.File}
 * dependency. Columns: Name | Size | Date | Time.
 */
public class FileTableModel extends javax.swing.table.AbstractTableModel {

	public static final String ParentFolderName = EntryInfo.PARENT_ENTRY_NAME;

	private static final long serialVersionUID = 1L;

	private List<EntryInfo> entries = new ArrayList<>();

	// ── Mutators ────────────────────────────────────────────────────────────

	/** Replaces the current listing and fires a table data changed event. */
	public void setEntries(List<EntryInfo> entries) {
		this.entries = new ArrayList<>(entries);
		fireTableDataChanged();
	}

	// ── AbstractTableModel ───────────────────────────────────────────────────

	@Override
	public int getRowCount() {
		return entries.size();
	}

	@Override
	public int getColumnCount() {
		return 4;
	}

	private static final DateFormat DATE_FMT =
			DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault());

	private static final DateFormat TIME_FMT =
			new SimpleDateFormat("HH:mm", Locale.getDefault());

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		var entry = entries.get(rowIndex);

		return switch (columnIndex) {
			case 0 -> entry.displayName();
			case 1 -> {
				if (entry.isParentEntry()) yield "";
				yield entry.directory()
						? "Folder"
						: FileUtils.byteCountToDisplaySize(entry.size());
			}
			case 2 -> entry.isParentEntry() || entry.modified() == null
					? ""
					: DATE_FMT.format(entry.modified().toMillis());
			case 3 -> entry.isParentEntry() || entry.modified() == null
					? ""
					: TIME_FMT.format(entry.modified().toMillis());
			default -> "-";
		};
	}

	@Override
	public String getColumnName(int column) {
		return switch (column) {
			case 0 -> "Name";
			case 1 -> "Size";
			case 2 -> "Date";
			case 3 -> "Time";
			default -> "-";
		};
	}

	// ── Accessors ────────────────────────────────────────────────────────────

	/** Returns the {@link EntryInfo} at the given model row index. */
	public EntryInfo getEntryAt(int row) {
		return entries.get(row);
	}
}
