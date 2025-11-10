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
 * {@link net.minecraft.server.level.TicketType#REGION_PROFILING_HOLD canvas:region_profiler_hold}, which
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
 * {@link io.canvasmc.canvas.spark.profiler.RegionScheduleHandlePinner.RegionPinner#PROFILING_CHUNKS}, so when a split occurs we can mark the region
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
 * {@link net.minecraft.server.level.ServerLevel#moonrise$loadChunksAsync(int, int, int, int, Priority, Consumer)}
 * at {@link ca.spottedleaf.concurrentutil.util.Priority#HIGHER}. Once loading completes, the callback already ensures all chunks
 * are fully available in a region, so we place profiler tickets, and pin the region for profiling.
 * </p>
 *
 * <p>
 * Utilizing our spark plugin internals, we create an interchangeable system that swaps
 * between REGEX pattern-based matching and thread name matching, since
 * {@link io.canvasmc.canvas.spark.profiler.SparkRegionProfilerExtension#TRACKING_THREAD} can provide us with the {@link io.canvasmc.canvas.tick.ScheduledTaskThreadPool.TickThreadRunner}
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
package io.canvasmc.canvas.spark.profiler;

import ca.spottedleaf.concurrentutil.util.Priority;
import java.util.function.Consumer;
