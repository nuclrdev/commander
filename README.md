# Nuclr Commander

**Nuclr Commander** is a cross-platform, keyboard-first desktop file manager for developers. It follows the classic dual-pane commander model, embeds a terminal, and pushes most format- and workflow-specific behavior into signed runtime plugins.

The `commander` repository contains the core Java application. In a typical local workspace it works together with sibling repositories for the plugin SDK, core plugins, and launcher.

## Highlights

- Dual-pane commander UI for fast navigation and copy/move workflows
- Keyboard-centric interaction with function-key actions and quick-view support
- Embedded terminal console using PTY4J and JediTerm
- Signed plugin loading from the local `plugins/` directory
- Theme-aware Swing UI built on FlatLaf
- Plugin extension points for file panels, quick viewers, and full-screen screens

## Repository Layout

- `src/main/java/dev/nuclr/commander` - application entrypoint, UI, services, events, plugin loading
- `src/main/resources/dev/nuclr/commander` - app configuration and bundled resources
- `plugins/` - signed plugin ZIPs loaded at runtime by the local app build
- `data/` - icons, fonts, splash assets, and other desktop resources

Related sibling repositories used by this workspace:

- `../plugins-sdk` - Java SDK for authoring Nuclr plugins
- `../plugins` - source for official/core plugins
- `../launcher` - native launcher and packaging work

## Technology Stack

- Java 21
- Maven 3.9+
- Swing
- Spring Context
- FlatLaf
- PTY4J + JediTerm
- Jackson
- RSyntaxTextArea
- SLF4J + Logback
- Caffeine

## Plugin Model

Nuclr currently supports three plugin categories:

- `FilePanelProvider` plugins for left/right commander panes
- `QuickViewProvider` plugins for inline previews
- `ScreenProvider` plugins for full-screen tools

Plugins are loaded from the folder configured by [`src/main/resources/dev/nuclr/commander/app.properties`](C:\nuclr\sources\commander\src\main\resources\dev\nuclr\commander\app.properties), which currently points at `plugins/`. Each plugin is packaged as a ZIP plus a detached `.sig` file. Commander verifies the signature before activating the plugin.

## Bundled Runtime Plugins

The current `plugins/` directory in this repository includes these signed plugins:

| Plugin | Type | What it adds |
| --- | --- | --- |
| `filepanel-fs` | File panel | Local filesystem roots for the main commander panes |
| `filepanel-zip` | File panel | Archive browsing for ZIP, JAR, TAR, RAR, GZ and related formats |
| `quick-view-image` | Quick view | Fast built-in preview for common images such as BMP, JPG, PNG, GIF |
| `imagemagick-bridge` | Quick view | Expanded image preview through a local ImageMagick 7 installation |
| `quick-view-pdf` | Quick view | PDF rendering, page navigation, cache, metadata overlay, optional CLI backends |
| `quick-view-text` | Quick view | Syntax-highlighted preview for text and source files, including files inside archives |
| `quick-view-jvm` | Quick view | Vineflower-based `.class` decompilation preview |
| `quick-view-executables` | Quick view | PE, ELF, and Mach-O metadata preview |
| `quick-view-archive` | Quick view | Read-only archive inspection with metadata and bounded listings |
| `quick-view-3d` | Quick view | 3D model statistics, bounds, textures, and import warnings |
| `quick-view-torrent` | Quick view | Torrent metadata, trackers, info hash, and magnet link preview |
| `quick-view-sdl2-music` | Quick view | Audio playback and waveform preview through SDL2/SDL2_mixer |

## Additional Core Plugins In The Workspace

The sibling repository at `C:\nuclr\sources\plugins\core` also contains plugin sources and READMEs for additional capabilities that are not currently present in this repo's `plugins/` folder:

- `filepanel-net` - SFTP/SCP remote panel support
- `filepanel-github` - read-only GitHub repository browsing through `gh`
- `screenpanel-text-editor` - full-screen syntax-highlighted text editor screen

Those plugin READMEs are the authoritative source for their build/runtime details.

## Current Capabilities

With the current bundled plugin set, Commander is already positioned for workflows such as:

- Browsing local disks and entering supported archive files directly from the panel
- Previewing text, source code, PDFs, images, JVM classes, executables, torrents, archives, audio, and 3D assets
- Extending image support through a locally installed ImageMagick
- Running quick inspections without leaving the file manager
- Loading extra capabilities as signed plugins instead of baking everything into the core app

## Requirements

- Java 21+
- Maven 3.9+
- UTF-8 capable environment

## Build

The `commander` module depends on the locally installed `dev.nuclr:plugins-sdk:1.0.0` artifact.

Install the SDK first:

```bash
cd ../plugins-sdk
mvn clean install -DskipTests
```

Then build Commander:

```bash
cd ../commander
mvn clean package
```

This produces a shaded desktop application JAR in `target/`.

## Run

Start the application with:

```bash
java -jar target/commander-0.0.1-SNAPSHOT.jar
```

Example with explicit heap settings:

```bash
java -Xms64m -Xmx512m -jar target/commander-0.0.1-SNAPSHOT.jar
```

## Working With Plugins During Development

Commander loads plugins from `plugins/` at startup. For a normal local run you usually need:

- the core app built successfully
- plugin ZIPs present in `plugins/`
- matching `.sig` files beside each ZIP

If you are building plugin sources from `../plugins/core`, follow the individual plugin README for signing and deployment steps, then copy or deploy the generated ZIP and `.sig` into this repository's `plugins/` directory.

## Development Notes

- Entrypoint: `dev.nuclr.commander.Nuclr`
- Main desktop shell: `dev.nuclr.commander.ui.MainWindow`
- Plugin loading: `dev.nuclr.commander.service.PluginLoader`
- Runtime registration/events: `dev.nuclr.commander.service.PluginRegistry`

## License

Apache 2.0. See [LICENSE](LICENSE).
