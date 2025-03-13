package io.canvasmc.canvas.server.chunk;

import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

public class ChunkPriorityManager {
    public static int IDLE = 1;
    public static int MAX_PRIORITY = Math.max(MoonriseConstants.MAX_VIEW_DISTANCE + 2, Priority.BLOCKING.priority + 2);
    public static int BLOCKING = Math.max(MAX_PRIORITY - 1, Priority.BLOCKING.priority);
    public final List<ChunkPos> blockingOverride = new CopyOnWriteArrayList<>() {
        @Override
        public boolean contains(final Object o) {
            if (o instanceof ChunkPos chunkPos) {
                for (final ChunkPos pos : this) {
                    if (pos.equals(chunkPos)) return true;
                }
            }
            return false;
        }
    };
    private final ServerLevel serverLevel;

    public ChunkPriorityManager(ServerLevel serverLevel) {
        this.serverLevel = serverLevel;
    }

    public int computePriority(int chunkX, int chunkZ) {
        int closestDistance = Integer.MAX_VALUE;

        for (ServerPlayer player : this.serverLevel.players()) {
            ChunkPos playerChunk = player.chunkPosition();
            int x = playerChunk.x;
            int z = playerChunk.z;
            closestDistance = Math.min(computeObjectPriority(x, z, chunkX, chunkZ), closestDistance);
        }

        // the distance is rly short, the shorter it is, the higher the priority
        // if dist == 0 diff == MAX -- 2 under max, since max is blocking
        int diff = MoonriseConstants.MAX_VIEW_DISTANCE - closestDistance;
        if (diff < 1) {
            diff = 1;
        }
        // we ensure that its at minimum of 1 to ensure we don't submit negative priority indexes
        return diff;
    }

    private int computeObjectPriority(int objX, int objZ, int chunkX, int chunkZ) {
        if (chunkX == objX && chunkZ == objZ) {
            return 0;
        }
        int dx = Math.abs(objX - chunkX);
        int dz = Math.abs(objZ - chunkZ);
        return dx + dz;
    }

    public Priority getPriority(int chunkX, int chunkZ) {
        if (blockingOverride.contains(new ChunkPos(chunkX, chunkZ))) return Priority.BLOCKING;
        if (this.serverLevel.players().isEmpty()) return Priority.LOWEST;

        int closestDistance = Integer.MAX_VALUE;

        for (ServerPlayer player : this.serverLevel.players()) {
            ChunkPos playerChunk = player.chunkPosition();
            int x = playerChunk.x;
            int z = playerChunk.z;
            closestDistance = Math.min(computeObjectPriority(x, z, chunkX, chunkZ), closestDistance);
        }

        return calculateBasePriority(closestDistance);
    }

    private Priority calculateBasePriority(int distance) {
        // increment 10%
        double increment = MoonriseConstants.MAX_VIEW_DISTANCE * 0.1;
        if (distance <= 1) return Priority.HIGHEST;
        int i = 0;
        double d = increment;
        while (d <= distance) {
            d += increment;
            i += 1;
        }
        try {
            Priority priority = Priority.values()[Math.min(i, 9)];
            return priority == Priority.COMPLETING ? Priority.LOWEST : priority;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("attempted to build moonrise priority for flowsched dist " + distance + " with increment " + increment + " with final increment " + d, e);
        }
    }
}
