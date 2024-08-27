<img src="readme_top.png">

<center><b style="font-size:29px">Fork of <a href="https://github.com/PurpurMC/Purpur">PurpurMC</a> adding multithreaded dimensions and more</b></center>

## Significant Optimizations
Canvas adds a ton of new optimizations to the game, including AI optimizations, farm optimizations, and large-scale threading optimizations

### Multithreaded Dimension Ticking
Canvas' most significant optimization is Multithreaded 
Dimension Ticking(MDT), which turns each world into its 
own mini-server. NMS has a `BlockableEventLoop` class, 
which acts as a scheduler for ticking repeated tasks on 
a single thread. The main performance drawback of normal 
servers are the fact that it ticks almost entirely on a 
single thread. Folia attempts to fix this bottleneck by 
threading the server in `regions`, which has caused 
numerous issues like breaking plugin compatibility, 
disabling numerous features and commands, and tons of 
bugs and crashes. To fix this, Canvas attempts at making 
the server combine work using multiple threads for 
ticking 1 single server.

#### Threading Logic
The main thread tick loop has been stripped of most 
logic having to do with World ticking, since each world 
now is an extension of a `TickLoopThread`, which is an 
abstract implementation of the NMS `BlockableEventLoop` 
that handles most of the logic for ticking each world.

After startup, each world(including ones added by plugins 
and datapacks) gets booted into its own thread to begin 
ticking, and at that point each world ticks 
independently and is completely split off from the main 
thread, similar to Folias region logic, but instead of a 
thread pool executing the ticks, it processes their own 
tick on their own.

Upon a player joining the server, the main thread will 
handle the joining logic like creating the player, 
building the playerdata, etc. Once its ready for 
"gameplay" stages(or the player is now in runtime), the 
packet handler is sent to its currently owned world to 
be processed, meaning each world will handle its own 
players connections/disconnections. If the world is 
crashing/lagging, the main thread will be used as 
fallback until the world thread is stable again.

In the end, this essentially makes each world its own 
server, interacting with each other and working 
concurrently to provide a huge boost in performance. 
Some users have seen immense improvements in MSPT, and 
even with hundreds of players, it can maintain 20TPS.

### Entity Optimizations
There are numerous optimizations added by Canvas in 
relation to entities, like the following:
- Async Pathfinding
- Experience Orb optimizations
- Entity Goal optimizations
- Entity Pathfinding optimizations

#### Entity Goal / Pathfinding optimizations
An odd part of Minecraft pathfinding is its nature to 
continue attempting to pathfinding despite the entity 
being in an enclosed space or crammed with other 
entities, making it unable to move, despite the 
pathfinder still trying to. This causes unnecessary 
processing that takes up a lot of server resources fast 
for mob farms like Enderman farms. To fix this, Canvas 
pre-calculates the entities currently intersecting with 
its bounding box, meaning if its crammed with a 
configurable amount of entities(default 2, which means 
its crammed with a lot of entities since normally 
entities shouldnt be intersecting with each others 
bounding boxes), the pathfinder will be turned off for 
that tick, saving a lot of unneeded resources on mob farms.

Entity goals have also been optimized, reducing the 
rates of strolling and extremely expensive mob goals by 
a configurable amount to allow for less time spent on 
goal ticking, while also making it still look natural.

#### Experience Orb optimizations
With giant XP farms, a lot of XP orbs are created and 
get "merged" by spigots XP orb optimization. This 
optimization works, but only to a specific degree. It 
does "merge" the orbs, but it mostly just moves the orbs 
to the same location and thats it, which doesnt do as 
much in the long run. There is now a configuration that 
allows for better merging of these orbs, allowing for 
making the entity truly into a single entity with the 
combined XP count of all the orbs. This reduces the 
amount of entities processed on the server and client, 
often times improving framerates in heavy mob farm 
situations.

## FAQ

#### What kind of hardware is needed to run Canvas?
Generally, at least 3-4 more threads than you normally 
would to account for the new WorldTicker threads.

#### Why not just use Folia?
Folia is more experimental software, and has a ton of 
bugs. Canvas is more of an in-between of Paper and Folia,
bringing Folia-like optimizations but to a less severe 
degree and reworked to be more plugin-compatible and 
more performant. Plugin compatibility is a lot better 
than Folia, just because of the nature of how the both 
of them work. However, the plugin does have to be 
thread-safe in most situations to be considered 
compatible with Canvas.
