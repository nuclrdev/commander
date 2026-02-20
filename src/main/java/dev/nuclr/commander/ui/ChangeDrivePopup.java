package dev.nuclr.commander.ui;

import java.nio.file.Path;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

import dev.nuclr.commander.vfs.MountRegistry;

/**
 * Shows a popup menu listing all local filesystem roots so the user can
 * switch the active panel to a different drive (Windows) or mount point.
 *
 * <p>Roots are obtained from {@link MountRegistry#listLocalRoots()} —
 * no direct {@code java.io.File} usage.
 */
public class ChangeDrivePopup {

	public static void show(FilePanel targetPanel, MountRegistry mountRegistry) {
		JPopupMenu popup = new JPopupMenu("Change Drive");

		Path currentRoot = targetPanel.getCurrentRoot();
		JMenuItem currentItem = null;

		for (Path root : mountRegistry.listLocalRoots()) {
			String label = root.toString();
			JMenuItem item = new JMenuItem(label);
			item.addActionListener(e -> targetPanel.navigateTo(root));
			if (!label.isEmpty()) {
				item.setMnemonic(Character.toUpperCase(label.charAt(0)));
			}
			popup.add(item);
			if (root.equals(currentRoot)) {
				currentItem = item;
			}
		}

		popup.show(targetPanel, targetPanel.getWidth() / 2, 0);

		if (currentItem != null) {
			final JMenuItem itemToSelect = currentItem;
			SwingUtilities.invokeLater(() ->
					MenuSelectionManager.defaultManager().setSelectedPath(
							new MenuElement[]{popup, itemToSelect}));
		}
	}
}
