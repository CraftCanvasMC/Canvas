package io.canvasmc.canvas;

import io.canvasmc.canvas.region.Region;
import io.canvasmc.canvas.scheduler.MultithreadedTickScheduler;
import org.bukkit.World;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import java.util.List;

public interface ThreadedBukkitServer {

    class InstanceHolder {
        private static volatile ThreadedBukkitServer instance;
    }

    /**
     * Returns the {@link ThreadedBukkitServer} instance for the runtime
     */
    @Contract(pure = true)
    static ThreadedBukkitServer getInstance() {
        return InstanceHolder.instance;
    }

    @ApiStatus.Internal // don't use, this is for internal use only.
    static void setInstance(ThreadedBukkitServer server) {
        if (server == null) throw new IllegalArgumentException("ThreadedServer instance cannot be null");
        synchronized (InstanceHolder.class) {
            if (InstanceHolder.instance != null) {
                throw new IllegalStateException("ThreadedServer instance already set");
            }
            InstanceHolder.instance = server;
        }
    }

    @Unmodifiable
    List<World> getWorlds();

    /**
     * Gets the level access, which allows accessing internals of the tick task specifically tied to the world
     * like scheduling, and the world-independent scheduler.
     * @param world the bukkit world
     * @return the world's level access
     */
    LevelAccess getLevelAccess(World world);

    /**
     * Gets the tick scheduler for Canvas, allowing scheduling and fetching of tick tasks
     * @return the scheduler
     */
    MultithreadedTickScheduler getScheduler();

    /**
     * Schedules a task on the main thread
     * @param runnable task
     */
    void scheduleOnMain(Runnable runnable);

    /**
     * Gets the region at the specified chunk. If regionizing is disabled, this returns null, or if the chunk is not loaded.
     * @param chunkX x coord
     * @param chunkZ z coord
     * @return the region
     */
    @Nullable
    Region getRegionAtChunk(World world, int chunkX, int chunkZ);
}
