package dev.nuclr.commander.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.nuclr.platform.Settings;
import dev.nuclr.platform.events.NuclrEventListener;
import dev.nuclr.plugin.MenuResource;
import dev.nuclr.plugin.PluginPathResource;
import dev.nuclr.plugin.ResourceContentPlugin;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class PluginRegistry {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired(required = false)
	private List<Object> springPanelProviders;

	private final ApplicationPluginContext pluginContext = new DefaultApplicationPluginContext();
	private final List<URLClassLoader> pluginClassLoaders = new CopyOnWriteArrayList<>();
	private final ConcurrentHashMap<ResourceContentPlugin, PluginDescriptor> screenProviderInfo = new ConcurrentHashMap<>();

	@PostConstruct
	public void init() {
		if (springPanelProviders == null) {
			return;
		}
		for (Object provider : springPanelProviders) {
			registerSpringProvider(provider);
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
			PluginDescriptor manifest = readPluginDescriptor(manifestFile, zipFile.getName());

			List<Path> jarPaths = collectJarPaths(pluginDir);
			if (jarPaths.isEmpty()) {
				log.error("No JAR files found in plugin: [{}]", zipFile.getName());
				return;
			}

			URLClassLoader classLoader = new URLClassLoader(toUrls(jarPaths), getClass().getClassLoader());
			pluginClassLoaders.add(classLoader);

			List<String> classNames = discoverResourceContentPluginClasses(jarPaths, classLoader);
			if (classNames.isEmpty()) {
				log.warn("No ResourceContentPlugin implementations found in plugin: [{}]", zipFile.getName());
				return;
			}

			for (String className : classNames) {
				loadResourceContentProvider(className, classLoader, manifest);
			}

			panelProviders.sort(Comparator.comparingInt(this::priorityOfPanelProvider));
		} catch (IOException e) {
			log.error("Failed to load plugin [{}]: {}", zipFile.getName(), e.getMessage(), e);
		}
	}

	private void registerSpringProvider(Object provider) {
		try {
			if (provider instanceof PanelProviderPlugin panelProvider) {
				panelProvider.load(pluginContext);
				panelProviders.add(panelProvider);
				loadedPlugins.add(panelProvider);
				log.info("Registered built-in panel provider: [{}]", provider.getClass().getName());
				return;
			}
			if (provider instanceof ResourceContentPlugin resourceProvider) {
				ResourceContentPanelProviderAdapter adapter = new ResourceContentPanelProviderAdapter(
						resourceProvider.getClass(),
						pluginContext,
						resourceProvider,
						defaultPluginDescriptor(resourceProvider.getClass()));
				panelProviders.add(adapter);
				loadedPlugins.add(adapter);
				log.info("Registered built-in resource content provider: [{}]", provider.getClass().getName());
				return;
			}
			log.debug("Ignoring unsupported built-in plugin bean: [{}]", provider.getClass().getName());
		} catch (Exception e) {
			log.error("Failed to initialize built-in provider [{}]: {}", provider.getClass().getName(), e.getMessage(), e);
		}
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
		return !entry.isDirectory()
				&& entry.getName().endsWith(".class")
				&& !entry.getName().equals("module-info.class")
				&& !entry.getName().endsWith("package-info.class")
				&& !entry.getName().contains("$");
	}

	private String toClassName(String entryName) {
		return entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.');
	}

	private boolean isConcreteResourceContentPlugin(String className, ClassLoader classLoader) {
		try {
			Class<?> clazz = Class.forName(className, false, classLoader);
			int modifiers = clazz.getModifiers();
			return ResourceContentPlugin.class.isAssignableFrom(clazz)
					&& !clazz.isInterface()
					&& !Modifier.isAbstract(modifiers);
		} catch (LinkageError | ReflectiveOperationException e) {
			log.debug("Skipping plugin class [{}]: {}", className, e.getMessage());
			return false;
		}
	}

	private void loadResourceContentProvider(String className, ClassLoader classLoader, PluginDescriptor manifest) {
		try {
			Class<?> clazz = classLoader.loadClass(className);
			@SuppressWarnings("unchecked")
			Class<? extends ResourceContentPlugin> type = (Class<? extends ResourceContentPlugin>) clazz
					.asSubclass(ResourceContentPlugin.class);
			ResourceContentPanelProviderAdapter plugin = new ResourceContentPanelProviderAdapter(type, pluginContext,
					type.getDeclaredConstructor().newInstance(), pluginDescriptorFor(manifest, className));
			panelProviders.add(plugin);
			loadedPlugins.add(plugin);
			log.info("Loaded resource content provider: [{}]", className);
		} catch (Exception e) {
			log.error("Failed to load resource content provider [{}]: {}", className, e.getMessage(), e);
			Alerts.showMessageDialog(null,
					"Failed to load resource content provider [" + className + "]: " + e.getMessage(),
					"Plugin Load Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	private PluginDescriptor pluginDescriptorFor(PluginDescriptor manifest, String className) {
		if (manifest != null) {
			return manifest;
		}
		PluginDescriptor descriptor = new PluginDescriptor();
		descriptor.setName(simpleName(className));
		descriptor.setId(className);
		descriptor.setVersion("unknown");
		descriptor.setDescription("Discovered ResourceContentPlugin");
		descriptor.setType("Plugin");
		return descriptor;
	}

	private PluginDescriptor defaultPluginDescriptor(Class<?> type) {
		return pluginDescriptorFor(null, type.getName());
	}

	private String simpleName(String className) {
		int lastDot = className.lastIndexOf('.');
		return lastDot >= 0 ? className.substring(lastDot + 1) : className;
	}

	private int priorityOfPanelProvider(PanelProviderPlugin provider) {
		if (provider instanceof ResourceContentPanelProviderAdapter adapter) {
			return adapter.getPriority();
		}
		return 0;
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
		public void emit(String type, Map<String, Object> event) {
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

	public interface DelegateAwarePanelProvider {

		boolean wrapsDelegate(Object candidate);
	}

	private static final class ResourceContentPanelProviderAdapter
			implements PanelProviderPlugin, FocusablePlugin, DelegateAwarePanelProvider {

		private final Class<? extends ResourceContentPlugin> delegateType;
		private final ApplicationPluginContext pluginContext;
		private final ResourceContentPlugin delegate;
		private final PluginDescriptor pluginDescriptor;

		private ResourceContentPanelProviderAdapter(
				Class<? extends ResourceContentPlugin> delegateType,
				ApplicationPluginContext pluginContext,
				ResourceContentPlugin delegate,
				PluginDescriptor pluginDescriptor) {
			this.delegateType = delegateType;
			this.pluginContext = pluginContext;
			this.delegate = delegate;
			this.pluginDescriptor = pluginDescriptor;
			this.delegate.load(pluginContext);
		}

		private ResourceContentPanelProviderAdapter(
				Class<? extends ResourceContentPlugin> delegateType,
				ApplicationPluginContext pluginContext,
				PluginDescriptor pluginDescriptor) throws Exception {
			this(delegateType, pluginContext, delegateType.getDeclaredConstructor().newInstance(), pluginDescriptor);
		}

		private ResourceContentPanelProviderAdapter newInstance() throws Exception {
			return new ResourceContentPanelProviderAdapter(delegateType, pluginContext, pluginDescriptor);
		}

		private int getPriority() {
			return delegate.priority();
		}

		@Override
		public PluginDescriptor getPluginInfo() {
			return pluginDescriptor;
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
		public boolean openItem(PluginPathResource resource, AtomicBoolean cancelled) throws Exception {
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
}
