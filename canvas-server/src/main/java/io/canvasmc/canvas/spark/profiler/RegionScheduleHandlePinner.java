package io.canvasmc.canvas.spark.profiler;

import ca.spottedleaf.concurrentutil.scheduler.SchedulableTick;
import ca.spottedleaf.concurrentutil.util.Priority;
import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import ca.spottedleaf.moonrise.common.util.TickThread;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.canvasmc.canvas.tick.AffinitySchedulerThreadPool;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongComparator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.border.WorldBorder;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface RegionScheduleHandlePinner {
    // helper exception types
    SimpleCommandExceptionType ERROR_OUT_OF_WORLD = new SimpleCommandExceptionType(net.minecraft.network.chat.Component.translatable("argument.pos.outofworld"));
    Dynamic2CommandExceptionType ERROR_TOO_MANY_CHUNKS = new Dynamic2CommandExceptionType(
        (maxChunks, specifiedChunks) -> net.minecraft.network.chat.Component.translatableEscape("commands.forceload.toobig", maxChunks, specifiedChunks)
    );

    /**
     * Setup pinning for the {@link RegionScheduleHandlePinner}. This should always do the following:
     * <br>
     * - Call the finalizer {@link BiConsumer} when the scheduled handle is created and running if not already
     * <br>
     * - Ensure the schedule handle will *never* disappear until profiling is over
     * <br></br>
     *
     * @param finalizer
     *     the consumer that finishes the Spark profiler setup
     *
     * @throws CommandSyntaxException
     *     if an error occurs in setup and should be sent as an error to the sender
     */
    void pin(BiConsumer<TickRegionScheduler.RegionScheduleHandle, AffinitySchedulerThreadPool.TickThreadRunner> finalizer) throws CommandSyntaxException;

    /**
     * Wraps up pinning for the current {@link RegionScheduleHandlePinner}. This should do the following:
     * <br>
     * - Call the finalizer {@link Consumer} when you <i>retrieve</i> the
     * {@link ca.spottedleaf.concurrentutil.scheduler.SchedulableTick}, and then <i>after</i> cleanup the pin.
     * <br>
     * - Call the finalizer <i>on the profiling schedule handle</i>
     * <br></br>
     *
     * @param finalizer
     *     the consumer that finishes the Spark profiler completion
     *
     * @throws CommandSyntaxException
     *     if an error occurs in completion and should be sent as an error to the sender
     */
    void unpin(Consumer<SchedulableTick> finalizer) throws CommandSyntaxException;

    /**
     * Fetches the logger for this region pinner, used for debugging purposes
     *
     * @return the logger
     */
    Logger getLogger();

    /**
     * Pinning implementation for a specific region area, allowing for a 'from' and a 'to' position to select more than
     * one chunk to be profiled, with a maximum of 512 chunks (Note: the limit may be removed in the future)
     *
     * @param fromPos
     *     the 'from' coordinates
     * @param toPos
     *     the 'to' coordinates
     * @param level
     *     the level we are operating on
     */
    record RegionPinner(ColumnPos fromPos, ColumnPos toPos, ServerLevel level) implements RegionScheduleHandlePinner {
        public @NonNull ChunkPos getCenter() {
            final LongArrayList chunks = LongArrayList.wrap(gatherChunksToProfile());

            final LongComparator comparator = (final long k1, final long k2) -> {
                final int x1 = CoordinateUtils.getChunkX(k1);
                final int x2 = CoordinateUtils.getChunkX(k2);

                final int z1 = CoordinateUtils.getChunkZ(k1);
                final int z2 = CoordinateUtils.getChunkZ(k2);

                final int zCompare = Integer.compare(z1, z2);
                if (zCompare != 0) {
                    return zCompare;
                }

                return Integer.compare(x1, x2);
            };
            chunks.sort(comparator);

            final long middle = chunks.getLong(chunks.size() >> 1);

            return new ChunkPos(CoordinateUtils.getChunkX(middle), CoordinateUtils.getChunkZ(middle));
        }

        // note: this is unchecked, no validation of the border or chunk count
        public long[] gatherChunksToProfile() {
            LongArrayList ret = new LongArrayList();
            // note: this is the BLOCK pos, not to be confused with the CHUNK pos
            int minX = Math.min(fromPos.x(), toPos.x());
            int minZ = Math.min(fromPos.z(), toPos.z());
            int maxX = Math.max(fromPos.x(), toPos.x());
            int maxZ = Math.max(fromPos.z(), toPos.z());

            // convert to section coordinates
            int minChunkX = minX >> 4;
            int minChunkZ = minZ >> 4;
            int maxChunkX = maxX >> 4;
            int maxChunkZ = maxZ >> 4;

            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    ret.add(CoordinateUtils.getChunkKey(chunkX, chunkZ));
                }
            }
            return ret.toLongArray();
        }

        @Override
        public void pin(final BiConsumer<TickRegionScheduler.RegionScheduleHandle, AffinitySchedulerThreadPool.TickThreadRunner> finalizer) throws CommandSyntaxException {
            // note: this is the BLOCK pos, not to be confused with the CHUNK pos
            int minX = Math.min(fromPos.x(), toPos.x());
            int minZ = Math.min(fromPos.z(), toPos.z());
            int maxX = Math.max(fromPos.x(), toPos.x());
            int maxZ = Math.max(fromPos.z(), toPos.z());

            // convert to section coordinates
            int minChunkX = minX >> 4;
            int minChunkZ = minZ >> 4;
            int maxChunkX = maxX >> 4;
            int maxChunkZ = maxZ >> 4;

            // validate level bounds
            WorldBorder border = level.getWorldBorder();
            double borderMinX = border.getMinX();
            double borderMinZ = border.getMinZ();
            double borderMaxX = border.getMaxX();
            double borderMaxZ = border.getMaxZ();

            if (minX < borderMinX || minZ < borderMinZ || maxX > borderMaxX || maxZ > borderMaxZ) {
                throw ERROR_OUT_OF_WORLD.create();
            }

            // validate chunk count
            long totalChunks = (long) (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
            if (totalChunks > 512L) { // keep a hard limit on this
                throw ERROR_TOO_MANY_CHUNKS.create(512L, totalChunks);
            }

            // place tickets for load
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    final long longCoord = ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
                    Ticket ticket = new Ticket(TicketType.REGION_PROFILING_HOLD, ChunkMap.FORCED_TICKET_LEVEL);
                    level.chunkSource.ticketStorage.addTicket(longCoord, ticket);
                }
            }

            // process ticket updates
            level.moonrise$getChunkTaskScheduler().chunkHolderManager.processTicketUpdates();

            // load chunks and schedule finalizing
            level.canvas$loadOrRunAtChunksAsync(
                // minX, maxX, minZ, maxZ
                minChunkX, maxChunkX, minChunkZ, maxChunkZ, Priority.HIGHEST, () -> {
                    final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                        level.regioniser.getRegionAtSynchronised(minChunkX, minChunkZ);
                    if (region == null) {
                        throw new IllegalStateException("Region must be present at the profiling coordinates");
                    }
                    if (!TickThread.isTickThreadFor(level, minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
                        throw new IllegalStateException("Not ticking on scheduled region");
                    }
                    if (region != TickRegionScheduler.getCurrentRegion()) {
                        throw new IllegalStateException("Not current region? Running region around " + region.getCenterChunk() + ", but scheduled to region owning " + getCenter());
                    }
                    // note: calling curr tick runner here is safe since the callback is run at the specified coords
                    final TickRegionScheduler.RegionScheduleHandle schedulingHandle = region.getData().getRegionSchedulingHandle();
                    final AffinitySchedulerThreadPool.TickThreadRunner thread = ((AffinitySchedulerThreadPool) TickRegions.getScheduler().scheduler).getCurrentTickThreadRunner();

                    finalizer.accept(schedulingHandle, thread);
                }
            );
        }

        @Override
        public void unpin(Consumer<SchedulableTick> finalizer) {
            final long[] curr = gatherChunksToProfile();

            final ChunkPos center = getCenter();
            final int chunkX = center.x();
            final int chunkZ = center.z();

            // Note: this should be loaded already, however we run this safe version just to be sure
            level.canvas$loadOrRunAtChunksAsync(
                // offset by 8 to get the middle block of the chunk
                new BlockPos((chunkX << 4) + 8, 0, (chunkZ << 4) + 8),
                16,
                Priority.HIGHEST,
                () -> {
                    ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = TickRegionScheduler.getCurrentRegion();
                    if (region == null)
                        throw new IllegalStateException("Region must be present at the profiling coordinates");
                    TickRegionScheduler.RegionScheduleHandle backendTask = region.getData().getRegionSchedulingHandle();
                    finalizer.accept(backendTask);
                    // cleanup pinning, we need to do the following:
                    // - remove tickets (Note: we don't *need* to process ticket updates here, they will be done later)
                    // - clear PROFILING_CHUNKS
                    for (long longCoord : curr) {
                        Ticket ticket = new Ticket(TicketType.REGION_PROFILING_HOLD, ChunkMap.FORCED_TICKET_LEVEL);
                        level.chunkSource.ticketStorage.removeTicket(longCoord, ticket);
                    }
                }
            );
        }

        @Override
        public Logger getLogger() {
            return LoggerFactory.getLogger("SparkRegionPinner");
        }
    }

    /**
     * Pinning implementation for tracking the global tick, instead of a specified area of chunks
     * <br></br>
     * Note: unfortunately this *does* block on the global tick. There isn't any way of avoiding this unless we find a
     * way to make it still accurate while also having this complete async
     */
    class GlobalTickPinner implements RegionScheduleHandlePinner {
        @Override
        public void pin(BiConsumer<TickRegionScheduler.RegionScheduleHandle, AffinitySchedulerThreadPool.TickThreadRunner> finalizer) throws CommandSyntaxException {
            Runnable pinTask = () -> {
                // note: calling curr tick runner here is safe since the runnable is executed on the global tick
                TickRegionScheduler.RegionScheduleHandle schedulingHandle = RegionizedServer.getGlobalTickData();
                final AffinitySchedulerThreadPool.TickThreadRunner thread = ((AffinitySchedulerThreadPool) TickRegions.getScheduler().scheduler).getCurrentTickThreadRunner();

                if (thread == null) {
                    throw new IllegalStateException("Region scheduling handle returned null task or runner");
                }

                finalizer.accept(schedulingHandle, thread);
            };
            RegionizedServer.getInstance().scheduleToOrExecute(pinTask);
        }

        @Override
        public void unpin(@NonNull Consumer<SchedulableTick> finalizer) {
            TickRegionScheduler.RegionScheduleHandle schedulingHandle = RegionizedServer.getGlobalTickData();
            RegionizedServer.getInstance().scheduleToOrExecute(() -> finalizer.accept(schedulingHandle));
        }

        @Override
        public Logger getLogger() {
            return LoggerFactory.getLogger("SparkGlobalPinner");
        }
    }
}
