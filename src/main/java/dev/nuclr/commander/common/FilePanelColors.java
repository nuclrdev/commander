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
 * {@code settings.json} directly to change any color or add/remove extensions.
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
        List<String> archiveExtensions,

        /** Foreground color for image file names. */
        String imageColor,

        /** Extensions that identify images. */
        List<String> imageExtensions,

        /** Foreground color for audio/music file names. */
        String audioColor,

        /** Extensions that identify audio/music. */
        List<String> audioExtensions,

        /** Foreground color for video file names. */
        String videoColor,

        /** Extensions that identify video. */
        List<String> videoExtensions,

        /** Foreground color for PDF file names. */
        String pdfColor,

        /** Extensions that identify PDFs (usually just .pdf). */
        List<String> pdfExtensions,

        /** Foreground color for "document/office" style files (optional bucket). */
        String documentColor,

        /** Extensions that identify documents. */
        List<String> documentExtensions,

        /** Foreground color for hidden entries (usually gray/dim). */
        String hiddenColor,

        /** Foreground color for system entries (Windows/system FS) (usually gray/dim). */
        String systemColor,

        /** Foreground color for symlinks/shortcuts. */
        String symlinkColor

) {

    /** Normalises null fields so callers never receive nulls. */
    public FilePanelColors {
        executableColor      = executableColor      != null ? executableColor      : Defaults.EXECUTABLE_COLOR;
        executableExtensions = executableExtensions != null ? List.copyOf(executableExtensions) : Defaults.EXECUTABLE_EXTENSIONS;

        archiveColor         = archiveColor         != null ? archiveColor         : Defaults.ARCHIVE_COLOR;
        archiveExtensions    = archiveExtensions    != null ? List.copyOf(archiveExtensions)    : Defaults.ARCHIVE_EXTENSIONS;

        imageColor           = imageColor           != null ? imageColor           : Defaults.IMAGE_COLOR;
        imageExtensions      = imageExtensions      != null ? List.copyOf(imageExtensions)      : Defaults.IMAGE_EXTENSIONS;

        audioColor           = audioColor           != null ? audioColor           : Defaults.AUDIO_COLOR;
        audioExtensions      = audioExtensions      != null ? List.copyOf(audioExtensions)      : Defaults.AUDIO_EXTENSIONS;

        videoColor           = videoColor           != null ? videoColor           : Defaults.VIDEO_COLOR;
        videoExtensions      = videoExtensions      != null ? List.copyOf(videoExtensions)      : Defaults.VIDEO_EXTENSIONS;

        pdfColor             = pdfColor             != null ? pdfColor             : Defaults.PDF_COLOR;
        pdfExtensions        = pdfExtensions        != null ? List.copyOf(pdfExtensions)        : Defaults.PDF_EXTENSIONS;

        documentColor        = documentColor        != null ? documentColor        : Defaults.DOCUMENT_COLOR;
        documentExtensions   = documentExtensions   != null ? List.copyOf(documentExtensions)   : Defaults.DOCUMENT_EXTENSIONS;

        hiddenColor          = hiddenColor          != null ? hiddenColor          : Defaults.HIDDEN_COLOR;
        systemColor          = systemColor          != null ? systemColor          : Defaults.SYSTEM_COLOR;
        symlinkColor         = symlinkColor         != null ? symlinkColor         : Defaults.SYMLINK_COLOR;
    }

    /** Returns a {@link FilePanelColors} populated with out-of-the-box defaults. */
    public static FilePanelColors defaults() {
        return new FilePanelColors(
                Defaults.EXECUTABLE_COLOR,
                Defaults.EXECUTABLE_EXTENSIONS,
                Defaults.ARCHIVE_COLOR,
                Defaults.ARCHIVE_EXTENSIONS,
                Defaults.IMAGE_COLOR,
                Defaults.IMAGE_EXTENSIONS,
                Defaults.AUDIO_COLOR,
                Defaults.AUDIO_EXTENSIONS,
                Defaults.VIDEO_COLOR,
                Defaults.VIDEO_EXTENSIONS,
                Defaults.PDF_COLOR,
                Defaults.PDF_EXTENSIONS,
                Defaults.DOCUMENT_COLOR,
                Defaults.DOCUMENT_EXTENSIONS,
                Defaults.HIDDEN_COLOR,
                Defaults.SYSTEM_COLOR,
                Defaults.SYMLINK_COLOR
        );
    }

    // ── AWT color conversions ─────────────────────────────────────────────

    public Color executableAwtColor() { return Color.decode(executableColor); }
    public Color archiveAwtColor()    { return Color.decode(archiveColor); }
    public Color imageAwtColor()      { return Color.decode(imageColor); }
    public Color audioAwtColor()      { return Color.decode(audioColor); }
    public Color videoAwtColor()      { return Color.decode(videoColor); }
    public Color pdfAwtColor()        { return Color.decode(pdfColor); }
    public Color documentAwtColor()   { return Color.decode(documentColor); }
    public Color hiddenAwtColor()     { return Color.decode(hiddenColor); }
    public Color systemAwtColor()     { return Color.decode(systemColor); }
    public Color symlinkAwtColor()    { return Color.decode(symlinkColor); }

    // ── Extension helpers ─────────────────────────────────────────────────

    /** Used on Windows; on POSIX prefer the permission-bit check. */
    public boolean isWindowsExecutable(Path path) {
        return matchesExtension(path, executableExtensions);
    }

    public boolean isArchive(Path path) {
        return matchesExtension(path, archiveExtensions);
    }

    public boolean isImage(Path path) {
        return matchesExtension(path, imageExtensions);
    }

    public boolean isAudio(Path path) {
        return matchesExtension(path, audioExtensions);
    }

    public boolean isVideo(Path path) {
        return matchesExtension(path, videoExtensions);
    }

    public boolean isPdf(Path path) {
        return matchesExtension(path, pdfExtensions);
    }

    public boolean isDocument(Path path) {
        return matchesExtension(path, documentExtensions);
    }

    private static boolean matchesExtension(Path path, List<String> extensions) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && extensions.contains(name.substring(dot).toLowerCase());
    }

    // ── Default values ────────────────────────────────────────────────────

    public static final class Defaults {
        private Defaults() {}

        /** Bright green — classic Unix executable color. */
        public static final String EXECUTABLE_COLOR = "#00CC00";

        /** Windows file extensions treated as executable. */
        public static final List<String> EXECUTABLE_EXTENSIONS = List.of(
                ".exe", ".bat", ".cmd", ".com",
                ".msi", ".ps1", ".vbs", ".wsf", ".scr"
        );

        /** Hot pink. */
        public static final String ARCHIVE_COLOR = "#FF69B4";

        /** Common archive and compressed-file extensions. */
        public static final List<String> ARCHIVE_EXTENSIONS = List.of(
                ".zip", ".tar", ".gz", ".tgz", ".bz2", ".xz", ".zst",
                ".7z", ".rar", ".lz4", ".lzma",
                ".jar", ".war", ".ear",
                ".iso", ".img", ".cab"
        );

        /** Purple-ish for images. */
        public static final String IMAGE_COLOR = "#B388FF";

        public static final List<String> IMAGE_EXTENSIONS = List.of(
                ".png", ".jpg", ".jpeg", ".gif", ".bmp",
                ".webp", ".tif", ".tiff",
                ".ico", ".svg",
                ".heic", ".heif",
                ".avif",
                ".psd" // if you plan to support it (ImageMagick etc.)
        );

        /** Teal-ish for audio. */
        public static final String AUDIO_COLOR = "#4DD0E1";

        public static final List<String> AUDIO_EXTENSIONS = List.of(
                ".mp3", ".flac", ".wav", ".aac", ".m4a",
                ".ogg", ".opus", ".wma", "s3m", ".mod", ".xm", ".it",
                ".aiff", ".aif", ".aifc",
                ".alac"
        );

        /** Blue-ish for video. */
        public static final String VIDEO_COLOR = "#64B5F6";

        public static final List<String> VIDEO_EXTENSIONS = List.of(
                ".mp4", ".mkv", ".avi", ".mov", ".webm",
                ".wmv", ".flv", ".m4v", ".mpeg", ".mpg"
        );

        /** Red-ish for PDFs (recognisable). */
        public static final String PDF_COLOR = "#EF5350";

        public static final List<String> PDF_EXTENSIONS = List.of(".pdf");

        /** Amber-ish for docs/office. */
        public static final String DOCUMENT_COLOR = "#FFB74D";

        public static final List<String> DOCUMENT_EXTENSIONS = List.of(
                ".txt", ".md", ".rtf",
                ".doc", ".docx", ".odt",
                ".xls", ".xlsx", ".ods",
                ".ppt", ".pptx", ".odp",
                ".csv", ".tsv"
        );

        /** Gray/dim for hidden. */
        public static final String HIDDEN_COLOR = "#9E9E9E";

        /** Slightly darker gray for system. */
        public static final String SYSTEM_COLOR = "#757575";

        /** Cyan-ish for symlinks/shortcuts. */
        public static final String SYMLINK_COLOR = "#26C6DA";
    }
}