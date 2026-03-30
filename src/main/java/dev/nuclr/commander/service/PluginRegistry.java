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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipFile;

import javax.swing.JOptionPane;
import javax.swing.JComponent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.nuclr.commander.plugin.PluginDescriptor;
import dev.nuclr.commander.ui.common.Alerts;
import dev.nuclr.platform.Settings;
import dev.nuclr.platform.events.NuclrEventListener;
import dev.nuclr.plugin.ApplicationPluginContext;
import dev.nuclr.plugin.BasePlugin;
import dev.nuclr.plugin.FocusablePlugin;
import dev.nuclr.plugin.MenuResource;
import dev.nuclr.plugin.PanelProviderPlugin;
import dev.nuclr.plugin.PluginManifest;
import dev.nuclr.plugin.PluginPathResource;
import dev.nuclr.plugin.QuickViewProviderPlugin;
import dev.nuclr.plugin.ResourceContentPlugin;
import dev.nuclr.plugin.ScreenProviderPlugin;
import dev.nuclr.plugin.event.PluginEvent;
import dev.nuclr.plugin.event.bus.PluginEventBus;
import dev.nuclr.plugin.event.bus.PluginEventListener;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class PluginRegistry {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired(required = false)
	private List<PanelProviderPlugin> springPanelProviders;

	private final ApplicationPluginContext pluginContext = new DefaultApplicationPluginContext();
	private final List<BasePlugin> loadedPlugins = new CopyOnWriteArrayList<>();
	private final List<PanelProviderPlugin> panelProviders = new CopyOnWriteArrayList<>();
	private final List<Class<? extends PanelProviderPlugin>> panelProviderTypes = new CopyOnWriteArrayList<>();
	private final List<QuickViewProviderPlugin> quickViewProviders = new CopyOnWriteArrayList<>();
	private final List<ScreenProviderPlugin> screenProviders = new CopyOnWriteArrayList<>();
	private final List<URLClassLoader> pluginClassLoaders = new CopyOnWriteArrayList<>();
	private final ConcurrentHashMap<ScreenProviderPlugin, PluginDescriptor> screenProviderInfo = new ConcurrentHashMap<>();

	@PostConstruct
	public void init() {
		if (springPanelProviders == null) {
			return;
		}
		for (PanelProviderPlugin provider : springPanelProviders) {
			try {
				provider.load(pluginContext);
				panelProviders.add(provider);
				loadedPlugins.add(provider);
				log.info("Registered built-in panel provider: [{}]", provider.getClass().getName());
			} catch (Exception e) {
				log.error("Failed to initialize built-in panel provider [{}]: {}", provider.getClass().getName(),
						e.getMessage(), e);
			}
		}
	}

	public List<PanelProviderPlugin> getPanelProviders() {
		return List.copyOf(panelProviders);
	}

	public PanelProviderPlugin createPanelProviderInstance(PanelProviderPlugin template) {
		try {
			if (template instanceof ResourceContentPanelProviderAdapter adapterTemplate) {
				return adapterTemplate.newInstance();
			}
			@SuppressWarnings("unchecked")
			Class<? extends PanelProviderPlugin> type = (Class<? extends PanelProviderPlugin>) template.getClass();
			PanelProviderPlugin plugin = type.getDeclaredConstructor().newInstance();
			plugin.load(pluginContext);
			return plugin;
		} catch (Exception e) {
			throw new IllegalStateException(
					"Failed to create panel provider instance for " + template.getClass().getName(), e);
		}
	}

	public Collection<QuickViewProviderPlugin> getQuickViewProviders() {
		return Collections.unmodifiableList(quickViewProviders);
	}

	public List<QuickViewProviderPlugin> getQuickViewProvidersByItem(PluginPathResource item) {
		var matches = quickViewProviders.stream().filter(provider -> provider.supports(item))
				.sorted(Comparator.comparingInt(QuickViewProviderPlugin::getPriority)).toList();

		if (!matches.isEmpty()) {
			return matches;
		}

		String extension = item.getExtension() == null ? "" : item.getExtension();
		if (!extension.isBlank() || !isLikelyText(item)) {
			return matches;
		}

		return quickViewProviders.stream()
				.filter(provider -> provider.getClass().getSimpleName().equals("TextQuickViewProvider"))
				.sorted(Comparator.comparingInt(QuickViewProviderPlugin::getPriority)).toList();
	}

	public ScreenProviderPlugin getScreenProviderByPath(PluginPathResource path) {
		return screenProviders.stream().filter(provider -> provider.supports(path)).findFirst().orElse(null);
	}

	public List<BasePlugin> getLoadedPlugins() {
		return List.copyOf(loadedPlugins);
	}

	public PluginDescriptor getPluginInfoByScreenProvider(ScreenProviderPlugin provider) {
		return provider == null ? null : screenProviderInfo.get(provider);
	}

	public ApplicationPluginContext getPluginContext() {
		return pluginContext;
	}

	public void loadPlugin(File zipFile) {
		log.info("Loading plugin: [{}]", zipFile.getAbsolutePath());

		try {
			Path pluginDir = Files.createTempDirectory("nuclr-plugin-" + UUID.randomUUID());
			extractZip(zipFile, pluginDir);

			Path manifestFile = pluginDir.resolve("plugin.json");
			if (!Files.exists(manifestFile)) {
				log.error("plugin.json not found in plugin: [{}]", zipFile.getName());
				return;
			}

			PluginDescriptor manifest = objectMapper.readValue(manifestFile.toFile(), PluginDescriptor.class);
			log.info("Plugin manifest: id=[{}], name=[{}], version=[{}]", manifest.getId(), manifest.getName(),
					manifest.getVersion());

			List<URL> jarUrls = collectJarUrls(pluginDir);
			if (jarUrls.isEmpty()) {
				log.error("No JAR files found in plugin: [{}]", zipFile.getName());
				return;
			}

			URLClassLoader classLoader = new URLClassLoader(jarUrls.toArray(URL[]::new), getClass().getClassLoader());
			pluginClassLoaders.add(classLoader);
			loadPanelProviders(manifest.getPanelProviders(), classLoader);
			loadProviders(manifest.getQuickViewProviders(), classLoader, QuickViewProviderPlugin.class,
					quickViewProviders);
			loadScreenProviders(manifest.getScreenProviders(), classLoader, manifest);
		} catch (IOException e) {
			log.error("Failed to load plugin [{}]: {}", zipFile.getName(), e.getMessage(), e);
		}
	}

	private void loadPanelProviders(List<String> classNames, ClassLoader classLoader) {
		for (String className : classNames) {
			try {
				Class<?> clazz = classLoader.loadClass(className);
				PanelProviderPlugin plugin = null;
				if (PanelProviderPlugin.class.isAssignableFrom(clazz)) {
					@SuppressWarnings("unchecked")
					Class<? extends PanelProviderPlugin> type = (Class<? extends PanelProviderPlugin>) clazz
							.asSubclass(PanelProviderPlugin.class);
					plugin = type.getDeclaredConstructor().newInstance();
					panelProviderTypes.add(type);
				} else if (ResourceContentPlugin.class.isAssignableFrom(clazz)) {
					@SuppressWarnings("unchecked")
					Class<? extends ResourceContentPlugin> type = (Class<? extends ResourceContentPlugin>) clazz
							.asSubclass(ResourceContentPlugin.class);
					plugin = new ResourceContentPanelProviderAdapter(type, pluginContext);
				}
				if (plugin == null) {
					throw new IllegalStateException("Unsupported panel provider contract: " + clazz.getName());
				}
				panelProviders.add(plugin);
				loadedPlugins.add(plugin);
				log.info("Loaded panel provider: [{}]", className);
			} catch (Exception e) {
				log.error("Failed to load panel provider [{}]: {}", className, e.getMessage(), e);
				Alerts.showMessageDialog(null, "Failed to load panel provider [" + className + "]: " + e.getMessage(),
						"Plugin Load Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private <T extends BasePlugin> void loadProviders(List<String> classNames, ClassLoader classLoader,
			Class<T> expectedType, List<T> target) {

		for (String className : classNames) {
			try {
				Class<?> clazz = classLoader.loadClass(className);
				T plugin = expectedType.cast(clazz.getDeclaredConstructor().newInstance());
				plugin.load(pluginContext);
				target.add(plugin);
				loadedPlugins.add(plugin);
				log.info("Loaded plugin provider: [{}]", className);
			} catch (Exception e) {
				log.error("Failed to load plugin provider [{}]: {}", className, e.getMessage(), e);
				Alerts.showMessageDialog(null, "Failed to load plugin provider [" + className + "]: " + e.getMessage(),
						"Plugin Load Error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	private void loadScreenProviders(List<String> classNames, ClassLoader classLoader, PluginDescriptor manifest) {
		for (String className : classNames) {
			try {
				Class<?> clazz = classLoader.loadClass(className);
				ScreenProviderPlugin plugin = ScreenProviderPlugin.class
						.cast(clazz.getDeclaredConstructor().newInstance());
				plugin.load(pluginContext);
				screenProviders.add(plugin);
				loadedPlugins.add(plugin);
				screenProviderInfo.put(plugin, manifest);
				log.info("Loaded screen provider: [{}]", className);
			} catch (Exception e) {
				log.error("Failed to load screen provider [{}]: {}", className, e.getMessage(), e);
				Alerts.showMessageDialog(null, "Failed to load screen provider [" + className + "]: " + e.getMessage(),
						"Plugin Load Error", JOptionPane.ERROR_MESSAGE);
			}
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

	private List<URL> collectJarUrls(Path pluginDir) throws IOException {
		List<URL> urls = new ArrayList<>();

		try (var stream = Files.list(pluginDir)) {
			stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> addJarUrl(urls, path));
		}

		Path libDir = pluginDir.resolve("lib");
		if (Files.isDirectory(libDir)) {
			try (var stream = Files.list(libDir)) {
				stream.filter(path -> path.toString().endsWith(".jar")).forEach(path -> addJarUrl(urls, path));
			}
		}

		return urls;
	}

	private void addJarUrl(List<URL> urls, Path path) {
		try {
			urls.add(path.toUri().toURL());
		} catch (Exception e) {
			log.warn("Failed to convert JAR path to URL: {}", path, e);
		}
	}

	private boolean isLikelyText(PluginPathResource item) {
		byte[] buffer = new byte[4096];

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
			log.debug("Failed to inspect quick-view item [{}]: {}", item.getName(), e.getMessage());
			return false;
		}
	}

	@Data
	private static final class DefaultApplicationPluginContext implements ApplicationPluginContext {
		private final PluginEventBus eventBus = new DefaultPluginEventBus();
		private final ConcurrentHashMap<String, Object> globalData = new ConcurrentHashMap<>();
		private final ConcurrentHashMap<String, Object> theme = new ConcurrentHashMap<>();
		private final Settings settings = new InMemorySettings();
		private ObjectMapper objectMapper = new ObjectMapper();
	}

	private static final class DefaultPluginEventBus implements PluginEventBus {

		private final List<PluginEventListener> listeners = new CopyOnWriteArrayList<>();
		private final List<NuclrEventListener> nuclrListeners = new CopyOnWriteArrayList<>();

		@Override
		public void emit(PluginEvent event) {
			for (PluginEventListener listener : listeners) {
				if (listener.isMessageSupported(event)) {
					listener.handleMessage(event);
				}
			}
		}

		@Override
		public void subscribe(PluginEventListener listener) {
			listeners.add(listener);
			nuclrListeners.add(listener);
		}

		@Override
		public void unsubscribe(PluginEventListener listener) {
			listeners.remove(listener);
			nuclrListeners.remove(listener);
		}

		@Override
		public void emit(String type, java.util.Map<String, Object> event) {
			for (NuclrEventListener listener : nuclrListeners) {
				if (listener.isMessageSupported(type)) {
					listener.handleMessage(type, event);
				}
			}
		}

		@Override
		public void subscribe(NuclrEventListener listener) {
			nuclrListeners.add(listener);
			if (listener instanceof PluginEventListener pluginListener && !listeners.contains(pluginListener)) {
				listeners.add(pluginListener);
			}
		}

		@Override
		public void unsubscribe(NuclrEventListener listener) {
			nuclrListeners.remove(listener);
			if (listener instanceof PluginEventListener pluginListener) {
				listeners.remove(pluginListener);
			}
		}
	}

	private static final class InMemorySettings implements Settings {

		private final ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> namespaces = new ConcurrentHashMap<>();

		@Override
		public void set(String namespace, String key, Object value) {
			namespaces.computeIfAbsent(namespace, unused -> new ConcurrentHashMap<>()).put(key, value);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> T get(String namespace, String key) {
			return (T) namespaces.getOrDefault(namespace, new ConcurrentHashMap<>()).get(key);
		}

		@Override
		public <T> T getOrDefault(String namespace, String key, T defaultValue) {
			T value = get(namespace, key);
			return value != null ? value : defaultValue;
		}
	}

	public interface DelegateAwarePanelProvider {

		boolean wrapsDelegate(Object candidate);
	}

	private static final class ResourceContentPanelProviderAdapter
			implements PanelProviderPlugin, FocusablePlugin, DelegateAwarePanelProvider {

		private final Class<? extends ResourceContentPlugin> delegateType;
		private final ApplicationPluginContext pluginContext;
		private final ResourceContentPlugin delegate;

		private ResourceContentPanelProviderAdapter(
				Class<? extends ResourceContentPlugin> delegateType,
				ApplicationPluginContext pluginContext) throws Exception {
			this(delegateType, pluginContext, delegateType.getDeclaredConstructor().newInstance());
		}

		private ResourceContentPanelProviderAdapter(
				Class<? extends ResourceContentPlugin> delegateType,
				ApplicationPluginContext pluginContext,
				ResourceContentPlugin delegate) throws Exception {
			this.delegateType = delegateType;
			this.pluginContext = pluginContext;
			this.delegate = delegate;
			this.delegate.load(pluginContext);
		}

		private ResourceContentPanelProviderAdapter newInstance() throws Exception {
			return new ResourceContentPanelProviderAdapter(delegateType, pluginContext);
		}

		@Override
		public PluginDescriptor getPluginInfo() {
			return toPluginDescriptor(delegate.manifest());
		}

		@Override
		public JComponent getPanel() {
			return delegate.panel();
		}

		@Override
		public List<PluginPathResource> getChangeDriveResources() {
			List<PluginPathResource> resources = delegate.getChangeDriveResources();
			return resources != null ? resources : List.of();
		}

		@Override
		public boolean openItem(PluginPathResource resource, java.util.concurrent.atomic.AtomicBoolean cancelled)
				throws Exception {
			return delegate.openResource(resource, cancelled);
		}

		@Override
		public boolean canSupport(PluginPathResource resource) {
			return delegate.supports(resource);
		}

		@Override
		public List<MenuResource> getMenuItems(PluginPathResource currentResource) {
			List<MenuResource> items = delegate.menuItems(currentResource);
			return items != null ? items : List.of();
		}

		@Override
		public void unload() throws Exception {
			delegate.closeResource();
			delegate.unload();
		}

		@Override
		public void onFocusGained() {
			invokeNoArg("onFocusGained");
		}

		@Override
		public void onFocusLost() {
			invokeNoArg("onFocusLost");
		}

		@Override
		public boolean wrapsDelegate(Object candidate) {
			return delegate == candidate;
		}

		private void invokeNoArg(String methodName) {
			try {
				delegate.getClass().getMethod(methodName).invoke(delegate);
			} catch (NoSuchMethodException ignored) {
				// Optional lifecycle hook.
			} catch (Exception ex) {
				log.debug("Failed to invoke [{}] on [{}]: {}", methodName, delegate.getClass().getName(),
						ex.getMessage());
			}
		}
	}

	private static PluginDescriptor toPluginDescriptor(PluginManifest manifest) {
		if (manifest == null) {
			return null;
		}
		PluginDescriptor descriptor = new PluginDescriptor();
		descriptor.setSchemaVersion(manifest.getSchemaVersion());
		descriptor.setName(manifest.getName());
		descriptor.setId(manifest.getId());
		descriptor.setVersion(manifest.getVersion());
		descriptor.setDescription(manifest.getDescription());
		descriptor.setAuthor(manifest.getAuthor());
		descriptor.setLicense(manifest.getLicense());
		descriptor.setWebsite(manifest.getWebsite());
		descriptor.setPageUrl(manifest.getPageUrl());
		descriptor.setDocUrl(manifest.getDocUrl());
		Object type = readField(manifest, "type");
		if (type instanceof String text) {
			descriptor.setType(text);
		}
		for (String field : List.of("panelProviders", "quickViewProviders", "screenProviders")) {
			Object value = readField(manifest, field);
			if (value instanceof List<?> list) {
				List<String> strings = list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
				switch (field) {
					case "panelProviders" -> descriptor.setPanelProviders(strings);
					case "quickViewProviders" -> descriptor.setQuickViewProviders(strings);
					case "screenProviders" -> descriptor.setScreenProviders(strings);
					default -> {
					}
				}
			}
		}
		return descriptor;
	}

	private static Object readField(Object target, String fieldName) {
		try {
			var field = target.getClass().getDeclaredField(fieldName);
			field.setAccessible(true);
			return field.get(target);
		} catch (Exception ignored) {
			return null;
		}
	}
}
