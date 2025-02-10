# Canvas

[![GitHub License](https://img.shields.io/github/license/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas/blob/master/LICENSE)
[![GitHub contributors](https://img.shields.io/github/contributors/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas/graphs/contributors)
[![Discord](https://img.shields.io/discord/1168986665038127205?color=5865F2)](https://canvasmc.io/discord)

CanvasMC is a high-performance Minecraft server software focused on maximizing server performance while maintaining plugin compatibility. Built on top of [Purpur](https://github.com/PurpurMC/Purpur), it implements various experimental optimizations to achieve significant performance improvements.

Useful links:

- [Website](https://canvasmc.io)
- [Documentation](https://docs.canvasmc.io) (Work in progress)
- [Discord](https://canvasmc.io/discord)

## Key Features

- Multithreaded Dimension Ticking (MDT)
- Chunk Generation Optimizations
- Command Optimizations
- Entity Improvements

## Requirements

> [!WARNING]
> Canvas requires Java 22 or higher. It will not run on older Java versions as it uses Foreign Function & Memory API. You can read more about it [here](https://openjdk.org/jeps/454).

### Running Canvas

- Java 22 or higher

### Building Canvas

- Java 22 or higher
- Git (with configured email and name)
- Gradle

## Scripts

```bash
> ./gradlew applyAllPatches              # apply all patches
> ./gradlew createMojmapPaperclipJar     # build the server jar
> ./gradlew rebuildPatches               # generate patches
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
