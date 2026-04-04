/*

	Copyright 2026 Sergio, Nuclr (https://nuclr.dev)
	
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	
	http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

*/
package dev.nuclr.commander.plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.nuclr.commander.ui.quickView.PathQuickViewItem;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.plugin.PluginPathResource;
import dev.nuclr.plugin.ResourceContentPlugin;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class PluginRegistry {

	@Autowired
	private ObjectMapper objectMapper;

	private List<ResourceContentPlugin> plugins = new CopyOnWriteArrayList<>();

	@Autowired
	private NuclrPluginContext pluginContext;

	private final List<URLClassLoader> pluginClassLoaders = new CopyOnWriteArrayList<>();

	private final Map<ResourceContentPlugin, PluginDescriptor> pluginDescriptors = new ConcurrentHashMap<>();
	
	@PostConstruct
	public void init() {
	}

	public List<ResourceContentPlugin> getLoadedPlugins() {
		return List.copyOf(plugins);
	}

	public void loadPlugin(File zipFile) {

		log.info("Loading plugin: [{}]", zipFile.getAbsolutePath());

		try {

			var pluginDir = Files.createTempDirectory("nuclr-plugin-" + UUID.randomUUID());
			extractZip(zipFile, pluginDir);

			var manifestFile = pluginDir.resolve("plugin.json");
			var manifest = readPluginDescriptor(manifestFile, zipFile.getName());

			var jarPaths = collectJarPaths(pluginDir);
			if (jarPaths.isEmpty()) {
				log.error("No JAR files found in plugin: [{}]", zipFile.getName());
				return;
			}

			var classLoader = new URLClassLoader(toUrls(jarPaths), getClass().getClassLoader());
			pluginClassLoaders.add(classLoader);

			var classNames = discoverResourceContentPluginClasses(jarPaths, classLoader);
			if (classNames.isEmpty()) {
				log.warn("No ResourceContentPlugin implementations found in plugin: [{}]", zipFile.getName());
				return;
			}

			for (var className : classNames) {
				loadResourceContentProvider(className, classLoader, manifest);
			}

			plugins.sort(Comparator.comparingInt(ResourceContentPlugin::priority));

		} catch (IOException e) {
			log.error("Failed to load plugin [{}]: {}", zipFile.getName(), e.getMessage(), e);
		}
	}

	private void loadResourceContentProvider(String className, URLClassLoader classLoader, PluginDescriptor manifest) {

		
		
	}

	private PluginDescriptor readPluginDescriptor(Path manifestFile, String pluginName) throws IOException {
		if (!Files.exists(manifestFile)) {
			log.warn("plugin.json not found in plugin: [{}]", pluginName);
			return null;
		}
		PluginDescriptor manifest = objectMapper.readValue(manifestFile.toFile(), PluginDescriptor.class);
		log.info("Plugin manifest: id=[{}], name=[{}], version=[{}]", manifest.getId(), manifest.getName(),
				manifest.getVersion());
		return manifest;
	}

	private List<Path> collectJarPaths(Path pluginDir) throws IOException {
		List<Path> jarPaths = new ArrayList<>();
		try (Stream<Path> stream = Files.list(pluginDir)) {
			stream.filter(path -> path.toString().endsWith(".jar")).forEach(jarPaths::add);
		}

		Path libDir = pluginDir.resolve("lib");
		if (Files.isDirectory(libDir)) {
			try (Stream<Path> stream = Files.list(libDir)) {
				stream.filter(path -> path.toString().endsWith(".jar")).forEach(jarPaths::add);
			}
		}
		return jarPaths;
	}

	private URL[] toUrls(List<Path> jarPaths) {
		return jarPaths.stream().map(this::toUrl).filter(java.util.Objects::nonNull).toArray(URL[]::new);
	}

	private URL toUrl(Path path) {
		try {
			return path.toUri().toURL();
		} catch (Exception e) {
			log.warn("Failed to convert JAR path to URL: {}", path, e);
			return null;
		}
	}

	private List<String> discoverResourceContentPluginClasses(List<Path> jarPaths, ClassLoader classLoader) {
		List<String> classNames = new ArrayList<>();
		for (Path jarPath : jarPaths) {
			try (JarFile jarFile = new JarFile(jarPath.toFile())) {
				Enumeration<JarEntry> entries = jarFile.entries();
				while (entries.hasMoreElements()) {
					JarEntry entry = entries.nextElement();
					if (!isCandidateClassEntry(entry)) {
						continue;
					}
					String className = toClassName(entry.getName());
					if (isConcreteResourceContentPlugin(className, classLoader)) {
						classNames.add(className);
					}
				}
			} catch (IOException e) {
				log.warn("Failed to scan plugin JAR [{}]: {}", jarPath, e.getMessage());
			}
		}
		return classNames.stream().distinct().sorted().collect(Collectors.toList());
	}

	private boolean isCandidateClassEntry(JarEntry entry) {
		return !entry.isDirectory() && entry.getName().endsWith(".class")
				&& !entry.getName().equals("module-info.class") && !entry.getName().endsWith("package-info.class")
				&& !entry.getName().contains("$");
	}

	private String toClassName(String entryName) {
		return entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.');
	}

	private boolean isConcreteResourceContentPlugin(String className, ClassLoader classLoader) {
		try {
			Class<?> clazz = Class.forName(className, false, classLoader);
			int modifiers = clazz.getModifiers();
			return ResourceContentPlugin.class.isAssignableFrom(clazz) && !clazz.isInterface()
					&& !Modifier.isAbstract(modifiers);
		} catch (LinkageError | ReflectiveOperationException e) {
			log.debug("Skipping plugin class [{}]: {}", className, e.getMessage());
			return false;
		}
	}

	private String simpleName(String className) {
		int lastDot = className.lastIndexOf('.');
		return lastDot >= 0 ? className.substring(lastDot + 1) : className;
	}

	private void extractZip(File zipFile, Path targetDir) throws IOException {
		try (ZipFile zip = new ZipFile(zipFile)) {
			var entries = zip.entries();
			while (entries.hasMoreElements()) {
				var entry = entries.nextElement();
				Path entryPath = targetDir.resolve(entry.getName());
				if (!entryPath.normalize().startsWith(targetDir.normalize())) {
					throw new IOException("Bad zip entry: " + entry.getName());
				}

				if (entry.isDirectory()) {
					Files.createDirectories(entryPath);
					continue;
				}

				Files.createDirectories(entryPath.getParent());
				try (InputStream is = zip.getInputStream(entry)) {
					Files.copy(is, entryPath);
				}
			}
		}
	}

	public PluginDescriptor getPluginDescriptors(ResourceContentPlugin plugin) {
		return this.pluginDescriptors.get(plugin);
	}

	public List<ResourceContentPlugin> getPluginByItem(PathQuickViewItem item) {
		// TODO Auto-generated method stub
		return null;
	}

	public ResourceContentPlugin createPanelProviderInstance(ResourceContentPlugin template) {
		try {
			return template.getClass().newInstance();
		} catch (Exception e) {
			log.error("Failed to create plugin instance: {}", e.getMessage(), e);
		}
		return null;
	}

	public ResourceContentPlugin getPluginByResource(PluginPathResource resource) {
		return this.plugins.stream().filter(plugin -> plugin.supports(resource)).findFirst().orElse(null);
	}

	public ResourceContentPlugin getPluginById(String string) {
		return this.plugins.stream().filter(plugin -> {
			var descriptor = this.pluginDescriptors.get(plugin);
			return descriptor != null && descriptor.getId().equals(string);
		}).findFirst().orElse(null);
	}

}
