package dev.nuclr.commander.common;

import java.awt.Color;

/**
 * Configurable colors for the file-panel table.
 *
 * <p>Each color is stored as a CSS hex string (e.g. {@code "#00CC00"}) so
 * Jackson can round-trip it without a custom serializer.  Use the
 * {@code *AwtColor()} methods to obtain live {@link Color} instances for
 * Swing rendering.
 *
 * <p>Persisted as part of {@link LocalSettingsStore.AppSettings}.
 */
public record FilePanelColors(

        /** Foreground color applied to executable file names. */
        String executableColor) {

    /** Normalises null fields so callers never receive null colors. */
    public FilePanelColors {
        if (executableColor == null) executableColor = Defaults.EXECUTABLE;
    }

    /** Returns a {@link FilePanelColors} with out-of-the-box defaults. */
    public static FilePanelColors defaults() {
        return new FilePanelColors(Defaults.EXECUTABLE);
    }

    // ── AWT conversions ───────────────────────────────────────────────────

    /** Returns the {@link Color} to use for executable file names. */
    public Color executableAwtColor() {
        return Color.decode(executableColor);
    }

    // ── Default values (constants so subclasses / tests can reference them) ──

    public static final class Defaults {
        private Defaults() {}

        /** Bright green — classic Unix executable color. */
        public static final String EXECUTABLE = "#00CC00";
    }
}
