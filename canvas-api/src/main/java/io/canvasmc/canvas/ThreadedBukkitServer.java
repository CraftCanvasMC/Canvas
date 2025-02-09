package io.canvasmc.canvas;

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

    boolean isLevelThread(long id);
    boolean isLevelThread(Thread thread);

    @Unmodifiable
    List<World> getWorlds();
    LevelAccess getLevelAccess(World world);

    void scheduleOnMain(Runnable runnable);
}
