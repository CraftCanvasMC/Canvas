![title](./canvas_title.png)

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)  
[![GitHub stars](https://img.shields.io/github/stars/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas)  
[![GitHub forks](https://img.shields.io/github/forks/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas)  

**CanvasMC** is a high-performance fork of the Folia Minecraft server software. It addresses gameplay inconsistencies and bugs, while introducing performance optimizations and enhancements to the dedicated server.





---
[![bStats Graph Data](https://bstats.org/signatures/server-implementation/Canvas.svg)](https://bstats.org/plugin/server-implementation/Canvas)
---

## Features & Highlights

### Rewritten Scheduler
- Canvas is primarily based on a rewritten scheduler for Folia, which makes Canvas extremely fast in comparison to other forks.

### Optimized Chunk Generation
- With fixed linear scaling through a complete rewrite of the chunk system executor, Canvas achieves **unparalleled chunk performance** compared to other forks.

### Extensive Configuration
- Fine-tune aspects of your server with fully documented configuration options and performance settings.

### Proper Region Profiling
- Canvas introduces a genuine Spark profiler that is fully compatible with region threading, replacing the limited Folia profiling engine.

### Powerful and Optimized
- By fixing **numerous** Folia bugs and crashes, Canvas delivers a high-performance, stable, and reliable experience.

---

## Getting Started

### Downloading & Running

1. Download the latest server JAR from the **Downloads** page on [canvasmc.io](https://canvasmc.io/downloads).  
2. Launch using Java (Java 21+ required) with your preferred arguments and configuration.

### Building from Source

**Requirements:**

- Java 21
- Git (configured with name/email)

**Common build commands:**

```bash
./gradlew applyAllPatches
./gradlew createMojmapPublisherJar
./gradlew runDevServer
```

There is also a helper script:

```bash
./rebuildPatches
```

which regenerates patches for modified directories.

---

## Using the Canvas API in Plugins

You can use Canvas’s API in your own Minecraft plugins. Here’s an example of how to include it in your `build.gradle.kts`:

```kotlin
repositories {
    maven {
        name = "Canvas"
        url = uri("https://maven.canvasmc.io/snapshots")
    }
}

dependencies {
    compileOnly("io.canvasmc.canvas:canvas-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
```

Replace the version number with the appropriate version you want to target.

---

## REST API

Canvas provides a REST interface to fetch build and version metadata. Documentation is available via [The docs page](https://docs.canvasmc.io/guides/developers/rest-api/)

---

## Documentation & Resources

* **Official Documentation**: [https://docs.canvasmc.io](https://docs.canvasmc.io)
* **Community & Support**: Join the Canvas [Discord](https://canvasmc.io/discord)
* **Issue Tracker / Contributing**: Use this GitHub repo for reporting bugs, proposing features, and submitting pull requests
* **Donations / Sponsorship**: Support development on [Ko-fi](https://ko-fi.com/dueris)

---

## Contributing

We welcome many forms of contributions:

* Code (bug fixes, features)
* Documentation improvements
* Testing & bug reporting
* Community help & support
* Donations to help support the developers

See the [Canvas Contributing Guide](https://docs.canvasmc.io/guides/developers/contributing/canvas/) for more detail.

---

## Compatibility & Notes

* Canvas is a fork of **Folia** and is *not* a drop-in replacement for Purpur, Paper, or other non-Folia forks. It is intended primarily for environments already using Folia or Folia-based forks.
* The project adheres strictly to Folia’s threading and safety rules and does *not* permit bypassing them.

---

## License

This project is licensed under the **GNU General Public License v3.0 (GPL-3.0)**.

---

## Acknowledgments & Inspiration

Canvas incorporates patches inspired by or derived from other high-performance projects (e.g. **Lithium**), along with its own custom optimizations.
