package io.canvasmc.canvas.region;

import io.canvasmc.canvas.scheduler.WrappedTickLoop;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface Region {
    /**
     * Gets the long chunk keys for the region. This can be translated from long -> chunk via {@link Region#long2Chunk(long, World)}
     * @return fastutil long arraylist of long packed positions
     */
    LongArrayList getOwnedChunkPositions();

    /**
     * Gets the center chunk as a packed position
     * @return the center chunk
     */
    @Nullable
    Long getCenterChunk();

    /**
     * Translates a long packed position -> bukkit chunk
     * @param chunkKey packed position
     * @param world world of the chunk
     * @return the chunk
     */
    static @NotNull Chunk long2Chunk(long chunkKey, @NotNull World world) {
        int x = (int)chunkKey;
        int z = (int)(chunkKey >> 32);
        return world.getChunkAt(x, z);
    }

    /**
     * Gets the world that the region is owned in
     * @return the world
     */
    World getWorld();

    /**
     * Gets the region tick handle
     * @return the tick task impl for the region
     */
    WrappedTickLoop getTickHandle();
}
