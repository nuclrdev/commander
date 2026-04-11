package dev.nuclr.commander.ui;

import java.awt.Component;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;

import dev.nuclr.platform.plugin.NuclrResourcePath;

/**
 * Shows a popup menu listing change-drive resources for the active panel plugin.
 */
public class ChangeDrivePopup {

    public record Entry(String section, String label, NuclrResourcePath resource) {
    }

    public static void show(
            Component anchorComponent,
            List<Entry> entries,
            NuclrResourcePath currentResource,
            Consumer<NuclrResourcePath> onSelect) {
        JPopupMenu popup = new JPopupMenu("Change Drive");

        JMenuItem currentItem = null;
        String currentSection = null;

        for (Entry entry : entries) {
            if (entry == null || entry.resource() == null) {
                continue;
            }

            if (!Objects.equals(currentSection, entry.section())) {
                if (currentSection != null) {
                    popup.add(new JSeparator());
                }
                if (entry.section() != null && !entry.section().isBlank()) {
                    JMenuItem header = new JMenuItem(entry.section());
                    header.setEnabled(false);
                    popup.add(header);
                }
                currentSection = entry.section();
            }

            String label = entry.label() != null ? entry.label() : "";
            JMenuItem item = new JMenuItem(label);
            item.addActionListener(e -> onSelect.accept(entry.resource()));
            if (!label.isEmpty()) {
                item.setMnemonic(Character.toUpperCase(label.charAt(0)));
            }
            popup.add(item);
            if (sameResource(entry.resource(), currentResource)) {
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

    private static boolean sameResource(NuclrResourcePath left, NuclrResourcePath right) {
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
