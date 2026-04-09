package io.canvasmc.canvas.spark.profiler;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.canvasmc.canvas.tick.AffinitySchedulerThreadPool;
import io.canvasmc.canvas.tick.SchedulerUtil;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import io.papermc.paper.util.MCUtil;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class RegionProfiler {
    private static final SimpleCommandExceptionType ERROR_ALREADY_PROFILING =
        new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Server already running a region profiler!"));
    private static final SimpleCommandExceptionType ERROR_NOT_PROFILING =
        new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Server isn't running a region profile currently!"));
    private static final SimpleCommandExceptionType ERROR_NOT_SUPPORTED =
        new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Region Specific Profiling(RSP) is unavailable during this runtime due to the active scheduler not supporting pinning at this time."));

    public static final AtomicReference<ProfilingState> STATE = new AtomicReference<>();

    public static boolean isProfiling() {
        return STATE.get() != null;
    }

    /**
     * Ends pinning of the currently profiling region
     *
     * @param sendMessage
     *     consumer to send messages to, normally used for command feedback
     * @param sendFailure
     *     consumer to send failure messages, normally used for command feedback
     * @param unpinCallback
     *     callback for when the region has been fully unpinned
     */
    public static void endPinning(
        Consumer<String> sendMessage,
        Consumer<String> sendFailure,
        Runnable unpinCallback
    ) {
        try {
            if (!SchedulerUtil.doesSupportRegionProfiler()) {
                throw ERROR_NOT_SUPPORTED.create();
            }
            if (!isProfiling()) {
                // we aren't profiling, don't bother canceling
                throw ERROR_NOT_PROFILING.create();
            }
            // this is on the async handler, spark blocks a TON,
            // so we need this executed BEFORE scheduling.
            // if we unpin later it doesn't really matter
            unpinCallback.run();
            STATE.getAndSet(null).handlePinner.unpin((scheduleHandle) -> {
                AffinitySchedulerThreadPool.TickThreadRunner threadRunner = ((AffinitySchedulerThreadPool) TickRegions.getScheduler().scheduler).getCurrentTickThreadRunner();
                threadRunner.unlink();
                sendMessage.accept("Completed profiler unpin and cleared state");
            });
        } catch (CommandSyntaxException ex) {
            sendFailure.accept(ex.getRawMessage().getString());
        }
    }

    /**
     * Starts a region profiler process and pinning
     *
     * @param sendMessage
     *     consumer to send messages to, normally used for command feedback
     * @param sendFailure
     *     consumer to send failure messages, normally used for command feedback
     * @param pinner
     *     the handler to pin the region or global tick
     * @param pinCallback
     *     callback for when the region is fully loaded and pinned
     */
    public static void computeProfilePin(
        Consumer<String> sendMessage,
        Consumer<String> sendFailure,
        RegionScheduleHandlePinner pinner,
        Runnable pinCallback
    ) {
        try {
            if (!SchedulerUtil.doesSupportRegionProfiler()) {
                throw ERROR_NOT_SUPPORTED.create();
            }
            if (isProfiling()) {
                // we are already profiling, don't do another one
                throw ERROR_ALREADY_PROFILING.create();
            }
            sendMessage.accept("Beginning region schedule handle pin with '" + pinner.getClass().getSimpleName() + "'");
            pinner.pin((schedulingHandle, thread) -> {
                // pin the actual region tick to the runner
                STATE.set(new ProfilingState(schedulingHandle, pinner, thread));
                thread.link(schedulingHandle, false);
                sendMessage.accept("Completed scheduler setup for region pin profiling");
                // schedule async, since spark runs its operations in this pool
                MCUtil.scheduleAsyncTask(pinCallback);
            });
        } catch (CommandSyntaxException ex) {
            sendFailure.accept(ex.getRawMessage().getString());
        }
    }

    public record ProfilingState(
        TickRegionScheduler.RegionScheduleHandle regionScheduleHandle,
        RegionScheduleHandlePinner handlePinner,
        AffinitySchedulerThreadPool.TickThreadRunner threadRunner
    ) {}
}
