package dev.nuclr.commander.vfs;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;

/**
 * Backend-agnostic description of a single directory entry.
 *
 * <p>This is the unit of data the UI (FilePanel / FileTableModel) works with.
 * It carries everything needed to render a row in the file table without
 * touching the underlying filesystem again.
 *
 * @param path        canonical Path of this entry within its mounted filesystem
 * @param displayName name shown in the UI; use {@link #PARENT_ENTRY_NAME} for the ".." row
 * @param directory   {@code true} if this entry is a directory (or ZIP root etc.)
 * @param executable  {@code true} if the file is executable (POSIX: owner-execute bit;
 *                    Windows: recognised executable extension such as .exe/.bat/.cmd)
 * @param archive     {@code true} if the file is a recognised archive or compressed format
 * @param size        file size in bytes; 0 for directories or when unavailable
 * @param modified    last-modified timestamp; may be {@code null} for synthetic entries
 * @param owner       POSIX owner name; {@code null} when unsupported or unreadable
 * @param permissions POSIX permission string (e.g. "rwxr-xr-x"); {@code null} when unsupported
 * @param extras      provider-specific attributes (e.g. ETag, storage class) for future use
 */
public record EntryInfo(
        Path path,
        String displayName,
        boolean directory,
        boolean executable,
        boolean archive,
        long size,
        FileTime modified,
        String owner,
        String permissions,
        Map<String, Object> extras) {

    /** Sentinel name used for the "go to parent directory" row. */
    public static final String PARENT_ENTRY_NAME = "..";

    /** {@code true} if this entry represents the ".." parent navigation row. */
    public boolean isParentEntry() {
        return PARENT_ENTRY_NAME.equals(displayName);
    }

    /**
     * Creates the synthetic parent-navigation entry for {@code currentDir}.
     * The {@link #path} is set to {@code currentDir} so callers can navigate
     * to {@code path.getParent()} when the row is activated.
     */
    public static EntryInfo parentEntry(Path currentDir) {
        return new EntryInfo(
                currentDir,
                PARENT_ENTRY_NAME,
                true,
                false,
                false,
                0L,
                null,
                null,
                null,
                Map.of());
    }
}
