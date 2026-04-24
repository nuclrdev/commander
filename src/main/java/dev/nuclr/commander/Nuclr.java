package dev.nuclr.commander;

import java.io.IOException;
import java.lang.management.ManagementFactory;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import dev.nuclr.commander.common.MacOSIntegration;
import dev.nuclr.commander.common.SystemUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Nuclr {

	static AnnotationConfigApplicationContext ctx;

	public static void main(String[] args) throws IOException {
		
		writeIntroInfo();

		// Print the application version
		log.info("License: {}", "Apache 2.0");

		if (SystemUtils.isOsMac()) {
			MacOSIntegration.installAboutHandler();
		}

		ctx = new AnnotationConfigApplicationContext("dev.nuclr.commander");
		ctx.start();

	}

	private static void writeIntroInfo() {

		// Log jvm options and java properties
		log.info("JVM Options:");
		System.getProperties().forEach((key, value) -> {
			log.info("JVM Option: {} = {}", key, value);
		});
		
		// All JVM input arguments (-X, -D, etc.)
		var runtimeMxBean = ManagementFactory.getRuntimeMXBean();
		var args = runtimeMxBean.getInputArguments();
		args.forEach(v-> log.info("JVM Argument: {}", v));

		// Specific memory values
		Runtime rt = Runtime.getRuntime();
		log.info("Max heap: " + rt.maxMemory() / 1024 / 1024 + "MB");
		log.info("Total heap: " + rt.totalMemory() / 1024 / 1024 + "MB");
		log.info("Free heap: " + rt.freeMemory() / 1024 / 1024 + "MB");		
		
	}

	public static void exit() {
		log.info("Shutting down the application...");
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
