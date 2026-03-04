package dev.nuclr.commander.common;

import java.util.Map;

/**
 * JSON-backed Swing/FlatLaf override palette.
 *
 * <p>Keys are UIManager defaults (for example {@code "Panel.background"}).
 * Values are CSS-style hex colors (for example {@code "#102a43"}).
 */
public record ThemeScheme(
		String name,
		Map<String, String> uiDefaults) {

	public ThemeScheme {
		name = name != null && !name.isBlank() ? name : "Unnamed";
		uiDefaults = uiDefaults != null ? Map.copyOf(uiDefaults) : Map.of();
	}
}
