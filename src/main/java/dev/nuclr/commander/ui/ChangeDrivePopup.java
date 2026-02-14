package dev.nuclr.commander.ui;

import java.io.File;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

public class ChangeDrivePopup {

	public static void show(FilePanel targetPanel) {
		JPopupMenu popup = new JPopupMenu("Change Drive");

		File currentRoot = targetPanel.getCurrentRoot();
		JMenuItem currentItem = null;

		for (File root : File.listRoots()) {
			String path = root.getAbsolutePath();
			JMenuItem item = new JMenuItem(path);
			item.addActionListener(e -> targetPanel.navigateTo(root));
			if (!path.isEmpty()) {
				item.setMnemonic(Character.toUpperCase(path.charAt(0)));
			}
			popup.add(item);
			if (root.equals(currentRoot)) {
				currentItem = item;
			}
		}

		popup.show(targetPanel, targetPanel.getWidth() / 2, 0);

		if (currentItem != null) {
			final JMenuItem itemToSelect = currentItem;
			SwingUtilities.invokeLater(() -> {
				MenuSelectionManager.defaultManager().setSelectedPath(
						new MenuElement[]{popup, itemToSelect});
			});
		}
	}

}
