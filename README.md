![Canvas MC](./canvas_title.png)

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![GitHub stars](https://img.shields.io/github/stars/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas)
[![GitHub forks](https://img.shields.io/github/forks/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas)

CanvasMC is a high-performance fork of Folia designed to provide a stable and consistent region-threaded environment, alongside extensive optimizations for large-scale servers.

---

[![bStats Graph Data](https://bstats.org/signatures/server-implementation/Canvas.svg)](https://bstats.org/plugin/server-implementation/Canvas)

---

## Features & Highlights

### Alternative Scheduler
Canvas provides a custom scheduler called `AFFINITY`, written by the team, with configurable options that significantly improve performance.

### Optimized Chunk Generation
Canvas replaces the default chunk system thread pool with a rewritten implementation, improving scaling and single-threaded throughput.

### Extensive Configuration
Fine-tune your server with fully documented configuration options and performance settings.

### Proper Region Profiling
Canvas introduces full Spark profiler support compatible with region threading, replacing Folia's limited profiling engine.

### Stable & High-Performance
Canvas fixes numerous Folia bugs and crashes, delivering a reliable and performant server experience.

---

## Getting Started

> **Requires Java 25+**

### Downloading & Running
1. Download the latest server JAR from the [Downloads page](https://canvasmc.io/downloads/canvas).
2. Launch with your preferred JVM arguments and configuration.

### Building from Source

**Requirements:**
- Java 25
- Git (configured with name and email)

```bash
./gradlew applyAllPatches   # Applies all patches to construct the Canvas source
./gradlew createPaperclipJar # Creates the Paperclip JAR
./gradlew runDev             # Starts a local development server
```

---

## Documentation & Resources

| Resource | Link |
|---|---|
| Official Docs | [docs.canvasmc.io](https://docs.canvasmc.io) |
| Community & Support | [Discord](https://canvasmc.io/discord) |
| Issue Tracker | [GitHub Issues](https://github.com/CraftCanvasMC/Canvas/issues) |
| Donations | [Ko-fi](https://ko-fi.com/dueris) |

---

## Contributing

We welcome contributions in any form:

- Bug fixes and new features
- Documentation improvements
- Testing and bug reports
- Community support

See the [Canvas Contributing Guide](https://docs.canvasmc.io/canvas/developers/contributing/canvas/) for details.

---

## Compatibility

Canvas is a fork of **Folia** and is **not** a drop-in replacement for Paper, Purpur, or other non-Folia forks. It is intended for environments already running Folia or Folia-based servers.

Canvas strictly adheres to Folia's threading and region safety rules — bypassing them is not supported.

---

## License

Licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

---

## Acknowledgments

Canvas incorporates patches inspired by or derived from [Lithium](https://github.com/CaffeineMC/lithium), [Leaf](https://github.com/Winds-Studio/Leaf), and other high-performance projects, alongside its own custom optimizations.