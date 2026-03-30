package dev.nuclr.commander.plugin;

import java.util.List;

import lombok.Data;

@Data
public class PluginDescriptor {

	private int schemaVersion;
	private String name;
	private String id;
	private String version;
	private String description;
	private String type;
	private String author;
	private String license;
	private String website;
	private String pageUrl;
	private String docUrl;
	private List<String> panelProviders = List.of();
	private List<String> quickViewProviders = List.of();
	private List<String> screenProviders = List.of();
}
