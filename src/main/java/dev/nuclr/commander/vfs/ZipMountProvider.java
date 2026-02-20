package dev.nuclr.commander.vfs;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * {@link MountProvider} for ZIP and JAR archives using the NIO.2 built-in
 * ZIP filesystem provider ({@code com.sun.nio.zipfs}).
 *
 * <p>Handles {@code jar:file:/path/to/archive.zip!/} URIs. Each unique archive
 * URI is mounted at most once and cached; subsequent requests return the
 * same live {@link java.nio.file.FileSystem}.
 *
 * <p>ZIP-mounted filesystems are always read-only from the panel's perspective.
 *
 * <h3>Example URIs</h3>
 * <pre>
 *   jar:file:/C:/data/archive.zip!/
 *   jar:file:/home/user/sources.jar!/com/example/
 * </pre>
 */
@Slf4j
@Service
public class ZipMountProvider implements MountProvider {

    private final Map<URI, MountedFs> cache = new ConcurrentHashMap<>();

    @Override
    public boolean supports(URI uri) {
        String scheme = uri.getScheme();
        return "jar".equalsIgnoreCase(scheme) || "zip".equalsIgnoreCase(scheme);
    }

    @Override
    public MountedFs mount(URI uri, Map<String, ?> options) throws IOException {
        // Normalise: strip the entry path so we key on the archive URI only
        URI archiveUri = toArchiveUri(uri);

        MountedFs existing = cache.get(archiveUri);
        if (existing != null && existing.fileSystem().isOpen()) {
            return existing;
        }

        log.info("Mounting ZIP filesystem: {}", archiveUri);
        var fs = FileSystems.newFileSystem(archiveUri, options != null ? options : Map.of());
        var mounted = new MountedFs(
                archiveUri.toString(),
                archiveUri,
                fs,
                Capabilities.readOnly());

        cache.put(archiveUri, mounted);
        return mounted;
    }

    @Override
    public int priority() {
        return 10;
    }

    /**
     * Opens a ZIP archive from a plain local {@link Path} (e.g. when the user
     * navigates into a .zip file in the panel). Returns the root path of the
     * mounted archive.
     */
    public Path mountAndGetRoot(Path zipFile) throws IOException {
        URI jarUri = URI.create("jar:" + zipFile.toUri() + "!/");
        MountedFs mounted = mount(jarUri, Map.of());
        return mounted.fileSystem().getPath("/");
    }

    /** Converts a jar-entry URI ({@code jar:file:/a.zip!/foo/bar}) to the archive URI ({@code jar:file:/a.zip!/}). */
    private static URI toArchiveUri(URI uri) {
        String s = uri.toString();
        int bang = s.indexOf("!/");
        if (bang >= 0) {
            return URI.create(s.substring(0, bang + 2));
        }
        return uri;
    }
}
