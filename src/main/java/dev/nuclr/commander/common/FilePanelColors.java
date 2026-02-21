package dev.nuclr.commander.common;

import java.awt.Color;
import java.nio.file.Path;
import java.util.List;

/**
 * Configurable colors and file-type extension lists for the file-panel table.
 *
 * <p>Colors are stored as CSS hex strings (e.g. {@code "#00CC00"}) and
 * extension lists as lower-case dot-prefixed strings (e.g. {@code ".zip"}).
 * Both are serialized as plain JSON by Jackson, so you can edit
 * {@code settings.json} directly to change any color or add/remove extensions:
 *
 * <pre>{@code
 * "colors": {
 *   "executableColor"     : "#00CC00",
 *   "executableExtensions": [".exe", ".bat", ".cmd", ...],
 *   "archiveColor"        : "#FF69B4",
 *   "archiveExtensions"   : [".zip", ".tar", ".gz", ...]
 * }
 * }</pre>
 *
 * <p>On POSIX systems the {@code executableExtensions} list is ignored —
 * the owner-execute permission bit is used instead.
 *
 * <p>Persisted as part of {@link LocalSettingsStore.AppSettings}.
 */
public record FilePanelColors(

        /** Foreground color for executable file names (hex, e.g. {@code "#00CC00"}). */
        String executableColor,

        /**
         * Extensions that identify executable files on Windows (lower-case, dot-prefixed).
         * Ignored on POSIX — the owner-execute bit is used there.
         */
        List<String> executableExtensions,

        /** Foreground color for archive file names (hex, e.g. {@code "#FF69B4"}). */
        String archiveColor,

        /** Extensions that identify archive/compressed files (lower-case, dot-prefixed). */
        List<String> archiveExtensions) {

    /** Normalises null fields so callers never receive nulls. */
    public FilePanelColors {
        executableColor      = executableColor      != null ? executableColor      : Defaults.EXECUTABLE_COLOR;
        executableExtensions = executableExtensions != null ? List.copyOf(executableExtensions) : Defaults.EXECUTABLE_EXTENSIONS;
        archiveColor         = archiveColor         != null ? archiveColor         : Defaults.ARCHIVE_COLOR;
        archiveExtensions    = archiveExtensions    != null ? List.copyOf(archiveExtensions)    : Defaults.ARCHIVE_EXTENSIONS;
    }

    /** Returns a {@link FilePanelColors} populated with out-of-the-box defaults. */
    public static FilePanelColors defaults() {
        return new FilePanelColors(
                Defaults.EXECUTABLE_COLOR,
                Defaults.EXECUTABLE_EXTENSIONS,
                Defaults.ARCHIVE_COLOR,
                Defaults.ARCHIVE_EXTENSIONS);
    }

    // ── AWT color conversions ─────────────────────────────────────────────

    /** {@link Color} to use for executable file names. */
    public Color executableAwtColor() {
        return Color.decode(executableColor);
    }

    /** {@link Color} to use for archive file names. */
    public Color archiveAwtColor() {
        return Color.decode(archiveColor);
    }

    // ── Extension helpers ─────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code path}'s extension is in
     * {@link #executableExtensions} (case-insensitive).
     * Used on Windows; on POSIX prefer the permission-bit check.
     */
    public boolean isWindowsExecutable(Path path) {
        return matchesExtension(path, executableExtensions);
    }

    /**
     * Returns {@code true} if {@code path}'s extension is in
     * {@link #archiveExtensions} (case-insensitive).
     */
    public boolean isArchive(Path path) {
        return matchesExtension(path, archiveExtensions);
    }

    private static boolean matchesExtension(Path path, List<String> extensions) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && extensions.contains(name.substring(dot).toLowerCase());
    }

    // ── Default values ────────────────────────────────────────────────────

    /** Constants exposed so that code outside this class can reference them. */
    public static final class Defaults {
        private Defaults() {}

        /** Bright green — classic Unix executable color. */
        public static final String EXECUTABLE_COLOR = "#00CC00";

        /** Windows file extensions treated as executable. */
        public static final List<String> EXECUTABLE_EXTENSIONS = List.of(
                ".exe", ".bat", ".cmd", ".com",
                ".msi", ".ps1", ".vbs", ".wsf", ".scr");

        /** Hot pink. */
        public static final String ARCHIVE_COLOR = "#FF69B4";

        /** Common archive and compressed-file extensions. */
        public static final List<String> ARCHIVE_EXTENSIONS = List.of(
                ".zip", ".tar", ".gz", ".tgz", ".bz2", ".xz", ".zst",
                ".7z", ".rar", ".lz4", ".lzma",
                ".jar", ".war", ".ear",
                ".iso", ".img", ".cab");
    }
}
