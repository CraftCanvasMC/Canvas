# CollisionMc

[![GitHub License](https://img.shields.io/github/license/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas/blob/master/LICENSE)
[![GitHub contributors](https://img.shields.io/github/contributors/CraftCanvasMC/Canvas)](https://github.com/CraftCanvasMC/Canvas/graphs/contributors)
[![Discord](https://img.shields.io/discord/1168986665038127205?color=5865F2)](https://canvasmc.io/discord)

CollisionMc is a fork of CanvasMC that aims to optimize Server-side performance for either Low-End PCs, Bad Internet Connections, or Low-Player Counts. Example: Under 16 players.
Getting as much help as possible is incredibly important, and this project is open to contributions!

Versions for Minecraft 1.8.x, 1.12.x, 1.16.x, 1.21.x, and onwards will be supported!

## The current TODOs for CanvasMC are:

```
- fix events(respawn, teleport events, etc)
- fix more folia commands
- fix scoreboard api
- reapply old canvas optimizations and configs -- i(Dueris) will handle this
```

## Our TODOs for CollisionMc are:

```
- Get a working build functional.
- I could not find it again but add patches from a PaperMC fork for Small Redstone Creative Server.
- Fork patches from Pufferfish that were not added in Purpur and add them with compatible changes for other plugins.
- Look into PlazmaBukkit/Thunderbolt, PaperBin, & Sharkur patches.
- Add native support for Linear regions and slime regions.
``` 

Useful links:

- [Website]([https://canvasmc.io](https://sites.google.com/view/nocollision-yt/our-servers/collisionmc)
- [Documentation](https://docs.canvasmc.io)
- [Discord](https://discord.gg/E6y75w3Z3Z)

## Running CollisionMc

### Requirements

- Java 22 or higher

### Obtaining Server Jar

You can download the server jar from the RESERVED.

## Building CollisionMc

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

You can help CollisionMc grow by:

- Starring the project on GitHub
- Contributing code or documentation

Your support helps keep this project active and improving!

## License

Canvas is licensed under the [GNU AGPLv3](https://github.com/CraftCanvasMC/Canvas/blob/master/LICENSE). <img align="right" width="100" src="https://upload.wikimedia.org/wikipedia/commons/thumb/0/06/AGPLv3_Logo.svg/1200px-AGPLv3_Logo.svg.png" alt="AGPLv3 Logo">
