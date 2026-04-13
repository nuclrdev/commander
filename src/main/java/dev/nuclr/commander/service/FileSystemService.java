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
package dev.nuclr.commander.service;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.swing.JOptionPane;

import org.springframework.stereotype.Service;

import dev.nuclr.commander.ui.common.Alerts;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class FileSystemService {

	public void open(Path path) {

		if (path == null || !Files.exists(path)) {
			return;
		}
		if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			Alerts.showMessageDialog(null,
					"Opening items with the system default application is not supported on this platform.", "Error",
					JOptionPane.ERROR_MESSAGE);
			return;
		}
		try {
			Desktop.getDesktop().open(path.toFile());
		} catch (Exception ex) {
			Alerts.showMessageDialog(null, "Cannot open item " + path.toFile(), "Error", JOptionPane.ERROR_MESSAGE);
		}

	}

}
