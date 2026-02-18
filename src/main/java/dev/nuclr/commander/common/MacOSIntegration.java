package dev.nuclr.commander.common;
import java.awt.Desktop;
import java.awt.desktop.AboutEvent;
import java.awt.desktop.AboutHandler;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class MacOSIntegration {

    public static void installAboutHandler() {

        if (!Desktop.isDesktopSupported()) {
            return;
        }

        Desktop desktop = Desktop.getDesktop();

        desktop.setAboutHandler(new AboutHandler() {

            @Override
            public void handleAbout(AboutEvent e) {

                SwingUtilities.invokeLater(() -> {

                    JOptionPane.showMessageDialog(
                        null,
                        """
                        Nuclr Commander
                        Version 1.0.0

                        Plugin-driven developer commander.

                        © 2026 Nuclr Development Team
                        """,
                        "About Nuclr Commander",
                        JOptionPane.INFORMATION_MESSAGE
                    );

                });

            }
        });
        
        desktop.setPreferencesHandler(e -> {
            SwingUtilities.invokeLater(() -> {
      //          new SettingsDialog().setVisible(true);
            });
        });
        
    }
}