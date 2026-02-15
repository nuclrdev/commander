package dev.nuclr.commander.ui.common;

import java.awt.Component;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public final class Alerts {

	public static void showMessageDialog(
			Component parentComponent,
			Object message,
			String title,
			int messageType) {

		SwingUtilities.invokeLater(() -> {

			JOptionPane
					.showMessageDialog(
							parentComponent,
							message,
							title,
							messageType);

		});

	}

}
