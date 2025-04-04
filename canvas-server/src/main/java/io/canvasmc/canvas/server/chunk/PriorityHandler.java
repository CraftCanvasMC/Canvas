package io.canvasmc.canvas.server.chunk;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import static ca.spottedleaf.moonrise.common.util.MoonriseConstants.MAX_VIEW_DISTANCE;

public class PriorityHandler {
    public static final int MAX_PRIORITY = MAX_VIEW_DISTANCE + 2;
    public static final int BLOCKING = 0;

    public final ServerLevel level;
    public PriorityHandler(ServerLevel world) {
        this.level = world;
    }

    public int priority(int chunkX, int chunkZ) {
        int priority = MAX_PRIORITY;
        for (final ServerPlayer player : this.level.players()) {
            ChunkPos playerChunk = player.chunkPosition();
            int playerChunkX = playerChunk.x;
            int playerChunkZ = playerChunk.z;

            int dist = Math.max(Mth.abs(playerChunkX - chunkX), Mth.abs(playerChunkZ - chunkZ));
            int distPriority = Math.max(0, MAX_VIEW_DISTANCE - dist); // use the max view distance not priority, or else we can run blocking priorities with some chunks which we don't want.
            priority = Math.min(priority, distPriority);
        }
        return priority;
    }
}
