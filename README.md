# Nuclr Commander

**Nuclr Commander** is a cross-platform, keyboard-first file manager for developers, inspired by classic commander-style tools and built around a plugin-driven architecture.

At the moment, the codebase is a **Java desktop application** with a dual-pane layout, an embedded terminal console, quick-view support, and full-screen plugin screens such as a text editor.

## ✨ Highlights

- 🗂️ **Dual-pane commander UI** for fast navigation and file operations
- ⌨️ **Keyboard-centric workflow** with function-key actions and panel focus switching
- 🔌 **Plugin architecture** for panels, quick viewers, and full-screen tools
- 🧭 **Multiple resource types** including local filesystems, archives, remote systems, and service-oriented panels
- 🖥️ **Embedded terminal console** powered by PTY/JediTerm components
- 🎨 **Theme-aware UI** with FlatLaf and persisted local settings
- 🔒 **Signed plugin loading** with ZIP signature verification before runtime activation
- 📦 **Bundled core plugins** for filesystem, ZIP, quick view, and editor capabilities

## 🏗️ Project Layout

This repository contains the **core Commander application**.

Key areas:

- `src/main/java/dev/nuclr/commander` - application startup, UI, services, events, common utilities
- `src/main/resources/dev/nuclr/commander` - app properties and bundled resources
- `plugins/` - packaged plugin ZIPs loaded by the app at runtime
- `data/` - icons, splash assets, and fonts used by the desktop client

Related sibling repositories in the broader workspace:

- `../plugins-sdk` - Java SDK for writing Nuclr plugins
- `../plugins` - source for core plugins packaged into ZIPs
- `../launcher` - native C++ launcher for bundled distributions

## 🧱 Technology Stack

### Core Application

- ☕ **Java 21**
- 🛠️ **Maven**
- 🌱 **Spring Context** for wiring and lifecycle
- 🪟 **Swing** for the desktop UI
- 🎨 **FlatLaf** for look and feel
- 🖲️ **PTY4J + JediTerm** for embedded terminal support
- 📝 **RSyntaxTextArea** for editor/viewer functionality
- 🧾 **Jackson** for config and manifest parsing
- 📚 **SLF4J + Logback** for logging
- ⚡ **Caffeine** for caching
- 🧰 **Lombok** for reduced boilerplate

### Plugin / Connector Model

Nuclr currently supports three main plugin types:

- 📁 **Panel providers** for left/right commander panes
- 👀 **Quick-view providers** for inline previews
- 🪟 **Screen providers** for full-screen tools such as editors

Plugins are packaged as signed ZIP files containing:

- `plugin.json`
- plugin JARs
- runtime dependencies in `lib/`
- a detached `.sig` file used during verification

## 🚀 Current Capabilities

Based on the source tree and bundled plugins, Nuclr Commander currently targets workflows such as:

- 📂 Browsing the local filesystem
- 🗜️ Opening ZIP-like archives as panels
- 🌐 Connecting to remote systems through the network panel stack
- 🐙 Exploring GitHub-oriented resources via plugin panels
- 👁️ Previewing text and other supported formats through quick-view plugins
- ✍️ Opening readable files in a full-screen text editor screen

## 📋 Requirements

- Java **21+**
- Maven **3.9+**
- A UTF-8 capable environment

## 🔨 Build

The `commander` module depends on the local `plugins-sdk` artifact.
If you are building from the multi-repo workspace, install the SDK first:

```bash
cd ../plugins-sdk
mvn clean install -DskipTests
```

Then build Commander:

```bash
cd ../commander
mvn clean package
```

## ▶️ Run

Run the shaded application JAR:

```bash
java -jar target/commander-0.0.1-SNAPSHOT.jar
```

If you want to provide custom JVM memory settings:

```bash
java -Xms64m -Xmx512m -jar target/commander-0.0.1-SNAPSHOT.jar
```

## 🔌 Runtime Plugins

At startup, Commander scans the local `plugins/` directory configured in `src/main/resources/dev/nuclr/commander/app.properties` and attempts to load signed plugin ZIPs.

That means a normal local development run typically depends on:

- the core app being built
- plugin ZIPs being present in `plugins/`
- matching `.sig` files being present beside each ZIP

## 🧪 Development Notes

- The application entrypoint is `dev.nuclr.commander.Nuclr`
- The main desktop shell lives in `dev.nuclr.commander.ui.MainWindow`
- Plugin loading is handled by `dev.nuclr.commander.service.PluginLoader`
- Runtime plugin registration and event dispatch live in `dev.nuclr.commander.service.PluginRegistry`

## 📄 License

Apache 2.0. See [LICENSE](LICENSE).
