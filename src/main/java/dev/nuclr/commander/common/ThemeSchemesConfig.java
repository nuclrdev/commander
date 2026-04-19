package dev.nuclr.commander.common;

import java.util.Map;
import java.util.stream.Collectors;

import dev.nuclr.platform.NuclrThemeScheme;

/**
 * JSON document containing a selectable set of named color schemes.
 */
public record ThemeSchemesConfig(
		String activeScheme,
		Map<String, NuclrThemeScheme> schemes) {

	public ThemeSchemesConfig {
		activeScheme = activeScheme != null && !activeScheme.isBlank()
				? activeScheme
				: Defaults.ACTIVE_SCHEME;
		schemes = schemes != null && !schemes.isEmpty()
				? normalizeSchemes(schemes)
				: Defaults.SCHEMES;
	}

	public NuclrThemeScheme activeThemeScheme() {
		return schemes.getOrDefault(activeScheme, Defaults.SCHEMES.get(Defaults.ACTIVE_SCHEME));
	}

	public static ThemeSchemesConfig defaults() {
		return new ThemeSchemesConfig(Defaults.ACTIVE_SCHEME, Defaults.SCHEMES);
	}

	public static final class Defaults {
		private Defaults() {
		}

		public static final String ACTIVE_SCHEME = "farBlue";

		public static final Map<String, NuclrThemeScheme> SCHEMES = Map.of(
				ACTIVE_SCHEME,
				new NuclrThemeScheme(
						"Far Blue",
						FileNameHighlighter.mergeThemeEntries(Map.ofEntries(
								Map.entry("Panel.background", "#0b2f59"),
								Map.entry("Panel.foreground", "#d7e8ff"),
								Map.entry("Viewport.background", "#0b2f59"),
								Map.entry("ScrollPane.background", "#0b2f59"),
								Map.entry("Table.background", "#0b2f59"),
								Map.entry("Table.foreground", "#d7e8ff"),
								Map.entry("Table.selectionBackground", "#d8c35a"),
								Map.entry("Table.selectionForeground", "#091f3a"),
								Map.entry("Table.gridColor", "#1f4a7a"),
								Map.entry("TableHeader.background", "#123a6a"),
								Map.entry("TableHeader.foreground", "#e8f1ff"),
								Map.entry("TableHeader.separatorColor", "#1f4a7a"),
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
								Map.entry("Component.linkColor", "#8dc6ff"),
								Map.entry("TitlePane.background", "#123a6a"),
								Map.entry("TitlePane.foreground", "#e8f1ff"),
								Map.entry("TitlePane.inactiveBackground", "#0f2f54"),
								Map.entry("TitlePane.inactiveForeground", "#a8bfd8"),
								Map.entry("TitlePane.embeddedForeground", "#e8f1ff")
						)))
				);
		}

	private static Map<String, NuclrThemeScheme> normalizeSchemes(Map<String, NuclrThemeScheme> schemes) {
		return schemes.entrySet().stream()
				.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> normalizeScheme(entry.getValue())));
	}

	private static NuclrThemeScheme normalizeScheme(NuclrThemeScheme scheme) {
		if (scheme == null) {
			return Defaults.SCHEMES.get(Defaults.ACTIVE_SCHEME);
		}
		return new NuclrThemeScheme(
				scheme.getName(),
				FileNameHighlighter.mergeThemeEntries(scheme.getUiDefaults()));
	}
}
