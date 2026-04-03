/*

	Copyright 2026 Sergio, Nuclr (https://nuclr.dev)
	
	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at
	
	http://www.apache.org/licenses/LICENSE-2.0
	
	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

*/
package dev.nuclr.commander.plugin;

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
