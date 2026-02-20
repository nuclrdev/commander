package dev.nuclr.commander.vfs;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

/**
 * A live, mounted virtual filesystem with its associated metadata.
 *
 * <p>Holds a NIO.2 {@link FileSystem} and the {@link Capabilities} reported
 * by its {@link MountProvider}. The panel works exclusively with
 * {@link java.nio.file.Path} instances obtained from this filesystem.
 *
 * @param id           stable identifier for this mount (e.g. "local", "zip:/path/to/file.zip")
 * @param uri          URI that was used to open this mount
 * @param fileSystem   live NIO.2 FileSystem (never null)
 * @param capabilities what operations the underlying backend supports
 */
public record MountedFs(
        String id,
        URI uri,
        FileSystem fileSystem,
        Capabilities capabilities) implements Closeable {

    /**
     * Closes the underlying {@link FileSystem} unless it is the JVM-default
     * local filesystem, which must never be closed.
     */
    @Override
    public void close() throws IOException {
        if (!fileSystem.equals(FileSystems.getDefault())) {
            fileSystem.close();
        }
    }
}
