package io.canvasmc.canvas;

import io.canvasmc.canvas.region.WorldRegionizer;
import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/**
 * The server-wide shutdown handler: runs a shutdown task with the region context of a given location set on the
 * shutdown thread.
 *
 * <p>It runs immediately, and only during shutdown.
 *
 * <p>The shutdown thread owns every entity, but not every position at once. For position-based
 * thread checks to pass, the shutdown thread sets one field: the region it is currently shutting down. That field lets
 * the server save chunks and the like without violating thread checks or resorting to workarounds. While it is set, the
 * shutdown thread's thread-local region context returns it. While it is unset, the shutdown thread owns no region
 * data.
 *
 * <p>This handler fetches the region at the given location and sets it as that field, so a plugin
 * can run location-specific shutdown tasks from its {@link org.bukkit.plugin.java.JavaPlugin#onDisable()}.
 *
 * @author dueris
 */
public interface ServerShutdownHandler {

    void runShutdownAt(
        final Plugin plugin,
        final World world,
        final int chunkX,
        final int chunkZ,
        final Consumer<WorldRegionizer.ChunkRegion> scheduledTask
    );

    default void runShutdownAt(
        final Plugin plugin,
        final Location location,
        final Consumer<WorldRegionizer.ChunkRegion> scheduledTask
    ) {
        runShutdownAt(
            plugin,
            location.getWorld(),
            location.getBlockX() >> 4,
            location.getBlockZ() >> 4,
            scheduledTask
        );
    }

    List<World> getWorlds();
}
