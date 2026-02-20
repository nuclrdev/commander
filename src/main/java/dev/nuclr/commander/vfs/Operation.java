package dev.nuclr.commander.vfs;

/**
 * File system operations that a mounted filesystem may or may not support.
 * Used by {@link Capabilities} to advertise what the backend can do,
 * allowing the UI to enable/disable actions accordingly.
 */
public enum Operation {
    /** Browse / iterate directory contents. */
    LIST,
    READ,
    WRITE,
    DELETE,
    MOVE,
    RENAME,
    COPY,
    CREATE_DIRECTORY,
    WATCH,
    SET_PERMISSIONS
}
