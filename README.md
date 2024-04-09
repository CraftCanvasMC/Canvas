<br>
<div align="center">
  <a href="https://gyazo.com/d7be938a9e911f14b106d5c8f1bf12b1"><img src="https://i.gyazo.com/d7be938a9e911f14b106d5c8f1bf12b1.png" alt="Image from Gyazo" width="620"/></a>
</div>
<!DOCTYPE html>

<html>
<body>
  <h1>Welcome to Canvas</h1>
  <p><em>A Minecraft ServerType aimed for creativity and performance.</em></p>

  <h2>Introduction</h2>

  <p>Canvas is a ServerType for Minecraft based on Purpur that tries to give plugin developers unlimted freedom when making plugins,
   while keeping the server quick and performant.</p>

  <h2>Why use Canvas?</h2>
  <p>Canvas has an active development team constantly working on fixes and improvements, making new apis, features, and more. Canvas has been
   put to the test vs Paper and Purpur and definetly beats them in both performance, and api features. Its built to handle whatever you throw at it, so the only limit, is your imagination.</p>

  <h2>Features</h2>
  <ul>
    <li>Multithreaded world ticking</li>
    <ul>
        <li>In VanillaMC, it uses a "Ping-Pong" method for ticking worlds, aka ticking 1 world at a time. This can cause a ton of performance issues for servers with multiple worlds, or busy ones. Canvas now makes it so that it ticks in "batches" on different threads to tick them all at once to save on tick times, submitting them like "worlds 1-3 tick, then worlds 4-6, etc". Theres even a config for how many threads Canvas will use, which also changes how many worlds are allowed to tick at once(default 3).</li>
    </ul>
    <li>Builtin Mixin</li>
    <li>New and Enhanced APIs</li>
    <li>Entity Goal/AI Optimizations</li>
    <li>Threaded ConnectionHandlers(Multithreaded PacketHandlers)</li>
    <li>Experience Orb Optimizations</li>
    <ul>
        <li>Properly integrates XP Orb merging to save on the amount of entities being ticked</li>
        <li>Configurable to make players instantly absorb XP orbs</li>
    </ul>
    <li>Optimize Entity Pathfinding</li>
    <ul>
        <li>Reduces the amount of times the Pathfinder gets ticked for an entity when bunched up with multiple entities in the same space. This should improve performance a ton with giant mob-farms or XP farms, or general senerios where lots of mobs are bunched together</li>
    </ul>

  </ul>

  <h2>Resources</h2>
  <ul>
    <li>GitHub Repository: <a href="https://github.com/CraftCanvasMC/Canvas">https://github.com/CraftCanvasMC/Canvas</a></li>
    <li>Donation: <a href="https://ko-fi.com/dueris">https://ko-fi.com/dueris</a></li>
  </ul>

  <h2>Join Our Community</h2>
  <p>Join our Discord server to stay updated and connect with the Canvas community:</p>
  <a href="https://discord.com/invite/hs7EYwWf4G"><img src="https://1000logos.net/wp-content/uploads/2021/06/Discord-logo-2015.png" alt="Discord Icon" width="250" height="150"></a>

  <h2>Contributing</h2>
  <p>We welcome contributions from the community. If you'd like to contribute to Canvas, please read our <a href="CONTRIBUTING.md">contributing guidelines</a> for more information.</p>

  <h2>License</h2>
  <p>Canvas is open-source software licensed under the GNU General Public License version 3 License</a>.</p>

  <h2 align="left"><strong font-size="202px">Sponsored by BisectHosting!</strong></h2>
    <a href="https://bisecthosting.com/DUERIS"><img src="https://i.ibb.co/Rg0qD2V/a03f0848-c1da-4967-9c40-f56cc36ef03c.webp" alt="a03f0848-c1da-4967-9c40-f56cc36ef03c" border="0"></a>
    </div>

<hr>
  <p align="center">
    <em>Canvas - Empowering the Impossibe</em>
    <br>
    <img src="canvas-logo.png" alt="Canvas Logo" width="30" height="30">
  </p>
</body>
</html>
