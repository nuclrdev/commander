package dev.nuclr.commander.vfs;

import java.net.URI;
import java.nio.file.FileSystems;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.nuclr.commander.common.SystemUtils;

/**
 * {@link MountProvider} for the local (default) filesystem.
 *
 * <p>Handles {@code file://} URIs and bare path URIs (no scheme). Always
 * returns a singleton {@link MountedFs} backed by {@link FileSystems#getDefault()}.
 * Capabilities are set based on the host operating system.
 */
@Service
public class LocalMountProvider implements MountProvider {

    private final MountedFs localMount;

    public LocalMountProvider() {
        var caps = SystemUtils.isOsWindows()
                ? Capabilities.localWindows()
                : Capabilities.localPosix();

        this.localMount = new MountedFs(
                "local",
                URI.create("file:///"),
                FileSystems.getDefault(),
                caps);
    }

    @Override
    public boolean supports(URI uri) {
        String scheme = uri.getScheme();
        return scheme == null || "file".equalsIgnoreCase(scheme);
    }

    @Override
    public MountedFs mount(URI uri, Map<String, ?> options) {
        // The local filesystem is always open; no mounting needed.
        return localMount;
    }

    @Override
    public int priority() {
        return 0; // highest priority — matched before any other provider
    }

    /** Direct accessor for the local mount descriptor. */
    public MountedFs getLocalMount() {
        return localMount;
    }
}
