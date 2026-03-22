package dev.nuclr.commander.ui;

import java.awt.Component;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

import dev.nuclr.plugin.PluginPathResource;

/**
 * Shows a popup menu listing change-drive resources for the active panel plugin.
 */
public class ChangeDrivePopup {

    public static void show(
            Component anchorComponent,
            List<? extends PluginPathResource> resources,
            PluginPathResource currentResource,
            Consumer<PluginPathResource> onSelect) {
        JPopupMenu popup = new JPopupMenu("Change Drive");

        JMenuItem currentItem = null;

        for (PluginPathResource root : resources) {
            String label = root.getName();
            JMenuItem item = new JMenuItem(label);
            item.addActionListener(e -> onSelect.accept(root));
            if (!label.isEmpty()) {
                item.setMnemonic(Character.toUpperCase(label.charAt(0)));
            }
            popup.add(item);
            if (sameResource(root, currentResource)) {
                currentItem = item;
            }
        }

        if (anchorComponent == null || !anchorComponent.isShowing()) {
            return;
        }

        popup.show(anchorComponent, Math.max(anchorComponent.getWidth() / 2, 0), 0);

        if (currentItem != null) {
            final JMenuItem itemToSelect = currentItem;
            SwingUtilities.invokeLater(() ->
                    MenuSelectionManager.defaultManager().setSelectedPath(
                            new MenuElement[]{popup, itemToSelect}));
        }
    }

    private static boolean sameResource(PluginPathResource left, PluginPathResource right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (left.getUuid() != null && right.getUuid() != null) {
            return Objects.equals(left.getUuid(), right.getUuid());
        }
        return Objects.equals(left.getName(), right.getName());
    }
}
