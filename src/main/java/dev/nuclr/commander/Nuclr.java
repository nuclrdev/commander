package dev.nuclr.commander;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutorService;

import com.google.inject.Guice;
import com.google.inject.Injector;

import dev.nuclr.commander.common.MacOSIntegration;
import dev.nuclr.commander.common.SystemUtils;
import dev.nuclr.commander.config.CommanderModule;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Nuclr {

	static Injector injector;

	public static long startTime = ManagementFactory.getRuntimeMXBean().getStartTime();
	
	public static void main(String[] args) throws IOException {

		// Print the application version
		log.info("License: {}", "Apache 2.0");

		if (SystemUtils.isOsMac()) {
			MacOSIntegration.installAboutHandler();
		}

		injector = Guice.createInjector(new CommanderModule());

	}

	public static void exit() {
		try {
			if (injector != null) {
				injector.getInstance(ExecutorService.class).shutdownNow();
			}
		} catch (Exception e) {
			log.error("Error while shutting down the application: {}", e.getMessage(), e);
			System.exit(1);
			return;
		}
		System.exit(0);
	}

}
