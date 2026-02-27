package dev.nuclr.commander.panel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.nuclr.plugin.panel.FilePanelProvider;
import dev.nuclr.plugin.panel.PanelRoot;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Central registry for all {@link FilePanelProvider} instances.
 *
 * <p>At startup, Spring auto-discovers every {@link FilePanelProvider} bean
 * (if any).  Plugin-loaded providers are registered at runtime via
 * {@link #registerProvider}.
 *
 * <p>{@link #listAllRoots()} returns the union of all roots from all registered
 * providers, sorted by {@link FilePanelProvider#priority()} ascending.
 */
@Slf4j
@Service
public class FilePanelProviderRegistry {

    /** Spring-injected built-in providers; copied to a mutable list so plugins can be added. */
    private final List<FilePanelProvider> providers = new ArrayList<>();

    @Autowired(required = false)
    private List<FilePanelProvider> springProviders;

    @PostConstruct
    public void init() {
        if (springProviders != null) {
            providers.addAll(springProviders);
        }
        providers.sort(Comparator.comparingInt(FilePanelProvider::priority));
        log.info("FilePanelProviderRegistry ready with {} provider(s): {}",
                providers.size(),
                providers.stream().map(FilePanelProvider::id).toList());
    }

    /**
     * Registers a provider that was loaded at runtime (e.g. from a plugin).
     * The provider is inserted in priority order.
     */
    public void registerProvider(FilePanelProvider provider) {
        log.info("Registering FilePanelProvider: id=[{}], displayName=[{}]",
                provider.id(), provider.displayName());
        providers.add(provider);
        providers.sort(Comparator.comparingInt(FilePanelProvider::priority));
    }

    /**
     * Returns all roots from all registered providers, sorted by provider priority
     * (lower = first).
     */
    public List<PanelRoot> listAllRoots() {
        var roots = new ArrayList<PanelRoot>();
        for (var provider : providers) {
            roots.addAll(provider.roots());
        }
        return roots;
    }

    /** Returns a snapshot of all currently registered providers. */
    public List<FilePanelProvider> getProviders() {
        return List.copyOf(providers);
    }
}
