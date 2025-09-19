package io.canvasmc.canvas.command;

import ca.spottedleaf.concurrentutil.util.Priority;
import com.mojang.brigadier.CommandDispatcher;
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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.Ticket;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import org.bukkit.craftbukkit.CraftServer;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

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
 * at all. So when pinning via the {@code profiler} command, we load the chunks in the area
 * via the ticket {@link TicketType#REGION_PROFILING_HOLD canvas:region_profiler_hold}, which
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
 * {@link ProfilerCommand#PROFILING_CHUNKS}, so when a split occurs we can mark the region
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
 * <h2>Profiler Command</h2>
 *
 * <p>
 * The {@code profiler} command prepares the server for isolated profiling by defining
 * an area to profile and pinning its region. This does <i><b>not</b></i> replace or disable
 * the existing {@code spark} command, but is designed to work <i><b>with</b></i> it to
 * support targeted profiling.
 * </p>
 *
 * <p>
 * When executed, the command first checks that the requested profiling area lies within
 * the world border and that the number of chunks does not exceed {@code 512L} (a limit
 * which may change in the future). It then loads the chunks asynchronously if not loaded already using
 * {@link ServerLevel#moonrise$loadChunksAsync(int, int, int, int, Priority, Consumer)}
 * at {@link Priority#HIGHER}. Once loading completes, the callback already ensures all chunks
 * are fully available in a region, so we place profiler tickets, and pin the region for profiling.
 * </p>
 *
 * <p>
 * The command provides two actions: {@code start} and {@code stop}.
 * <ul>
 *   <li>{@code start} – sets up and begins profiling in the defined region.</li>
 *   <li>{@code stop} – turns off the spark profiler, removes the region profiling tickets,
 *       and runs cleanup so the region can be safely unloaded and other profilers used.</li>
 * </ul>
 * </p>
 *
 * <h2>Spark Integration</h2>
 *
 * <p>
 * Utilizing our spark plugin internals, we create an interchangeable system that swaps
 * between REGEX pattern-based matching and thread name matching, since
 * {@link ProfilerCommand#TRACKING_THREAD} can provide us with the {@link io.canvasmc.canvas.tick.ScheduledTaskThreadPool.TickThreadRunner}
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
public class ProfilerCommand {
    public static final SimpleCommandExceptionType ERROR_OUT_OF_WORLD = new SimpleCommandExceptionType(Component.translatable("argument.pos.outofworld"));
    public static final SimpleCommandExceptionType ERROR_ALREADY_PROFILING = new SimpleCommandExceptionType(Component.literal("Server already running a region profiler!"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENABLED = new SimpleCommandExceptionType(Component.literal("Region Specific Profiling(RSP) is unavailable during this runtime due to the absence of the internal Spark plugin. To enable RSP, please enable the builtin Spark plugin."));
    public static final SimpleCommandExceptionType ERROR_NOT_PROFILING = new SimpleCommandExceptionType(Component.literal("Server isn't running a region profile currently!"));
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
        (maxChunks, specifiedChunks) -> Component.translatableEscape("commands.forceload.toobig", maxChunks, specifiedChunks)
    );

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("profiler")
                .requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.command.profiler"))
                .then(literal("start").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.command.profiler.start"))
                    .then(argument("from", ColumnPosArgument.columnPos())
                        .executes(
                            context -> computeProfilePin(
                                context.getSource(),
                                ColumnPosArgument.getColumnPos(context, "from"),
                                ColumnPosArgument.getColumnPos(context, "from")
                            )
                        )
                        .then(
                            Commands.argument("to", ColumnPosArgument.columnPos())
                                .executes(
                                    context -> computeProfilePin(
                                        context.getSource(),
                                        ColumnPosArgument.getColumnPos(context, "from"),
                                        ColumnPosArgument.getColumnPos(context, "to")
                                    )
                                )
                        )))
                .then(literal("stop").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.command.profiler.stop"))
                    .executes(
                        context -> endPinning(context.getSource())
                    )
                )
        );
    }

    private static int endPinning(CommandSourceStack source) throws CommandSyntaxException {
        if (!ENABLED.get()) {
            throw ERROR_NOT_ENABLED.create();
        }
        RegionizedServer.getInstance().addTask(() -> {
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
                source.sendSystemMessage(Component.literal("Attempting to cancel current profiling session"));
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
                source.sendSuccess(() -> Component.literal("Marked the profiling region for unpinning and profiler shutdown"), true);
                final CraftServer bukkitServer = MinecraftServer.getServer().server;
                bukkitServer.dispatchCommand(bukkitServer.getConsoleSender(), "spark profiler stop --comment Region Profile @[" + x + "," + z + "]");
                // remove tickets
                for (long longCoord : curr) {
                    Ticket ticket = new Ticket(TicketType.REGION_PROFILING_HOLD, ChunkMap.FORCED_TICKET_LEVEL);
                    level.chunkSource.ticketStorage.removeTicket(longCoord, ticket);
                }
                // tickets are removed, profiler has shutdown, region is unloading if needed, exit profiler
                PROFILING_RESULTS_CACHE.clear();
                PROFILING_CHUNKS.clear();
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        });
        return 1;
    }

    private static int computeProfilePin(CommandSourceStack source, ColumnPos fromPos, ColumnPos toPos) throws CommandSyntaxException {
        if (!ENABLED.get()) {
            throw ERROR_NOT_ENABLED.create();
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
                final ServerLevel level = source.getLevel();
                int minChunkX = minX >> 4;
                int minChunkZ = minZ >> 4;
                int maxChunkX = maxX >> 4;
                int maxChunkZ = maxZ >> 4;

                // validate world bounds
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

                Runnable callback = () -> { // this is called in the region that we are loading
                    ChunkPos firstChangedChunk = null;
                    int changedCount = 0;

                    PROFILING_LEVEL.set(level); // set the level
                    // place tickets for load
                    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                            final long longCoord = ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
                            Ticket ticket = new Ticket(TicketType.REGION_PROFILING_HOLD, ChunkMap.FORCED_TICKET_LEVEL);
                            boolean flag = level.chunkSource.ticketStorage.addTicket(longCoord, ticket);
                            if (flag) { // (if the ticket was changed, which honestly it shouldn't...)
                                changedCount++;
                                if (firstChangedChunk == null) {
                                    firstChangedChunk = new ChunkPos(chunkX, chunkZ);
                                }
                            }
                            PROFILING_CHUNKS.add(longCoord);
                        }
                    }

                    ResourceKey<Level> dimensionKey = level.dimension();
                    // use get SYNCHRONIZED so that splits and merges won't screw us over
                    final ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region =
                        level.regioniser.getRegionAtSynchronised(minChunkX, minChunkZ);
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
                    // we are safe to just schedule this to the global tick, we are already pinned so we are safe to begin profiling
                    RegionizedServer.getInstance().addTask(() -> {
                        final CraftServer bukkitServer = MinecraftServer.getServer().server;
                        source.sendSystemMessage(Component.literal("Cancelling currently running spark profiler and restarting"));
                        bukkitServer.dispatchCommand(bukkitServer.getConsoleSender(), "spark profiler cancel");
                        bukkitServer.dispatchCommand(bukkitServer.getConsoleSender(), "spark profiler start");
                        // profiler has now started
                    });

                    PROFILING_RESULTS_CACHE.set(
                        new Pair<>(level, new COWLongArrayList(PROFILING_CHUNKS.getArray()))
                    );
                    if (changedCount == 1) {
                        final ChunkPos finalFirstChangedChunk = firstChangedChunk;
                        source.sendSuccess(
                            () -> Component.literal("Pinned region at chunk " + finalFirstChangedChunk + " in " + dimensionKey.location() + " for profiling"),
                            true
                        );
                    } else {
                        ChunkPos fromChunk = new ChunkPos(minChunkX, minChunkZ);
                        ChunkPos toChunk = new ChunkPos(maxChunkX, maxChunkZ);
                        source.sendSuccess(
                            () -> Component.literal("Pinned chunks [from:" + fromChunk + ",to:" + toChunk + "](" + totalChunks + " chunks) in " + dimensionKey.location() + " for region profiling"),
                            true
                        );
                    }
                };

                // don't bother checking if we are running this region, this is on the global tick atm
                if (level.moonrise$areChunksLoaded(minChunkX, minChunkZ, maxChunkX, maxChunkZ)) {
                    RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                        level, minChunkX, minChunkZ, callback, Priority.HIGHEST
                    );
                } else {
                    level.moonrise$loadChunksAsync(minChunkX, minChunkZ, maxChunkX, maxChunkZ, Priority.HIGHER, (chunks) -> callback.run());
                }
            } catch (CommandSyntaxException ex) {
                sendMessage(source, ex);
            }
        });
        return 1;
    }

    private static void sendMessage(@NotNull CommandSourceStack src, @NotNull CommandSyntaxException ex) {
        src.sendFailure((Component) ex.getRawMessage());
    }
}
