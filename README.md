# Nuclr Commander

**Nuclr Commander** is a cross-platform, terminal-based file and resource manager inspired by classic *Norton Commander*–style UIs, built for developers.

It provides a **unified TUI interface** for working with:
- Local filesystems
- Remote systems (FTP / SFTP / SSH)
- Archives
- Cloud and developer services (via connectors)

The core client is intentionally small and stable, while most functionality lives in **pluggable connectors**.

---

## ✨ Features

- 🖥️ **Terminal UI (TUI)** — fast, keyboard-driven, works over SSH
- 📁 **Dual-pane commander layout**
- 🔌 **Plugin / Connector architecture**
- 🧩 **Language-agnostic connectors** (connectors can be external processes)
- ⚡ **Java 21** (modern JVM, strong performance)
- 🧰 **Built-in essentials**
  - Local filesystem browsing
  - Archive explorer (view, extract, create)
- 🔐 **Secure by design**
  - No long-lived credentials in the client
  - Short-lived connector processes
- 🧪 **Developer-friendly**
  - Simple debugging
  - Minimal runtime dependencies
  
  ---

## 🚀 Getting Started

### Requirements

- Java **21+**
- Maven **3.9+**
- Terminal with UTF-8 support

### Build

```bash
mvn clean package

java -jar target/nuclr-commander.jar

JAVA_OPTS="-Xms64m -Xmx512m" java -jar target/nuclr-commander.jar

```


