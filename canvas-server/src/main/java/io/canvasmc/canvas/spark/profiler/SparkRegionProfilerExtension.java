package io.canvasmc.canvas.spark.profiler;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Pair;
import io.canvasmc.canvas.util.COWLongArrayList;
import io.canvasmc.canvas.tick.CRSThreadPool;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.TickRegions;
import io.papermc.paper.util.MCUtil;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;

public class SparkRegionProfilerExtension {
    public static final SimpleCommandExceptionType ERROR_ALREADY_PROFILING =
        new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Server already running a region profiler!"));
    public static final SimpleCommandExceptionType ERROR_NOT_CRS =
        new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Region Specific Profiling(RSP) is unavailable during this runtime due to the requirement of using the CRS scheduler. Please change your scheduler configuration in paper-global to \"CRS\" to enable RSP"));
    public static final SimpleCommandExceptionType ERROR_NOT_SUPPORTED =
        new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Region Specific Profiling(RSP) is unavailable during this runtime due to the CRS scheduler not supporting pinning at this time. Too little threads allocated?"));
    public static final SimpleCommandExceptionType ERROR_NOT_PROFILING =
        new SimpleCommandExceptionType(net.minecraft.network.chat.Component.literal("Server isn't running a region profile currently!"));
    public static final AtomicReference<CRSThreadPool.TickThreadRunner> TRACKING_THREAD = new AtomicReference<>();
    public static final AtomicReference<RegionScheduleHandlePinner> CURRENT_PINNER = new AtomicReference<>();
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
        if (!(TickRegions.getScheduler().scheduler instanceof CRSThreadPool crsScheduler)) {
            sendFailure.accept(ERROR_NOT_CRS.create().getRawMessage().getString());
            return;
        }
        if (!crsScheduler.doesSupportPinning()) {
            sendFailure.accept(ERROR_NOT_SUPPORTED.create().getRawMessage().getString());
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
                    TRACKING_THREAD.set(null); // clear tracking thread
                    ((CRSThreadPool.ScheduledState) scheduleHandle.state).unpin(crsScheduler);
                    PROFILING_RESULTS_CACHE.set(null);
                }, crsScheduler);
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
        if (!(TickRegions.getScheduler().scheduler instanceof CRSThreadPool crsScheduler)) {
            sendFailure.accept(ERROR_NOT_CRS.create().getRawMessage().getString());
            return;
        }
        if (!crsScheduler.doesSupportPinning()) {
            sendFailure.accept(ERROR_NOT_SUPPORTED.create().getRawMessage().getString());
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
                    ((CRSThreadPool.ScheduledState) schedulingHandle.state).pin(thread.id, crsScheduler);
                    sendMessage.accept("Completed scheduler setup for region pin profiling");
                    CURRENT_PINNER.set(pinner);
                    // schedule async, since spark runs its operations in this pool
                    MCUtil.scheduleAsyncTask(pinCallback);
                }, crsScheduler);
            } catch (CommandSyntaxException ex) {
                sendFailure.accept(ex.getRawMessage().getString());
            }
        });
    }
}
