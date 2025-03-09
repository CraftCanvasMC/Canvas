package io.canvasmc.canvas.entity.tracking;

import ca.spottedleaf.moonrise.common.list.ReferenceList;
import ca.spottedleaf.moonrise.common.misc.NearbyPlayers;
import ca.spottedleaf.moonrise.common.util.TickThread;
import ca.spottedleaf.moonrise.patches.chunk_system.entity.ChunkSystemEntity;
import ca.spottedleaf.moonrise.patches.chunk_system.level.ChunkSystemServerLevel;
import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.server.ServerEntityLookup;
import ca.spottedleaf.moonrise.patches.entity_tracker.EntityTrackerEntity;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.util.NamedAgnosticThreadFactory;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

public class ThreadedTracker {
    private static final ThreadPoolExecutor processor = new ThreadPoolExecutor(
        1,
        Config.INSTANCE.entityTracking.maxProcessors,
        Config.INSTANCE.entityTracking.keepAlive, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        new NamedAgnosticThreadFactory<>("entity_tracker", TrackerThread::new, Thread.NORM_PRIORITY - 2)
    );
    public static ThreadedTracker INSTANCE = new ThreadedTracker(Config.INSTANCE.entityTracking.enableThreadedTracking);
    private final boolean enableThreading;

    ThreadedTracker(boolean enableThreading) {
        this.enableThreading = enableThreading;
    }

    public static ThreadPoolExecutor getProcessor() {
        return processor;
    }

    public void tick(@NotNull ChunkSystemServerLevel chunkSystemServerLevel) {
        if (this.enableThreading) {
            final NearbyPlayers nearbyPlayers = chunkSystemServerLevel.moonrise$getNearbyPlayers();
            final Entity[] trackerEntitiesRaw = ((ServerEntityLookup) chunkSystemServerLevel.moonrise$getEntityLookup()).trackerEntities.getRawDataUnchecked();

            processor.execute(() -> {
                for (final Entity entity : trackerEntitiesRaw) {
                    if (entity == null) continue;

                    final ChunkMap.TrackedEntity trackedInstance = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();
                    if (trackedInstance == null) continue;

                    trackedInstance.moonrise$tick(nearbyPlayers.getChunk(entity.chunkPosition()));
                    trackedInstance.serverEntity.sendChanges();
                }
            });
        } else {
            final ServerEntityLookup entityLookup = (ServerEntityLookup) chunkSystemServerLevel.moonrise$getEntityLookup();

            final ReferenceList<Entity> trackerEntities = entityLookup.trackerEntities;
            final Entity[] trackerEntitiesRaw = trackerEntities.getRawDataUnchecked();
            for (int i = 0, len = trackerEntities.size(); i < len; ++i) {
                final Entity entity = trackerEntitiesRaw[i];
                if (entity == null) continue;
                final ChunkMap.TrackedEntity tracker = ((EntityTrackerEntity) entity).moonrise$getTrackedEntity();
                if (tracker == null) {
                    continue;
                }
                tracker.moonrise$tick(((ChunkSystemEntity) entity).moonrise$getChunkData() == null ? null : ((ChunkSystemEntity) entity).moonrise$getChunkData().nearbyPlayers);
                @Nullable FullChunkStatus chunkStatus = ((ChunkSystemEntity) entity).moonrise$getChunkStatus();
                if (tracker.moonrise$hasPlayers()
                    || (chunkStatus != null && chunkStatus.isOrAfter(FullChunkStatus.ENTITY_TICKING))) {
                    tracker.serverEntity.sendChanges();
                }
            }
        }
    }

    public static class TrackerThread extends TickThread {
        public TrackerThread(final ThreadGroup group, final Runnable runnable, final String name) {
            super(group, runnable, name);
        }
    }
}
