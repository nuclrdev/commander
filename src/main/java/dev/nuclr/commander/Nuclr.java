package dev.nuclr.commander;

import java.io.IOException;

import javax.swing.SwingUtilities;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Nuclr {

	public static void main(String[] args) throws IOException {

		// Print the application version
		log.info("License: {}", "Apache 2.0");

		SwingUtilities.invokeLater(() -> {
			var ctx = new AnnotationConfigApplicationContext("dev.nuclr.commander");
			ctx.start();
		});

	}

}
