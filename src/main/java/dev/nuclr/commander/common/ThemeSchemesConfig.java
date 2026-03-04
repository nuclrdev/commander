package dev.nuclr.commander.common;

import java.util.Map;

/**
 * JSON document containing a selectable set of named color schemes.
 */
public record ThemeSchemesConfig(
		String activeScheme,
		Map<String, ThemeScheme> schemes) {

	public ThemeSchemesConfig {
		activeScheme = activeScheme != null && !activeScheme.isBlank()
				? activeScheme
				: Defaults.ACTIVE_SCHEME;
		schemes = schemes != null && !schemes.isEmpty()
				? Map.copyOf(schemes)
				: Defaults.SCHEMES;
	}

	public ThemeScheme activeThemeScheme() {
		return schemes.getOrDefault(activeScheme, Defaults.SCHEMES.get(Defaults.ACTIVE_SCHEME));
	}

	public static ThemeSchemesConfig defaults() {
		return new ThemeSchemesConfig(Defaults.ACTIVE_SCHEME, Defaults.SCHEMES);
	}

	public static final class Defaults {
		private Defaults() {
		}

		public static final String ACTIVE_SCHEME = "farBlue";

		public static final Map<String, ThemeScheme> SCHEMES = Map.of(
				"farBlue",
				new ThemeScheme(
						"Far Blue",
						Map.ofEntries(
								Map.entry("Panel.background", "#0b2f59"),
								Map.entry("Panel.foreground", "#d7e8ff"),
								Map.entry("Viewport.background", "#0b2f59"),
								Map.entry("ScrollPane.background", "#0b2f59"),
								Map.entry("Table.background", "#0b2f59"),
								Map.entry("Table.foreground", "#d7e8ff"),
								Map.entry("Table.selectionBackground", "#d8c35a"),
								Map.entry("Table.selectionForeground", "#091f3a"),
								Map.entry("Table.gridColor", "#1f4a7a"),
								Map.entry("Label.foreground", "#d7e8ff"),
								Map.entry("Button.background", "#184a86"),
								Map.entry("Button.foreground", "#e8f1ff"),
								Map.entry("MenuBar.background", "#123a6a"),
								Map.entry("MenuBar.foreground", "#e8f1ff"),
								Map.entry("Menu.background", "#123a6a"),
								Map.entry("Menu.foreground", "#e8f1ff"),
								Map.entry("MenuItem.background", "#123a6a"),
								Map.entry("MenuItem.foreground", "#e8f1ff"),
								Map.entry("PopupMenu.background", "#123a6a"),
								Map.entry("SplitPane.background", "#0b2f59"),
								Map.entry("TextField.background", "#123a6a"),
								Map.entry("TextField.foreground", "#e8f1ff"),
								Map.entry("ProgressBar.background", "#123a6a"),
								Map.entry("ProgressBar.foreground", "#d8c35a"),
								Map.entry("ProgressBar.selectionBackground", "#091f3a"),
								Map.entry("ProgressBar.selectionForeground", "#d8c35a"),
							Map.entry("Component.focusColor", "#d8c35a"),
							Map.entry("Component.linkColor", "#8dc6ff")
					))
			);
		}
	}
