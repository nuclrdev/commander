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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.nuclr.commander.ui.quickView.PathQuickViewItem;
import dev.nuclr.platform.NuclrThemeScheme;
import dev.nuclr.platform.plugin.NuclrPlugin;
import dev.nuclr.platform.plugin.NuclrPluginContext;
import dev.nuclr.platform.plugin.NuclrPluginRole;
import dev.nuclr.platform.plugin.NuclrResourcePath;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class PluginRegistry {

	/**
	 * This contains a plugin that was constructed but might not be fully intialized
	 * yet. It is only used for info, and creating/initialising the proper working
	 * plugin instance
	 */
	private List<NuclrPlugin> pluginTemplates = new CopyOnWriteArrayList<>();

	@Autowired
	private NuclrPluginContext pluginContext;

	private final List<URLClassLoader> pluginClassLoaders = new CopyOnWriteArrayList<>();

	private Map<String, NuclrPlugin> pluginInstanceCache = new ConcurrentHashMap<>();
	
	@PostConstruct
	public void init() {
	}
	
	@PreDestroy
	public void destroy() {
		
		// Close all plugin class loaders to release file locks on Windows
		for (var classLoader : pluginClassLoaders) {
			try {
				classLoader.close();
			} catch (IOException e) {
				log.warn("Failed to close plugin class loader: {}", e.getMessage(), e);
			}
		}
		
		pluginInstanceCache.forEach((id, plugin) -> {
			try {
				log.info("Unloading plugin instance [{}]", id);
				plugin.unload();
			} catch (Exception e) {
				log.warn("Failed to unload plugin instance [{}]: {}", id, e.getMessage(), e);
			}
		});
		
	}

	public void loadPlugin(File zipFile) {

		log.info("Loading plugin: [{}]", zipFile.getAbsolutePath());

		try {

			var pluginDir = Files.createTempDirectory("nuclr-plugin-" + UUID.randomUUID());
			extractZip(zipFile, pluginDir);

			var jarPaths = collectJarPaths(pluginDir);
			if (jarPaths.isEmpty()) {
				log.error("No JAR files found in plugin: [{}]", zipFile.getName());
				return;
			}

			var classLoader = new URLClassLoader(toUrls(jarPaths), getClass().getClassLoader());
			pluginClassLoaders.add(classLoader);

			var classNames = discoverNuclrPluginClasses(jarPaths, classLoader);
			if (classNames.isEmpty()) {
				log.warn("No NuclrPlugin implementations found in plugin: [{}]", zipFile.getName());
				return;
			}

			for (var className : classNames) {
				loadResourceContentProvider(className, classLoader);
			}

			pluginTemplates.sort(Comparator.comparingInt(NuclrPlugin::priority));

		} catch (IOException e) {
			log.error("Failed to load plugin [{}]: {}", zipFile.getName(), e.getMessage(), e);
		}
	}

	private void loadResourceContentProvider(String className, URLClassLoader classLoader) {

		try {
			Class<?> rawClass = Class.forName(className, true, classLoader);
			if (!NuclrPlugin.class.isAssignableFrom(rawClass)) {
				log.warn("Skipping non-NuclrPlugin class [{}]", className);
				return;
			}

			Class<? extends NuclrPlugin> pluginClass = rawClass.asSubclass(NuclrPlugin.class);
			NuclrPlugin plugin = pluginClass.getDeclaredConstructor().newInstance();
			plugin.load(pluginContext, true);
			pluginTemplates.add(plugin);

			log.info("Loaded resource content provider [{}] with priority {}", plugin.id(), plugin.priority());

		} catch (Exception e) {
			log.error("Failed to load resource content provider [{}]: {}", className, e.getMessage(), e);
		}
	}

	private List<Path> collectJarPaths(Path pluginDir) throws IOException {

		var jarPaths = new ArrayList<Path>();

		try (var stream = Files.list(pluginDir)) {
			stream.filter(path -> path.toString().endsWith(".jar")).forEach(jarPaths::add);
		}

		var libDir = pluginDir.resolve("lib");
		if (Files.isDirectory(libDir)) {
			try (var stream = Files.list(libDir)) {
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

	private List<String> discoverNuclrPluginClasses(List<Path> jarPaths, ClassLoader classLoader) {

		var classNames = new ArrayList<String>();

		for (var jarPath : jarPaths) {

			try (var jarFile = new JarFile(jarPath.toFile())) {

				var entries = jarFile.entries();

				while (entries.hasMoreElements()) {

					var entry = entries.nextElement();

					if (!isCandidateClassEntry(entry)) {
						continue;
					}

					var className = toClassName(entry.getName());

					if (isConcreteNuclrPlugin(className, classLoader)) {
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

	private boolean isConcreteNuclrPlugin(String className, ClassLoader classLoader) {
		try {
			Class<?> clazz = Class.forName(className, false, classLoader);
			int modifiers = clazz.getModifiers();
			return NuclrPlugin.class.isAssignableFrom(clazz) && !clazz.isInterface() && !Modifier.isAbstract(modifiers);
		} catch (LinkageError | ReflectiveOperationException e) {
			log.debug("Skipping plugin class [{}]: {}", className, e.getMessage());
			return false;
		}
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

	public NuclrPlugin getPluginByItem(PathQuickViewItem item, NuclrPluginRole role) {
		return pluginTemplates.stream()
				.filter(plugin -> plugin.supports(item))
				.filter(plugin -> plugin.role().equals(role))
				.sorted(Comparator.comparingInt(NuclrPlugin::priority))
				.map(plugin -> getPluginInstance(plugin.id()))
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	public NuclrPlugin getPluginByResource(NuclrResourcePath resource, NuclrPluginRole role) {
		return this
				.pluginTemplates
				.stream()
				.filter(plugin -> plugin.supports(resource))
				.filter(plugin -> plugin.role().equals(role))
				.findFirst()
				.orElse(null);
	}

	public NuclrPlugin getPluginTemplateById(String id) {
		return this
				.pluginTemplates
				.stream()
				.filter(plugin -> {
					return plugin.id().equals(id);
				})
				.findFirst()
				.orElse(null);
	}

	public List<NuclrPlugin> getPluginTemplates() {
		return List.copyOf(pluginTemplates);
	}
	
	public NuclrPlugin getPluginInstance(String id) {
		
		var template = pluginTemplates
				.stream()
				.filter(plugin -> {
					
					if (plugin.id() == null) {
						log.error("Plugin [{}] has null id, skipping", plugin.getClass().getName());
						return false;
					}
					
					return plugin.id().equals(id);
				})
				.findFirst()
				.orElse(null);
		
		if (template == null) {
			log.warn("No plugin template found with id: {}", id);
			return null;
		}
		
		if (pluginInstanceCache.containsKey(id)) {
			log.debug("Returning cached plugin instance for id: {}", id);
			return pluginInstanceCache.get(id);
		}
		
		try {
			var instance = template.getClass().getDeclaredConstructor().newInstance();
			instance.load(pluginContext, false);
			
			if (instance.singleton()) {
				pluginInstanceCache.put(id, instance);
				log.debug("Cached singleton plugin instance for id: {}", id);
			}
			
			return instance;
			
		} catch (Exception e) {
			log.error("Failed to create plugin instance for id [{}]: {}", id, e.getMessage(), e);
			return null;
		}
	}

	public void unloadSingletonPluginInstance(String uuid) {
		
		var matchedPlugin = pluginInstanceCache.values().stream()
				.filter(plugin -> plugin.id().equals(uuid))
				.findFirst()
				.orElse(null);
		
		if (matchedPlugin == null) {
			log.warn("No loaded plugin instance found with id: {}", uuid);
			return;
		}
		
		try {
			matchedPlugin.unload();
			pluginInstanceCache.remove(uuid);
			log.info("Unloaded singleton lugin instance with id: {}", uuid);
		} catch (Exception e) {
			log.info("Failed to unload plugin instance with id [{}]: {}", uuid, e.getMessage(), e);
		}
				
	}

	public void broadcastThemeUpdate(NuclrThemeScheme themeScheme) {
		pluginTemplates.forEach(plugin -> applyThemeUpdate(plugin, themeScheme));
		pluginInstanceCache.values().forEach(plugin -> applyThemeUpdate(plugin, themeScheme));
	}

	private void applyThemeUpdate(NuclrPlugin plugin, NuclrThemeScheme themeScheme) {
		if (plugin == null || themeScheme == null) {
			return;
		}
		try {
			plugin.updateTheme(themeScheme);
		} catch (Exception e) {
			log.warn("Failed to update theme for plugin [{}]: {}", plugin.id(), e.getMessage(), e);
		}
	}

}
