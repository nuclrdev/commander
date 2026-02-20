package dev.nuclr.commander.vfs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Central registry for all virtual filesystem mounts.
 *
 * <p>Auto-discovers every {@link MountProvider} bean in the Spring context,
 * sorts them by {@link MountProvider#priority()}, and delegates mount/resolve
 * calls to the first matching provider.
 *
 * <h3>Indexes maintained</h3>
 * <ul>
 *   <li>{@code mountsByFs} — {@code Map<FileSystem, MountedFs>} for O(1) lookup
 *       in {@link #forPath(Path)} and {@link #capabilitiesFor(Path)}.
 *       Populated eagerly for the local FS in {@link #init()}, then lazily on
 *       first {@link #resolve(URI)} for every other backend.
 * </ul>
 */
@Slf4j
@Service
public class MountRegistry {

    @Autowired
    private List<MountProvider> providers;

    @Autowired
    private LocalMountProvider localMountProvider;

    /** O(1) reverse-lookup: FileSystem → the MountedFs that owns it. */
    private final Map<FileSystem, MountedFs> mountsByFs = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        providers.sort(Comparator.comparingInt(MountProvider::priority));
        log.info("MountRegistry ready with {} provider(s): {}",
                providers.size(),
                providers.stream().map(p -> p.getClass().getSimpleName()).toList());

        // Pre-register the local filesystem so forPath() works without a prior resolve() call.
        MountedFs local = localMountProvider.getLocalMount();
        mountsByFs.put(local.fileSystem(), local);
        log.info("Pre-registered local filesystem: {}", local.fileSystem());
    }

    // ── Primary API ───────────────────────────────────────────────────────────

    /**
     * Resolves a URI to a {@link MountedFs}, mounting on first access.
     * Each provider handles its own internal caching; this method additionally
     * registers the resulting filesystem in {@link #mountsByFs}.
     *
     * @throws IOException if no provider supports the URI or mounting fails
     */
    public MountedFs resolve(URI uri) throws IOException {
        for (MountProvider provider : providers) {
            if (provider.supports(uri)) {
                MountedFs mount = provider.mount(uri, Map.of());
                // Register in the FileSystem index (idempotent — same key → same value)
                mountsByFs.putIfAbsent(mount.fileSystem(), mount);
                return mount;
            }
        }
        throw new IOException("No mount provider available for URI: " + uri);
    }

    /**
     * Resolves a URI and returns the NIO.2 {@link Path} within the mounted filesystem.
     *
     * <ul>
     *   <li>For {@code file://} URIs: delegates to {@code Path.of(uri)} (standard NIO).
     *   <li>For {@code jar:} URIs: parses the entry path from the {@code !/} separator
     *       in the scheme-specific part, since {@code jar:} URIs are opaque and
     *       {@link URI#getPath()} returns {@code null} for them.
     * </ul>
     *
     * @throws IOException if the URI cannot be resolved
     */
    public Path pathFor(URI uri) throws IOException {
        MountedFs mount = resolve(uri);

        if (mount.fileSystem().equals(FileSystems.getDefault())) {
            return Path.of(uri);
        }

        // Opaque URIs (jar:, zip:) — getPath() returns null.
        // The entry path lives after the "!/" separator in the scheme-specific part.
        // e.g.  jar:file:/C:/a.zip!/foo/bar  →  ssp = "file:/C:/a.zip!/foo/bar"
        //        bang+1 = "/foo/bar"
        String ssp = uri.getSchemeSpecificPart(); // non-null for all URI forms
        int bang = ssp != null ? ssp.indexOf("!/") : -1;

        String entryPath;
        if (bang >= 0) {
            entryPath = ssp.substring(bang + 1); // keeps the leading '/'
            if (entryPath.isEmpty()) entryPath = "/";
        } else {
            // Hierarchical non-local URI — fall back to getPath()
            entryPath = uri.getPath();
            if (entryPath == null || entryPath.isEmpty()) entryPath = "/";
        }

        return mount.fileSystem().getPath(entryPath);
    }

    // ── Filesystem-based lookups (O(1)) ──────────────────────────────────────

    /**
     * Returns the {@link MountedFs} that owns the given {@link Path}'s filesystem,
     * or {@code null} if it has not been registered yet.
     *
     * <p>O(1) via {@code mountsByFs} index.
     */
    public MountedFs forPath(Path path) {
        return mountsByFs.get(path.getFileSystem());
    }

    /**
     * Returns the {@link Capabilities} for the filesystem that owns {@code path}.
     * Falls back to {@link Capabilities#localWindows()} when the mount is unknown
     * (conservative: read/write allowed, no POSIX).
     */
    public Capabilities capabilitiesFor(Path path) {
        MountedFs mount = forPath(path);
        return mount != null ? mount.capabilities() : Capabilities.localWindows();
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    /**
     * Lists all root {@link Path} entries on the local (default) filesystem.
     * On Windows returns {@code [C:\, D:\, ...]}.
     * On Linux/macOS returns {@code [/]}.
     */
    public List<Path> listLocalRoots() {
        List<Path> roots = new ArrayList<>();
        FileSystems.getDefault().getRootDirectories().forEach(roots::add);
        return roots;
    }
}
