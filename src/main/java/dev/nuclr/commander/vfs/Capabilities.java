package dev.nuclr.commander.vfs;

import java.util.EnumSet;
import java.util.Set;

/**
 * Describes what a mounted filesystem can do.
 * The UI queries capabilities before offering operations to the user so that
 * e.g. "Move" is only shown when the backend actually supports it.
 */
public record Capabilities(
        Set<Operation> supportedOps,
        boolean atomicMove,
        boolean serverSideCopy,
        boolean posixPermissions,
        boolean watchSupported) {

    public boolean supports(Operation op) {
        return supportedOps.contains(op);
    }

    // ── Factory presets ────────────────────────────────────────────────────

    /**
     * Read-only backend (ZIP archive, anonymous FTP, GCS without credentials).
     * Includes {@link Operation#LIST} because browsing directories is the minimum
     * useful capability — a backend that can't even list is not navigable.
     */
    public static Capabilities readOnly() {
        return new Capabilities(
                EnumSet.of(Operation.LIST, Operation.READ),
                false, false, false, false);
    }

    /** Local filesystem on Windows (no POSIX permissions, but has watches). */
    public static Capabilities localWindows() {
        return new Capabilities(
                EnumSet.of(
                        Operation.LIST, Operation.READ, Operation.WRITE, Operation.DELETE,
                        Operation.MOVE, Operation.RENAME, Operation.COPY,
                        Operation.CREATE_DIRECTORY, Operation.WATCH),
                true, false, false, true);
    }

    /** Local filesystem on POSIX (Linux/macOS): full capabilities. */
    public static Capabilities localPosix() {
        return new Capabilities(
                EnumSet.allOf(Operation.class),
                true, false, true, true);
    }
}
