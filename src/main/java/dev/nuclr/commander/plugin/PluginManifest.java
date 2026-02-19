package dev.nuclr.commander.plugin;

import java.util.ArrayList;
import java.util.List;

import dev.nuclr.plugin.PluginType;
import lombok.Data;

@Data
public class PluginManifest {

	private int schemaVersion;
	private String name;
	private String id;
	private String version;
	private String description;
	private String author;
	private String license;
	private String website;
	private String pageUrl;
	private String docUrl;
	private PluginType type;
	private List<String> quickViewProviders = new ArrayList<>();

}
