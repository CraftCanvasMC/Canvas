package io.canvasmc.canvas;

import io.canvasmc.canvas.scheduler.MultithreadedTickScheduler;
import org.bukkit.World;
import org.jetbrains.annotations.Contract;
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
    LevelAccess getLevelAccess(World world);
    MultithreadedTickScheduler getScheduler();

    void scheduleOnMain(Runnable runnable);
}
