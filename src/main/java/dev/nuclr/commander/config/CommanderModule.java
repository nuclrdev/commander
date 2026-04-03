package dev.nuclr.commander.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import dev.nuclr.commander.event.AppEventBus;
import dev.nuclr.commander.plugin.DefaultApplicationPluginContext;
import dev.nuclr.commander.plugin.DefaultPluginEventBus;
import dev.nuclr.commander.plugin.PluginLoader;
import dev.nuclr.commander.service.OpenFileWithDefaultProgram;
import dev.nuclr.commander.service.SystemSettings;
import dev.nuclr.commander.ui.main.MainWindow;
import dev.nuclr.platform.Settings;
import dev.nuclr.platform.events.NuclrEventBus;
import dev.nuclr.platform.plugin.NuclrPluginContext;

public final class CommanderModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(AppProperties.class).in(Singleton.class);
		bind(AppEventBus.class).in(Singleton.class);

		bind(DefaultPluginEventBus.class).in(Singleton.class);
		bind(NuclrEventBus.class).to(DefaultPluginEventBus.class);

		bind(SystemSettings.class).in(Singleton.class);
		bind(Settings.class).to(SystemSettings.class);

		bind(DefaultApplicationPluginContext.class).in(Singleton.class);
		bind(NuclrPluginContext.class).to(DefaultApplicationPluginContext.class);

		bind(OpenFileWithDefaultProgram.class).asEagerSingleton();
		bind(PluginLoader.class).asEagerSingleton();
		bind(MainWindow.class).asEagerSingleton();
	}

	@Provides
	@Singleton
	ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		return mapper;
	}

	@Provides
	@Singleton
	ExecutorService executorService() {
		return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("tasks-", 0).factory());
	}

	@Provides
	@Singleton
	Executor executor(ExecutorService executorService) {
		return executorService;
	}
}
