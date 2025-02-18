package io.canvasmc.canvas.server.chunk;

import ca.spottedleaf.concurrentutil.util.Priority;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.concurrent.*;
import com.google.common.collect.Lists;

public class ChunkPriorityManager {
    private final Map<Player, ChunkPos> playerChunks = new ConcurrentHashMap<>();
    public final List<ChunkPos> blockingOverride = Lists.newCopyOnWriteArrayList();

    public enum TaskType {
        GEN, LOAD, FULL, LIGHT;
    }

    public void trackPlayerMovement(Player player, BlockPos blockPos) {
        playerChunks.put(player, new ChunkPos(blockPos));
    }

    public Priority getPriority(int chunkX, int chunkZ, TaskType taskType) {
        if (playerChunks.isEmpty()) return Priority.LOWEST;
        if (blockingOverride.contains(new ChunkPos(chunkX, chunkZ))) return Priority.BLOCKING;

        int closestDistance = Integer.MAX_VALUE;

        for (ChunkPos playerChunk : playerChunks.values()) {
            int dx = Math.abs(playerChunk.x - chunkX);
            int dz = Math.abs(playerChunk.z - chunkZ);
            int distance = dx + dz;
            closestDistance = Math.min(closestDistance, distance);
        }

        Priority basePriority = calculateBasePriority(closestDistance);

        if (taskType == TaskType.GEN && basePriority.ordinal() > 0) {
            return Priority.values()[basePriority.ordinal() - 1];
        }

        return basePriority;
    }

    private Priority calculateBasePriority(int distance) {
        if (distance <= 1) return Priority.HIGHEST;
        if (distance <= 3) return Priority.HIGHER;
        if (distance <= 6) return Priority.HIGH;
        if (distance <= 10) return Priority.NORMAL;
        if (distance <= 15) return Priority.LOWER;
        return Priority.LOWEST;
    }

    public void resetTracking() {
        playerChunks.clear();
    }
}
