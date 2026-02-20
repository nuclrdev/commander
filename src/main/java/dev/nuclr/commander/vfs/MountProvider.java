package dev.nuclr.commander.vfs;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Plugin interface for contributing virtual filesystems to the panel.
 *
 * <p>Implementations are Spring beans (annotated with {@code @Service} or
 * registered by plugin loaders) and are auto-discovered by {@link MountRegistry}.
 *
 * <p>Example URIs:
 * <ul>
 *   <li>{@code file:///C:/} — local Windows filesystem
 *   <li>{@code jar:file:/C:/data/archive.zip!/} — ZIP/JAR archive via NIO.2
 *   <li>{@code sftp://user@host/home/} — future SSH provider
 *   <li>{@code gs://bucket/path/} — future GCS provider
 * </ul>
 */
public interface MountProvider {

    /**
     * Returns {@code true} if this provider can handle the given URI scheme.
     * This check must be cheap (no I/O).
     */
    boolean supports(URI uri);

    /**
     * Opens (or returns a cached) {@link MountedFs} for the given URI.
     * Implementations are expected to cache mounts internally where appropriate.
     *
     * @param uri     target URI
     * @param options provider-specific mount options (may be empty)
     * @return mounted filesystem descriptor
     * @throws IOException if the filesystem cannot be opened
     */
    MountedFs mount(URI uri, Map<String, ?> options) throws IOException;

    /**
     * Lower numbers win when multiple providers claim the same URI scheme.
     * Defaults to 100.
     */
    default int priority() {
        return 100;
    }
}
