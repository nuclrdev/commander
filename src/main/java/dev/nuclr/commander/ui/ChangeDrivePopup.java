package dev.nuclr.commander.ui;

import java.nio.file.Path;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

import dev.nuclr.commander.panel.FilePanelProviderRegistry;
import dev.nuclr.plugin.panel.PanelRoot;

/**
 * Shows a popup menu listing all roots from all registered
 * {@link dev.nuclr.plugin.panel.FilePanelProvider}s so the user can switch
 * the active panel to a different drive, mount point, or remote connection.
 *
 * <p>Roots are obtained from {@link FilePanelProviderRegistry#listAllRoots()}.
 */
public class ChangeDrivePopup {

    public static void show(FilePanel targetPanel, FilePanelProviderRegistry providerRegistry) {
        JPopupMenu popup = new JPopupMenu("Change Drive");

        Path currentRoot = targetPanel.getCurrentRoot();
        JMenuItem currentItem = null;

        for (PanelRoot root : providerRegistry.listAllRoots()) {
            String label = root.displayName();
            JMenuItem item = new JMenuItem(label);
            item.addActionListener(e -> targetPanel.navigateTo(root.path()));
            if (!label.isEmpty()) {
                item.setMnemonic(Character.toUpperCase(label.charAt(0)));
            }
            popup.add(item);
            if (root.path().equals(currentRoot)) {
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
