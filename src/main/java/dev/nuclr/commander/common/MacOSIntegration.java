/*

	Copyright 2026 Sergio, Nuclr (https://nuclr.dev)
	
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	
	http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

*/
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
                        Version """ + SystemUtils.getAppVersion() + """

                        Plugin-driven developer commander.

                        © 2026  Nuclr Development Team
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