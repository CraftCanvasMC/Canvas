package io.canvasmc.canvas.spark.profiler;

import ca.spottedleaf.concurrentutil.util.Priority;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import io.canvasmc.canvas.tick.COWLongArrayList;
import io.canvasmc.canvas.tick.ScheduledTaskThreadPool;
import io.canvasmc.canvas.util.ExpiringAtomicReference;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;

/**
 * <h1>Region Profiler V2</h1>
 *
 * <p>
 * In the Folia region threading environment, there are multiple issues with utilizing
 * this environment. One of the core issues is that profiling regions independently is
 * nearly impossible unless you have a single region loaded on a server. For production
 * environments, this is oftentimes not possible.
 * </p>
 *
 * <p>
 * To combat this, SpottedLeaf, the creator of V1 of the region profiler, implemented a
 * more generalized profiler to determine points of lag. The issue with this is that it
 * had no detailing whatsoever, causing issues when trying to make genuine performance
 * optimizations for the dedicated server, or for server owners and developers to find
 * sources of lag.
 * </p>
 *
 * <p>
 * Spark is the builtin profiler for Paper, upstream of Folia, and gives a perfect amount
 * of data to help diagnose issues and performance problems. However, the way Spark works
 * prevents us from being able to track regions independently. Spark has the capability of
 * tracking specific <i><b>threads</b></i>, not <i><b>regions</b></i>, as regions are tasked
 * based on a scheduled task thread pool.
 * </p>
 *
 * <p>
 * To combat this, we created a <i><b>task pinning</b></i> system that allows runners to
 * become marked as dedicated processors for a selected area of chunks to be profiled.
 * </p>
 *
 * <h2>Threading Details</h2>
 *
 * <p>
 * As stated in the brief explanation prior, Spark can track specific <i><b>threads</b></i>,
 * not <i><b>regions</b></i>, and so we need a way to ensure that the region is on a specific
 * thread at all times. An API was introduced to allow <i><b>pinning</b></i> the area
 * of chunks to a dedicated tick runner.
 * </p>
 *
 * <p>
 * This does include some performance penalties, as since the area needs isolation, it cannot
 * be utilized in scaling the server, so other tasks cannot use the thread for task processing
 * while a region is pinned to it. For the spark report to be accurate, we need to make the
 * area completely isolated. The new API allows us to mark tasks as pinned to the thread id
 * of a tick runner, which marks the thread as only able to process tasks pinned to it. The
 * API internally allows us to pin any amount of tasks to any amount of threads, however we
 * limit the server’s usage to only a single area at a time, as Spark can only run one
 * profiler at once.
 * </p>
 *
 * <p>
 * When the relationship between the thread runner and the tick task is formed or destroyed,
 * the scheduler will view this as a filter to ensure that the tick task is only ticked on
 * the thread runner, even if it exceeds the steal threshold. This is ensured by applying
 * a few behavioral changes in the forms of filters:
 * <ul>
 *     <li>If a pinned task is on a different thread than the one it's being pinned to, it will
 *         not be picked up by anything until it has exceeded its steal threshold, where the
 *         pinned thread will take the task.</li>
 *     <li>If the tick thread runner gets assigned a pinned task, it will only run the pinned
 *         tasks, and any other tasks on the current thread will eventually exceed their steal
 *         threshold and be picked up by other runners.</li>
 * </ul>
 * </p>
 *
 * <h2>Region Logic</h2>
 *
 * <p>
 * Regions behave unpredictably due to player interaction with the world. Any server could do
 * anything to the region being profiled. It may be loaded already, it may not be loaded
 * at all. So when pinning, we load the chunks in the area via the ticket
 * {@link TicketType#REGION_PROFILING_HOLD canvas:region_profiler_hold}, which
 * acts as a non-persistent ticket that is kept throughout runtime or until told to be removed,
 * like the {@code forced} ticket. Once all the chunks are loaded, we pin the running region to
 * a thread (any available).
 * </p>
 *
 * <h3>Splits</h3>
 * <p>
 * Splits introduce complexity. Given chunks can be loaded and unloaded, the area being
 * profiled is kept loaded. This means we can encounter splits, however they can also
 * happen for a wide number of other reasons. So, we store the chunks being profiled in
 * {@link SparkRegionProfilerExtension#PROFILING_CHUNKS}, so when a split occurs we can mark the region
 * with those chunks as the one needing to be pinned. The original one will be unpinned,
 * and profiling will continue as normal.
 * </p>
 *
 * <h3>Merges</h3>
 * <p>
 * When a region is merged, we unpin the region and then repin the resulting region.
 * </p>
 *
 * <h3>Region Death</h3>
 * <p>
 * Upon death of a region, we immediately fail-fast and kill the server. This should
 * <i><b>NEVER</b></i> happen under any circumstances.
 * </p>
 *
 * <p>
 * The fail-fast model is intentionally aggressive: developers and server owners are
 * guaranteed that profiling results are either valid or the server halts, with no
 * undefined state in between. This allows us to catch bugs much more quickly.
 * </p>
 *
 * <h2>Spark Integration</h2>
 *
 * <p>
 * We modify the class {@link me.lucko.spark.paper.common.command.modules.SamplerModule} in
 * spark to integrate a new argument, {@code --region} to the spark profiler command. This
 * extension takes 2 or 4 arguments, the from and to block positions of the region.
 * This can be stopped with the normal {@code /spark profiler stop}, and started like
 * {@code /spark profiler start --region ~ ~}.
 * </p>
 *
 * <p>
 * The {@code --region} extension prepares the server for isolated profiling by defining
 * an area to profile and pinning its region. This does <i><b>not</b></i> replace or disable
 * the existing {@code spark} command, but is designed to work <i><b>with</b></i> it to
 * support targeted profiling.
 * </p>
 *
 * <p>
 * When executed, the extension first checks that the requested profiling area lies within
 * the world border and that the number of chunks does not exceed {@code 512L} (a limit
 * which may change in the future). It then loads the chunks asynchronously if not loaded already using
 * {@link ServerLevel#moonrise$loadChunksAsync(int, int, int, int, Priority, Consumer)}
 * at {@link Priority#HIGHER}. Once loading completes, the callback already ensures all chunks
 * are fully available in a region, so we place profiler tickets, and pin the region for profiling.
 * </p>
 *
 * <p>
 * Utilizing our spark plugin internals, we create an interchangeable system that swaps
 * between REGEX pattern-based matching and thread name matching, since
 * {@link SparkRegionProfilerExtension#TRACKING_THREAD} can provide us with the {@link io.canvasmc.canvas.tick.ScheduledTaskThreadPool.TickThreadRunner}
 * we are actively profiling. When there are no regions pinned, the system defaults to
 * the standard REGEX pattern, {@code "Region Scheduler Thread #\d+"}.
 * </p>
 *
 * <h2>Concurrency & Safety</h2>
 *
 * <ul>
 *   <li>Use copy-on-write structures to avoid mutation during active reads.</li>
 *   <li>All profiler state is bound to volatile references, ensuring visibility across
 *       threads without explicit synchronization.</li>
 *   <li>Non-persistent tickets are attached at the chunk level, preventing accidental
 *       unloading of a profiled region.</li>
 *   <li>Hard server shutdown on an unknown issue prevents corruption of profiling output.</li>
 * </ul>
 *
 * @author Dueris
 * @version 2.0
 */
public class SparkRegionProfilerExtension {
    public static final SimpleCommandExceptionType ERROR_OUT_OF_WORLD = new SimpleCommandExceptionType(net.minecraft.network.chat.Component.translatable("argument.pos.outofworld"));
    public static final SimpleCommandExceptionType ERROR_ALREADY_PROFILING = new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Server already running a region profiler!"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENABLED = new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Region Specific Profiling(RSP) is unavailable during this runtime due to the absence of the internal Spark plugin. To enable RSP, please enable the builtin Spark plugin."));
    public static final SimpleCommandExceptionType ERROR_NOT_PROFILING = new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Server isn't running a region profile currently!"));
    public static final COWLongArrayList PROFILING_CHUNKS = new COWLongArrayList();
    public static final AtomicReference<ServerLevel> PROFILING_LEVEL = new AtomicReference<>();
    public static final ExpiringAtomicReference<ScheduledTaskThreadPool.TickThreadRunner> TRACKING_THREAD = new ExpiringAtomicReference<>();
    public static final AtomicBoolean ENABLED = new AtomicBoolean(true);
    /**
     * This is purely for Spark to fetch region
     * information and tick information during profiling
     * Its more of a temporary storage than anything
     */
    public static final ExpiringAtomicReference<Pair<ServerLevel, COWLongArrayList>> PROFILING_RESULTS_CACHE = new ExpiringAtomicReference<>();
    private static final Dynamic2CommandExceptionType ERROR_TOO_MANY_CHUNKS = new Dynamic2CommandExceptionType(
        (maxChunks, specifiedChunks) -> net.minecraft.network.chat.Component.translatableEscape("commands.forceload.toobig", maxChunks, specifiedChunks)
    );

    /**
     * Ends pinning of the currently profiling region
     * @param sendMessage consumer to send messages to, normally used for command feedback
     * @param sendFailure consumer to send failure messages, normally used for command feedback
     * @param unpinCallback callback for when the region has been fully unpinned
     */
    public static void endPinning(
        Consumer<String> sendMessage,
        Consumer<String> sendFailure,
        Runnable unpinCallback
    ) {
        if (!ENABLED.get()) {
            sendFailure.accept(ERROR_NOT_ENABLED.create().getRawMessage().getString());
            return;
        }
        try {
            final long[] curr = PROFILING_CHUNKS.getArray();
            if (curr.length == 0) {
                // we aren't profiling, don't bother canceling
                throw ERROR_NOT_PROFILING.create();
            }
            // we now know we have a profiler running, cleanup and kill
            long packedPos = curr[0];
            int x = (int) packedPos;
            int z = (int) (packedPos >> 32);
            final ServerLevel level = PROFILING_LEVEL.getAndSet(null);
            if (level == null)
                throw new IllegalStateException("World was null when attempting to end region profiling");
            // use get SYNCHRONIZED so that splits and merges won't screw us over
            ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region = level.regioniser.getRegionAtSynchronised(x, z);
            if (region == null)
                throw new IllegalStateException("Region must be present at the profiling coordinates");
            ScheduledTaskThreadPool.SchedulableTick backendTask = region.getData().getRegionSchedulingHandle();
            ScheduledTaskThreadPool.TickThreadRunner thread = TRACKING_THREAD.getAndSet(null); // this is ensured constant, we are fine
            if (thread == null) throw new IllegalStateException("Tracking thread must not be null");
            // unpin the task from the thread
            thread.unpin(backendTask);
            // kill spark, we don't care if we are still ticking or not
            sendMessage.accept("Marked the profiling region for unpinning and profiler shutdown");
            unpinCallback.run();
            // remove tickets
            for (long longCoord : curr) {
                Ticket ticket = new Ticket(TicketType.REGION_PROFILING_HOLD, ChunkMap.FORCED_TICKET_LEVEL);
                level.chunkSource.ticketStorage.removeTicket(longCoord, ticket);
            }
            // tickets are removed, profiler has shutdown, region is unloading if needed, exit profiler
            PROFILING_RESULTS_CACHE.clear();
            PROFILING_CHUNKS.clear();
        } catch (CommandSyntaxException ex) {
            sendFailure.accept(ex.getRawMessage().getString());
        }
    }

    /**
     * Starts a region profiler process and pinning
     * @param sendMessage consumer to send messages to, normally used for command feedback
     * @param sendFailure consumer to send failure messages, normally used for command feedback
     * @param fromPos the {@link ColumnPos} of the "from" position, can match the {@code toPos} argument
     * @param toPos the {@link ColumnPos} of the "to" position, can match the {@code fromPos} argument
     * @param world the world we are pinning in
     * @param pinCallback callback for when the region is fully loaded and pinned
     */
    public static void computeProfilePin(
        Consumer<String> sendMessage,
        Consumer<String> sendFailure,
        ColumnPos fromPos,
        ColumnPos toPos,
        ServerLevel world,
        Runnable pinCallback
    ) {
        if (!ENABLED.get()) {
            sendFailure.accept(ERROR_NOT_ENABLED.create().getRawMessage().getString());
            return;
        }
        RegionizedServer.getInstance().addTask(() -> {
            try {
                if (PROFILING_CHUNKS.getArray().length > 0) {
                    // we are already profiling, don't do another one
                    throw ERROR_ALREADY_PROFILING.create();
                }
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

                // validate world bounds
                WorldBorder border = world.getWorldBorder();
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

                Runnable callback = () -> { // this is called in the region that we are loading
                    ChunkPos firstChangedChunk = null;
                    int changedCount = 0;

                    PROFILING_LEVEL.set(world); // set the level
                    // place tickets for load
                    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                            final long longCoord = ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
                            Ticket ticket = new Ticket(TicketType.REGION_PROFILING_HOLD, ChunkMap.FORCED_TICKET_LEVEL);
                            boolean flag = world.chunkSource.ticketStorage.addTicket(longCoord, ticket);
                            if (flag) { // (if the ticket was changed, which honestly it shouldn't...)
                                changedCount++;
                                if (firstChangedChunk == null) {
                                    firstChangedChunk = new ChunkPos(chunkX, chunkZ);
                                }
                            }
                            PROFILING_CHUNKS.add(longCoord);
                        }
                    }

                    ResourceKey<Level> dimensionKey = world.dimension();
                    // use get SYNCHRONIZED so that splits and merges won't screw us over
                    final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                        world.regioniser.getRegionAtSynchronised(minChunkX, minChunkZ);
                    if (region == null) {
                        throw new IllegalStateException("Region must be present at the profiling coordinates");
                    }
                    final TickRegionScheduler.RegionScheduleHandle schedulingHandle = region.getData().getRegionSchedulingHandle();
                    final ScheduledTaskThreadPool.TickThreadRunner thread = schedulingHandle.getTickThreadRunner();

                    if (thread == null) {
                        throw new IllegalStateException("Region scheduling handle returned null task or runner");
                    }

                    // pin the actual region tick to the runner
                    TRACKING_THREAD.set(thread);
                    thread.pin(schedulingHandle);

                    PROFILING_RESULTS_CACHE.set(
                        new Pair<>(world, new COWLongArrayList(PROFILING_CHUNKS.getArray()))
                    );
                    if (changedCount == 1) {
                        sendMessage.accept("Pinned region at chunk " + firstChangedChunk + " in " + dimensionKey.location() + " for profiling");
                    } else {
                        ChunkPos fromChunk = new ChunkPos(minChunkX, minChunkZ);
                        ChunkPos toChunk = new ChunkPos(maxChunkX, maxChunkZ);
                        sendMessage.accept("Pinned chunks [from:" + fromChunk + ",to:" + toChunk + "](" + totalChunks + " chunks) in " + dimensionKey.location() + " for region profiling");
                    }
                    pinCallback.run();
                };

                // don't bother checking if we are running this region, this is on the global tick atm
                if (world.moonrise$areChunksLoaded(minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
                    RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                        world, minChunkX, minChunkZ, callback, Priority.HIGHEST
                    );
                } else {
                    world.moonrise$loadChunksAsync(minChunkX, minChunkZ, maxChunkX, maxChunkZ, Priority.HIGHER, (chunks) -> callback.run());
                }
            } catch (CommandSyntaxException ex) {
                sendFailure.accept(ex.getRawMessage().getString());
            }
        });
    }
}
