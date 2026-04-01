package dev.nuclr.commander.plugin;

import java.util.List;

import lombok.Data;

@Data
public class PluginDescriptor {

	/*
	
	{
		"schemaVersion": 1,
		"name": "Executable Quick Viewer",
		"id": "dev.nuclr.plugin.core.quickviewer.executables",
		"version": "1.0.0",
		"description": "A quick viewer for PE, ELF and Mach-O executables and libraries.",
		"author": "Nuclr Development Team",
		"license": "Apache-2.0",
		"website": "https://nuclr.dev",
		"pageUrl": "https://nuclr.dev/plugins/core/executable-quick-viewer.html",
		"docUrl": "https://nuclr.dev/plugins/core/executable-quick-viewer.html",
		"type": "Official"
	}
	
	 */
	
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
	private String type;
	
}
