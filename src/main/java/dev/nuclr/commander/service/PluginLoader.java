package dev.nuclr.commander.service;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ServiceLoader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.nuclr.plugin.Plugin;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class PluginLoader {

	@Autowired
	private TaskExecutor taskExecutor;

	@Autowired
	private ObjectMapper objectMapper;

	@Value("${plugins.folder}")
	private String pluginsDirectory;

	@PostConstruct
	public void init() {
		taskExecutor.execute(new Runnable() {
			@Override
			public void run() {
				loadPlugins();
			}
		});
	}

	protected void loadPlugins() {
		log.info("Loading plugins from directory: [{}]", pluginsDirectory);
		loadCorePlugins("plugins/core");
	}

	private void loadCorePlugins(String pluginsFolder) {

		var folders = new java.io.File(pluginsFolder).listFiles(java.io.File::isDirectory);

		if (folders != null) {

			for (var folder : folders) {

				log.info("Loading core plugin: [{}]", folder.getAbsolutePath());
				
				var jar = folder.listFiles()[0];

				try (var cl = new URLClassLoader(
						new URL[] { jar.toURI().toURL() },
						this.getClass().getClassLoader())) {

					var json = IOUtils
							.toString(
									cl.getResourceAsStream("plugin.json"),
									StandardCharsets.UTF_8);

					var pluginInfo = objectMapper.readValue(json, Plugin.class);

					log.info("Plugin info: [{}]", pluginInfo);

					var loader = ServiceLoader.load(Plugin.class, cl);

					for (var plugin : loader) {
						log.info("Initializing plugin: [{}]", plugin.getName());
						// plugin.initialize();
					}

				} catch (Exception e) {
					log.error("Failed to load plugin from folder: [{}]", folder.getName(), e);
				}

			}
		}

	}

}
