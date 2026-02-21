package dev.nuclr.commander.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipFile;

import javax.swing.JOptionPane;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.nuclr.commander.plugin.PluginManifest;
import dev.nuclr.commander.ui.common.Alerts;
import dev.nuclr.plugin.NuclrPlugin;
import dev.nuclr.plugin.PluginInfo;
import dev.nuclr.plugin.QuickViewItem;
import dev.nuclr.plugin.QuickViewProvider;
import dev.nuclr.plugin.ViewProvider;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class PluginRegistry {

	@Autowired
	private ObjectMapper objectMapper;

	private final List<NuclrPlugin> loadedPlugins = new ArrayList<>();
	private final List<QuickViewProvider> quickViewProviders = new ArrayList<>();

	public void registerViewProvider(ViewProvider provider) {
		log.info("Registering ViewProvider: [{}]", provider.getClass().getName());
		if (provider instanceof QuickViewProvider qvp) {
			quickViewProviders.add(qvp);
		}
	}

	public QuickViewProvider getQuickViewProviderByItem(QuickViewItem item) {
		return this
				.getQuickViewProviders()
				.stream()
				.filter(p -> p.matches(item))
				.findFirst()
				.orElse(null);
	}

	public List<QuickViewProvider> getQuickViewProvidersByItem(QuickViewItem item) {
		return this
				.getQuickViewProviders()
				.stream()
				.filter(p -> p.matches(item))
				.sorted(Comparator.comparingInt(QuickViewProvider::priority)) // lower first
				.toList();
	}

	public Collection<QuickViewProvider> getQuickViewProviders() {
		return Collections.unmodifiableList(quickViewProviders);
	}

	public void removeViewProvider(ViewProvider provider) {
		log.info("Removing ViewProvider: [{}]", provider.getClass().getName());
		if (provider instanceof QuickViewProvider qvp) {
			quickViewProviders.remove(qvp);
		}
	}

	public void loadPlugin(File zipFile) {

		log.info("Loading plugin: [{}]", zipFile.getAbsolutePath());

		try {
			// Extract ZIP to temp directory
			var pluginDir = Files.createTempDirectory("nuclr-plugin-" + UUID.randomUUID().toString());
			extractZip(zipFile, pluginDir);

			// Read and parse plugin.json
			var manifestFile = pluginDir.resolve("plugin.json");
			if (!Files.exists(manifestFile)) {
				log.error("plugin.json not found in plugin: [{}]", zipFile.getName());
				return;
			}
			var manifest = objectMapper.readValue(manifestFile.toFile(), PluginManifest.class);
			log.info("Plugin manifest: id=[{}], name=[{}], version=[{}]", manifest.getId(), manifest.getName(), manifest.getVersion());

			// Build classloader with plugin JAR + lib/ JARs
			var jarUrls = collectJarUrls(pluginDir);
			if (jarUrls.isEmpty()) {
				log.error("No JAR files found in plugin: [{}]", zipFile.getName());
				return;
			}
			var classLoader = new URLClassLoader(
					jarUrls.toArray(URL[]::new),
					getClass().getClassLoader());

			// Create NuclrPlugin
			var plugin = new NuclrPlugin();
			var info = new PluginInfo();
			info.setName(manifest.getName());
			info.setId(manifest.getId());
			info.setVersion(manifest.getVersion());
			info.setDescription(manifest.getDescription());
			info.setAuthor(manifest.getAuthor());
			info.setLicense(manifest.getLicense());
			info.setWebsite(manifest.getWebsite());
			info.setPageUrl(manifest.getPageUrl());
			info.setDocUrl(manifest.getDocUrl());
			info.setType(manifest.getType());
			plugin.setInfo(info);

			// Load QuickViewProviders
			for (var className : manifest.getQuickViewProviders()) {
				try {
					var clazz = classLoader.loadClass(className);
					var provider = (QuickViewProvider) clazz.getDeclaredConstructor().newInstance();
					plugin.getViewProviders().add(provider);
					quickViewProviders.add(provider);
					log.info("Loaded QuickViewProvider: [{}]", className);
				} catch (Exception e) {
					log.error("Failed to load QuickViewProvider [{}]: {}", className, e.getMessage(), e);
					Alerts.showMessageDialog(null, "Failed to load QuickViewProvider [" + className + "]: " + e.getMessage(), "Plugin Load Error", JOptionPane.ERROR_MESSAGE);
				}
			}

			loadedPlugins.add(plugin);
			log.info("Plugin [{}] loaded successfully with {} provider(s)", manifest.getId(), plugin.getViewProviders().size());

		} catch (IOException e) {
			log.error("Failed to load plugin [{}]: {}", zipFile.getName(), e.getMessage(), e);
		}
	}

	private void extractZip(File zipFile, Path targetDir) throws IOException {
		try (var zip = new ZipFile(zipFile)) {
			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				var entry = entries.nextElement();
				var entryPath = targetDir.resolve(entry.getName());

				// Prevent zip slip
				if (!entryPath.normalize().startsWith(targetDir.normalize())) {
					throw new IOException("Bad zip entry: " + entry.getName());
				}

				if (entry.isDirectory()) {
					Files.createDirectories(entryPath);
				} else {
					Files.createDirectories(entryPath.getParent());
					try (InputStream is = zip.getInputStream(entry)) {
						Files.copy(is, entryPath);
					}
				}
			}
		}
	}

	private List<URL> collectJarUrls(Path pluginDir) throws IOException {
		var urls = new ArrayList<URL>();

		// Root-level JARs
		try (var stream = Files.list(pluginDir)) {
			stream.filter(p -> p.toString().endsWith(".jar"))
					.forEach(p -> {
						try {
							urls.add(p.toUri().toURL());
						} catch (Exception e) {
							log.warn("Failed to convert JAR path to URL: {}", p, e);
						}
					});
		}

		// lib/ JARs
		var libDir = pluginDir.resolve("lib");
		if (Files.isDirectory(libDir)) {
			try (var stream = Files.list(libDir)) {
				stream.filter(p -> p.toString().endsWith(".jar"))
						.forEach(p -> {
							try {
								urls.add(p.toUri().toURL());
							} catch (Exception e) {
								log.warn("Failed to convert JAR path to URL: {}", p, e);
							}
						});
			}
		}

		return urls;
	}

}
