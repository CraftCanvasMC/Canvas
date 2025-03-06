# Canvas

[![GitHub License](https://img.shields.io/github/license/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas/blob/master/LICENSE)
[![GitHub contributors](https://img.shields.io/github/contributors/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas/graphs/contributors)
[![Discord](https://img.shields.io/discord/1168986665038127205?color=5865F2)](https://canvasmc.io/discord)

CanvasMC is a high-performance Minecraft server software focused on maximizing server performance while maintaining plugin compatibility. Built on top of [Purpur](https://github.com/PurpurMC/Purpur), it implements various experimental optimizations to achieve significant performance improvements.

Useful links:

- [Website](https://canvasmc.io)
- [Documentation](https://docs.canvasmc.io)
- [Discord](https://canvasmc.io/discord)

## Key Features

- Multithreaded Dimension Ticking (MDT)
- Chunk Generation Optimizations
- Command Optimizations
- Entity Improvements

## Running Canvas

### Requirements

- Java 22 or higher

### Obtaining Server Jar

You can download the server jar from the [downloads page](https://canvasmc.io/downloads).

## Building Canvas

### Requirements

- Java 22 or higher
- Git (with configured email and name)
- Gradle

### Scripts

```bash
> ./gradlew applyAllPatches              # apply all patches
> ./gradlew createMojmapPaperclipJar     # build the server jar
> ./gradlew runDevServer                 # run dev server
> ./rebuildPatches                       # custom script to generate patches for modified directories
```

## REST API

Canvas has a REST API that can be used to get builds and check for new versions.

It is temporarily documented in the [Website Repository](https://github.com/CraftCanvasMC/Website/blob/main/docs/API.md). Soon it will be moved over to the documentation website.

## Support

You can help CanvasMC grow by:

- Supporting us on [Ko-fi](https://ko-fi.com/dueris)
- Starring the project on GitHub
- Contributing code or documentation

Your support helps keep this project active and improving!

## License

Canvas is licensed under the [GNU AGPLv3](https://github.com/CraftCanvasMC/Canvas/blob/master/LICENSE). <img align="right" width="100" src="https://upload.wikimedia.org/wikipedia/commons/thumb/0/06/AGPLv3_Logo.svg/1200px-AGPLv3_Logo.svg.png" alt="AGPLv3 Logo">
