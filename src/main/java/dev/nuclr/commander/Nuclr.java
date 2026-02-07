package dev.nuclr.commander;

import java.io.IOException;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import dev.nuclr.commander.common.AppVersion;
import dev.nuclr.commander.common.Banner;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Nuclr {

	public static void main(String[] args) throws IOException {

		// Print banner
		Banner.printBanner();

		// Print the application version
		log.info("Version: {}", AppVersion.get());
		log.info("Licens: {}", "Apache 2.0");

		var ctx = new AnnotationConfigApplicationContext("dev.nuclr.commander");
		ctx.start();

	}

}
