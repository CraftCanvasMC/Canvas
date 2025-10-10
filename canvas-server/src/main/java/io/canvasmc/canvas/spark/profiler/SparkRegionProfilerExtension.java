package io.canvasmc.canvas.spark.profiler;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import io.canvasmc.canvas.tick.COWLongArrayList;
import io.canvasmc.canvas.tick.ScheduledTaskThreadPool;
import io.papermc.paper.threadedregions.RegionizedServer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import io.papermc.paper.util.MCUtil;
import net.minecraft.server.level.ServerLevel;

public class SparkRegionProfilerExtension {
    public static final SimpleCommandExceptionType ERROR_ALREADY_PROFILING = new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Server already running a region profiler!"));
    public static final SimpleCommandExceptionType ERROR_NOT_ENABLED = new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Region Specific Profiling(RSP) is unavailable during this runtime due to the absence of the internal Spark plugin. To enable RSP, please enable the builtin Spark plugin."));
    public static final SimpleCommandExceptionType ERROR_NOT_PROFILING = new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Server isn't running a region profile currently!"));
    public static final AtomicReference<ScheduledTaskThreadPool.TickThreadRunner> TRACKING_THREAD = new AtomicReference<>();
    public static final AtomicReference<RegionScheduleHandlePinner> CURRENT_PINNER = new AtomicReference<>();
    public static final AtomicBoolean ENABLED = new AtomicBoolean(true);
    /**
     * This is purely for Spark to fetch region
     * information and tick information during profiling
     * Its more of a temporary storage than anything
     */
    public static final AtomicReference<Pair<ServerLevel, COWLongArrayList>> PROFILING_RESULTS_CACHE = new AtomicReference<>();

    /**
     * Ends pinning of the currently profiling region
     *
     * @param sendMessage   consumer to send messages to, normally used for command feedback
     * @param sendFailure   consumer to send failure messages, normally used for command feedback
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
        if (TRACKING_THREAD.get() == null) {
            // we aren't profiling, don't bother canceling
            sendFailure.accept(ERROR_NOT_PROFILING.create().getRawMessage().getString());
            return;
        }
        // this is on the async handler, spark blocks a TON,
        // so we need this executed BEFORE scheduling.
        // if we unpin later it doesn't really matter
        unpinCallback.run();
        RegionizedServer.getInstance().addTask(() -> {
            try {
                CURRENT_PINNER.getAndSet(null).unpin((scheduleHandle) -> {
                    ScheduledTaskThreadPool.TickThreadRunner thread = TRACKING_THREAD.getAndSet(null); // this is ensured constant, we are fine
                    if (thread == null) throw new IllegalStateException("Tracking thread must not be null");
                    // unpin the task from the thread, clear profiling results cache
                    thread.unpin(scheduleHandle);
                    PROFILING_RESULTS_CACHE.set(null);
                });
            } catch (CommandSyntaxException ex) {
                sendFailure.accept(ex.getRawMessage().getString());
            }
        });
    }

    /**
     * Starts a region profiler process and pinning
     *
     * @param sendMessage consumer to send messages to, normally used for command feedback
     * @param sendFailure consumer to send failure messages, normally used for command feedback
     * @param pinner      the handler to pin the region or global tick
     * @param pinCallback callback for when the region is fully loaded and pinned
     */
    public static void computeProfilePin(
        Consumer<String> sendMessage,
        Consumer<String> sendFailure,
        RegionScheduleHandlePinner pinner,
        Runnable pinCallback
    ) {
        if (!ENABLED.get()) {
            sendFailure.accept(ERROR_NOT_ENABLED.create().getRawMessage().getString());
            return;
        }
        RegionizedServer.getInstance().addTask(() -> {
            try {
                if (TRACKING_THREAD.get() != null) {
                    // we are already profiling, don't do another one
                    throw ERROR_ALREADY_PROFILING.create();
                }
                sendMessage.accept("Beginning region schedule handle pin with '" + pinner.getClass().getSimpleName() + "'");
                pinner.pin((schedulingHandle, thread) -> {
                    // pin the actual region tick to the runner
                    TRACKING_THREAD.set(thread);
                    thread.pin(schedulingHandle);
                    sendMessage.accept("Completed scheduler setup for region pin profiling");
                    CURRENT_PINNER.set(pinner);
                    // schedule async, since spark runs its operations in this pool
                    MCUtil.scheduleAsyncTask(pinCallback);
                });
            } catch (CommandSyntaxException ex) {
                sendFailure.accept(ex.getRawMessage().getString());
            }
        });
    }
}
