package dev.nuclr.commander;

import java.io.IOException;

import javax.swing.SwingUtilities;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import dev.nuclr.commander.common.MacOSIntegration;
import dev.nuclr.commander.common.SystemUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Nuclr {

	static AnnotationConfigApplicationContext ctx;

	public static void main(String[] args) throws IOException {

		// Print the application version
		log.info("License: {}", "Apache 2.0");

		if (SystemUtils.isOsMac()) {
			MacOSIntegration.installAboutHandler();
		}

		SwingUtilities.invokeLater(() -> {
			ctx = new AnnotationConfigApplicationContext("dev.nuclr.commander");
			ctx.start();
		});

	}

	public static void exit() {
		try {
			ctx.stop();
			ctx.close();
		} catch (Exception e) {
			log.error("Error while shutting down the application: {}", e.getMessage(), e);
			// Optionally, you can choose to exit with a non-zero status code to indicate an error
			System.exit(1);
			return;
		}
		System.exit(0);
	}

}
