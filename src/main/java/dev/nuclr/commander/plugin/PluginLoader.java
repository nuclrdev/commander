package dev.nuclr.commander.plugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executor;

import javax.swing.JOptionPane;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.nuclr.commander.common.IOUtils;
import dev.nuclr.commander.config.AppProperties;
import dev.nuclr.commander.ui.common.Alerts;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class PluginLoader {

	private final Executor taskExecutor;

	private final ObjectMapper objectMapper;

	private final String corePluginsDirectory;

	private final PluginRegistry pluginRegistry;

	@Inject
	public PluginLoader(
			Executor taskExecutor,
			ObjectMapper objectMapper,
			AppProperties appProperties,
			PluginRegistry pluginRegistry) {
		this.taskExecutor = taskExecutor;
		this.objectMapper = objectMapper;
		this.corePluginsDirectory = appProperties.getRequired("core.plugins.folder");
		this.pluginRegistry = pluginRegistry;
		taskExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					loadPlugins();
				} catch (IOException e) {
					log.error("Failed to load plugins: {}", e.getMessage(), e);
				}
			}
		});
	}

	protected void loadPlugins() throws IOException {

		var pluginsFolder = new File(".", corePluginsDirectory);

		log.info("Loading plugins from directory: [{}]", pluginsFolder);

		var publicKey = IOUtils.toByteArray(this.getClass().getResourceAsStream("/dev/nuclr/key/nuclr-cert.pem"));

		Arrays
				.stream(pluginsFolder.listFiles(File::isFile))
				.filter(file -> file.getName().endsWith("zip"))
				.forEach(file -> {

					log.info("Loading core plugin: [{}]", file.getName());

					var sigFile = new File(file.getAbsolutePath() + ".sig");

					if (false == sigFile.exists()) {
						log
								.warn(
										"Plugin signature file not found for plugin: [{}]",
										file.getName());
						Alerts
								.showMessageDialog(
										null,
										"Plugin signature file not found for plugin: "
												+ file.getName(),
										"Plugin Load Error",
										JOptionPane.ERROR_MESSAGE);
						return;
					}

					try {
						var valid = ZipVerifier.verify(file, sigFile, publicKey);
						if (valid) {
							log.info("Plugin [{}] verified successfully", file.getName());
							pluginRegistry.loadPlugin(file);
						} else {
							log.warn("Invalid plugin signature for plugin: [{}]", file.getName());
							Alerts
									.showMessageDialog(
											null,
											"Invalid plugin signature for plugin: "
													+ file.getName(),
											"Plugin Load Error",
											JOptionPane.ERROR_MESSAGE);
						}
					} catch (Exception e) {
						log
								.error(
										"Failed to verify plugin [{}]: {}",
										file.getName(),
										e.getMessage(),
										e);
						Alerts
								.showMessageDialog(
										null,
										"Failed to verify plugin: " + file.getName(),
										"Plugin Load Error",
										JOptionPane.ERROR_MESSAGE);
					}

				});

	}

}
