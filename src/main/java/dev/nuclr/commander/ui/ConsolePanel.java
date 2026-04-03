package dev.nuclr.commander.ui;

import java.awt.BorderLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.springframework.stereotype.Component;

import com.jediterm.terminal.TerminalColor;
import com.jediterm.terminal.TextStyle;
import com.jediterm.terminal.ui.JediTermWidget;
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider;
import com.pty4j.PtyProcess;

import dev.nuclr.commander.common.SystemUtils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@Component
public class ConsolePanel {

	private JPanel consolePanel;
	private JediTermWidget termWidget;

	@PostConstruct
	public void init() {
		log.info("Initializing ConsolePanel...");

		consolePanel = new JPanel(new BorderLayout());

		DefaultSettingsProvider settings = new DefaultSettingsProvider() {
			@Override
			public TextStyle getDefaultStyle() {
				return new TextStyle(
						TerminalColor.fromColor(new com.jediterm.core.Color(255, 255, 255)),
						TerminalColor.fromColor(new com.jediterm.core.Color(0, 0, 0)));
			}
		};
		
		settings.useAntialiasing();
		
		termWidget = new JediTermWidget(settings);

		consolePanel.add(termWidget, BorderLayout.CENTER);

		// Pick a shell per OS (very basic)
		String[] cmd = SystemUtils.isOsWindows()
				? new String[] { "cmd.exe" }
				: new String[] { "/bin/bash", "-l" };

		try {
			Map<String, String> env = new HashMap<>(System.getenv());
			String cwd = Path.of(System.getProperty("user.home")).toAbsolutePath().toString();

			PtyProcess process = PtyProcess.exec(cmd, env, cwd);

			termWidget.setTtyConnector(new PtyProcessTtyConnector(process, StandardCharsets.UTF_8));
			termWidget.requestFocusInWindow();
			termWidget.start(); // starts the terminal session
		} catch (Exception e) {
			log.error("Failed to start terminal session", e);
			JOptionPane.showMessageDialog(consolePanel, "Failed to start terminal session: " + e.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}

		log.info("ConsolePanel initialized successfully.");
	}

}
