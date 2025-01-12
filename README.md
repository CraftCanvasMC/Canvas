<img src="readme_top.png">

---

<center><b style="font-size:29px">Fork of <a href="https://github.com/PurpurMC/Purpur">PurpurMC</a> adding experimental, but heavy performance optimizations.</b></center>
<p></p>

> [!IMPORTANT]
> CANVAS REQUIRES JAVA VERSION 22 TO RUN.

**Canvas is a project created by Dueris in an attempt to create a more powerful and original servertype, unlike most servertypes out there.**

---

## Optimizations
### Multithreaded Dimension Ticking
Canvas' most significant optimization is Multithreaded Dimension Ticking(MDT). Made in a way to be more of an in-between of Paper and Folia, offering
insane levels of performance while still maintaining plugin compatiblity more(however not guarenteed), and making each world essentially its own
server with its own tick loop, making the main thread practically useless. This evenly distributes resources on the CPU to their respective worlds, offering
immense performance improvements. This also comes with a new `threadedtps` command, showing all the TPS of each world. Plugin-Made worlds also work with
MDT.
Alongside this, ServerPlayer Connection handling has been reworked to be threaded on the players respective world, meaning any players in the overworld are
handled and ticked on that thread, and same thing for each world and its players.
### Chunk Gen Optimizations
Canvas incorporates many optimizations from mods like C2ME to further improve the chunk generation performance of Canvas. Chunk performance has been
improved GREATLY, often doubling the CPS(Chunks Per Second) in comparison to Paper.
### Command Optimizations
Canvas also runs a few commands off-main, specifically ones that take up a ton of resources on the main thread, often pausing the server for seconds
at a time until they complete. These commands are the `locate` command, and the `spreadplayers` command.
### Entity Optimizations
Canvas implements a ton of more optimizations for entities, most notable async pathfinding, spawning improvements, threaded entity tracking, brain optimizations, and more.

---
These are just the more notable optimziations Canvas provides, but there are a TON more behind the scenes improving performance even more.
Please consider <a href="https://ko-fi.com/dueris">donating</a>! I (Dueris) have put tons of hours into this project to make it as stable and performant as possible.

Discord: https://discord.gg/cKUt92kbzj
