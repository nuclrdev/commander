package dev.nuclr.plugin;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PluginTheme {

	private String name;
	private Map<String, String> uiDefaults;
	private String fontFamily;
	private int fontSize;
}
