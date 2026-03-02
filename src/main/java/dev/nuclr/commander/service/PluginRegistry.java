package dev.nuclr.commander.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
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

import dev.nuclr.commander.panel.FilePanelProviderRegistry;
import dev.nuclr.commander.plugin.PluginManifest;
import dev.nuclr.commander.ui.common.Alerts;
import dev.nuclr.commander.vfs.ArchiveMountProviderRegistry;
import dev.nuclr.plugin.NuclrPlugin;
import dev.nuclr.plugin.PluginInfo;
import dev.nuclr.plugin.QuickViewItem;
import dev.nuclr.plugin.QuickViewProvider;
import dev.nuclr.plugin.ViewProvider;
import dev.nuclr.plugin.mount.ArchiveMountProvider;
import dev.nuclr.plugin.panel.FilePanelProvider;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class PluginRegistry {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private FilePanelProviderRegistry filePanelProviderRegistry;

	@Autowired
	private ArchiveMountProviderRegistry archiveMountProviderRegistry;

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
		var matches = this
				.getQuickViewProviders()
				.stream()
				.filter(p -> p.matches(item))
				.sorted(Comparator.comparingInt(QuickViewProvider::priority)) // lower first
				.toList();

		if (!matches.isEmpty()) {
			return matches;
		}

		if (!item.extension().isBlank() || !isLikelyText(item)) {
			return matches;
		}

		return this
				.getQuickViewProviders()
				.stream()
				.filter(p -> p.getClass().getSimpleName().equals("TextQuickViewProvider"))
				.sorted(Comparator.comparingInt(QuickViewProvider::priority))
				.toList();
	}

	public Collection<QuickViewProvider> getQuickViewProviders() {
		return Collections.unmodifiableList(quickViewProviders);
	}

	public List<NuclrPlugin> getLoadedPlugins() {
		return Collections.unmodifiableList(loadedPlugins);
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

			// Load FilePanelProviders
			for (var className : manifest.getFilePanelProviders()) {
				try {
					var clazz = classLoader.loadClass(className);
					var provider = (FilePanelProvider) clazz.getDeclaredConstructor().newInstance();
					filePanelProviderRegistry.registerProvider(provider);
					log.info("Loaded FilePanelProvider: [{}]", className);
				} catch (Exception e) {
					log.error("Failed to load FilePanelProvider [{}]: {}", className, e.getMessage(), e);
					Alerts.showMessageDialog(null, "Failed to load FilePanelProvider [" + className + "]: " + e.getMessage(), "Plugin Load Error", JOptionPane.ERROR_MESSAGE);
				}
			}

			// Load ArchiveMountProviders
			for (var className : manifest.getArchiveMountProviders()) {
				try {
					var clazz = classLoader.loadClass(className);
					var provider = (ArchiveMountProvider) clazz.getDeclaredConstructor().newInstance();
					archiveMountProviderRegistry.registerProvider(provider);
					log.info("Loaded ArchiveMountProvider: [{}]", className);
				} catch (Exception e) {
					log.error("Failed to load ArchiveMountProvider [{}]: {}", className, e.getMessage(), e);
					Alerts.showMessageDialog(null, "Failed to load ArchiveMountProvider [" + className + "]: " + e.getMessage(), "Plugin Load Error", JOptionPane.ERROR_MESSAGE);
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

	private boolean isLikelyText(QuickViewItem item) {
		final int sampleSize = 4096;
		byte[] buffer = new byte[sampleSize];

		try (InputStream in = item.openStream()) {
			int read = in.read(buffer);
			if (read <= 0) {
				return true;
			}

			int suspicious = 0;
			for (int i = 0; i < read; i++) {
				int b = buffer[i] & 0xFF;
				if (b == 0) {
					return false;
				}
				if (b < 0x20 && b != '\n' && b != '\r' && b != '\t' && b != '\f') {
					suspicious++;
				}
			}

			if (suspicious == 0) {
				return true;
			}

			String sample = new String(buffer, 0, read, StandardCharsets.UTF_8);
			if (sample.indexOf('\uFFFD') >= 0) {
				return false;
			}

			return suspicious * 20 < read;
		} catch (Exception e) {
			log.debug("Failed to inspect quick-view item [{}]: {}", item.name(), e.getMessage());
			return false;
		}
	}

}
