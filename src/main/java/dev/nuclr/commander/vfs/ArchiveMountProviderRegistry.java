package dev.nuclr.commander.vfs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.nuclr.plugin.mount.ArchiveMountProvider;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Runtime registry for {@link ArchiveMountProvider} instances contributed by plugins.
 *
 * <p>Starts empty at application startup. Plugins register their providers via
 * {@link #registerProvider(ArchiveMountProvider)} during plugin loading. Providers
 * are kept sorted by {@link ArchiveMountProvider#priority()} (lower value = higher priority).
 */
@Slf4j
@Service
public class ArchiveMountProviderRegistry {

	@Autowired(required = false)
	private List<ArchiveMountProvider> springProviders;

	private final List<ArchiveMountProvider> providers = new ArrayList<>();

	@PostConstruct
	public void init() {
		if (springProviders != null) {
			providers.addAll(springProviders);
		}
		providers.sort(Comparator.comparingInt(ArchiveMountProvider::priority));
		log.info("ArchiveMountProviderRegistry ready with {} provider(s)", providers.size());
	}

	/**
	 * Registers a provider contributed by a plugin at runtime.
	 * Re-sorts the provider list by priority after insertion.
	 */
	public synchronized void registerProvider(ArchiveMountProvider provider) {
		providers.add(provider);
		providers.sort(Comparator.comparingInt(ArchiveMountProvider::priority));
		log.info("Registered ArchiveMountProvider: [{}] (priority {})",
				provider.getClass().getName(), provider.priority());
	}

	/**
	 * Returns the first provider (by priority) that can handle the given file,
	 * or {@link Optional#empty()} if none can.
	 */
	public Optional<ArchiveMountProvider> findFor(Path file) {
		return providers.stream()
				.filter(p -> p.canHandle(file))
				.findFirst();
	}
}
