# CollisionMc
![CollisionMc-final](https://github.com/user-attachments/assets/8dc8be06-f583-476e-8cc5-f8ad2f0ddd06)

[![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)  
[![GitHub stars](https://img.shields.io/github/stars/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas)  
[![GitHub forks](https://img.shields.io/github/forks/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas)  

CollisionMc is a fork of [CanvasMC](https://canvasmc.io) that aims to optimize Server-side performance for either Low-End PCs, Bad Internet Connections, or Low-Player Counts. Example: Under 16 players.
Getting as much help as possible is incredibly important, and this project is open to contributions!

Versions for Minecraft 1.8.x, 1.12.x, 1.16.x, 1.21.x, and onwards will be supported!

## The current TODOs for CanvasMC are:

### Rewritten Scheduler
- Canvas is primarily based on a rewritten scheduler for Folia, which makes Canvas extremely fast in comparison to other forks.

### Optimized Chunk Generation
- With fixed linear scaling through a complete rewrite of the chunk system executor, Canvas achieves **unparalleled chunk performance** compared to other forks.

## Our TODOs for CollisionMc are:

```
- Get a working build functional.
- I could not find it again but add patches from a PaperMC fork for Small Redstone Creative Server.
- Fork patches from Pufferfish that were not added in Purpur and add them with compatible changes for other plugins.
- Look into PlazmaBukkit/Thunderbolt, PaperBin, DivivneMc, & Sharkur patches.
- Add native support for Linear regions and slime regions.
``` 

### Proper Region Profiling
- Canvas introduces a genuine Spark profiler that is fully compatible with region threading, replacing the limited Folia profiling engine.

- [Website](https://nocollision.uk/our-servers/collisionmc)
- [Documentation](https://docs.canvasmc.io)
- [Discord](https://discord.gg/E6y75w3Z3Z)

## Running CollisionMc

## Getting Started

### Downloading & Running

1. Download the latest server JAR from the **Downloads** page on [canvasmc.io](https://canvasmc.io/downloads).  
2. Launch using Java (Java 21+ required) with your preferred arguments and configuration.

You can download the server jar from the RESERVED.

## Building CollisionMc

- Java 24
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
  mavenCentral()
  maven("https://repo.papermc.io/repository/maven-public/")
}
dependencies {
  implementation("io.canvasmc.canvas:canvas-api:1.21.8-R0.1-SNAPSHOT")
}
```

Replace the version number with the appropriate version you want to target.

---

## REST API

Canvas provides a REST interface to fetch build and version metadata. Documentation is available via [The docs page](https://docs.canvasmc.io/developers/rest-api)

---

## Documentation & Resources

* **Official Documentation**: [https://docs.canvasmc.io](https://docs.canvasmc.io)
* **Community & Support**: Join the Canvas [Discord](https://canvasmc.io/discord)
* **Issue Tracker / Contributing**: Use this GitHub repo for reporting bugs, proposing features, and submitting pull requests
* **Donations / Sponsorship**: Support development on [Ko-fi](https://ko-fi.com/dueris)

---

## Contributing

We welcome many forms of contributions:

You can help CollisionMc grow by:

- Starring the project on GitHub
- Contributing code or documentation
- [Joining our Discord](https://discord.gg/E6y75w3Z3Z)

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
