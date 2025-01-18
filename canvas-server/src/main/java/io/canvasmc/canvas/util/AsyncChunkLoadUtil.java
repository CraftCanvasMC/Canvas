package io.canvasmc.canvas.util;

import com.ibm.asyncutil.locks.AsyncSemaphore;
import com.ibm.asyncutil.locks.FairAsyncSemaphore;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.concurrent.CompletableFuture;

public class AsyncChunkLoadUtil {

    private static final TicketType<Unit> ASYNC_CHUNK_LOAD = TicketType.create("vmp_async_chunk_load", (unit, unit2) -> 0);

    private static final AsyncSemaphore SEMAPHORE = new FairAsyncSemaphore(6);

    public static CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkLoad(ServerLevel world, ChunkPos pos) {
        return scheduleChunkLoadWithRadius(world, pos, 3);
    }

    public static CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkLoadWithRadius(ServerLevel world, ChunkPos pos, int radius) {
        return scheduleChunkLoadWithLevel(world, pos, 33 - radius);
    }

    public static CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkLoadToStatus(ServerLevel world, ChunkPos pos, ChunkStatus status) {
        return scheduleChunkLoadWithLevel(world, pos, ChunkLevel.byStatus(status));
    }

    public static CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkLoadWithLevel(ServerLevel world, ChunkPos pos, int level) {
        final ServerChunkCache chunkManager = world.getChunkSource();
        final DistanceManager ticketManager = (chunkManager).distanceManager;

        final CompletableFuture<ChunkResult<ChunkAccess>> future = SEMAPHORE.acquire()
            .toCompletableFuture()
            .thenComposeAsync(unused -> {
                ticketManager.addTicket(ASYNC_CHUNK_LOAD, pos, level, Unit.INSTANCE);
                (chunkManager).runDistanceManagerUpdates();
                final ChunkHolder chunkHolder = (chunkManager.chunkMap).getUpdatingChunkIfPresent(pos.toLong());
                if (chunkHolder == null) {
                    throw new IllegalStateException("Chunk not there when requested");
                }
                final FullChunkStatus levelType = ChunkLevel.fullStatus(level);
                return switch (levelType) {
                    case INACCESSIBLE -> chunkHolder.scheduleChunkGenerationTask(ChunkLevel.generationStatus(level), world.getChunkSource().chunkMap);
                    case FULL -> chunkHolder.getFullChunkFuture().thenApply(either -> (ChunkResult<ChunkAccess>) (Object) either);
                    case BLOCK_TICKING -> chunkHolder.getTickingChunkFuture().thenApply(either -> (ChunkResult<ChunkAccess>) (Object) either);
                    case ENTITY_TICKING -> chunkHolder.getEntityTickingChunkFuture().thenApply(either -> (ChunkResult<ChunkAccess>) (Object) either);
                };
            }, world.getServer());
        future.whenCompleteAsync((unused, throwable) -> {
            SEMAPHORE.release();
            if (throwable != null) throwable.printStackTrace();
            ticketManager.removeTicket(ASYNC_CHUNK_LOAD, pos, level, Unit.INSTANCE);
        }, world.getServer());
        return future;
    }

    private static final ThreadLocal<Boolean> isRespawnChunkLoadFinished = ThreadLocal.withInitial(() -> false);

    public static void setIsRespawnChunkLoadFinished(boolean value) {
        isRespawnChunkLoadFinished.set(value);
    }

    public static boolean isRespawnChunkLoadFinished() {
        return isRespawnChunkLoadFinished.get();
    }

}
