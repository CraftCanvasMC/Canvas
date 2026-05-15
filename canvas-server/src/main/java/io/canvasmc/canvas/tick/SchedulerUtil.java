package io.canvasmc.canvas.tick;

import ca.spottedleaf.concurrentutil.numa.OSNuma;
import ca.spottedleaf.concurrentutil.scheduler.EDFSchedulerThreadPool;
import ca.spottedleaf.concurrentutil.scheduler.Scheduler;
import ca.spottedleaf.concurrentutil.scheduler.StealingScheduledThreadPool;
import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.spark.profiler.RegionProfiler;
import io.canvasmc.canvas.spark.profiler.RegionScheduleHandlePinner;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import java.nio.file.Path;
import java.util.concurrent.ThreadFactory;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.NonNull;

/**
 * This defines utilities primarily surrounding the instantiation and configuration of the scheduler types, along with
 * testing if the region specific profiler is supported. This also contains the callback logic for region profiler
 * hooks
 *
 * @author dueris
 */
public class SchedulerUtil {
    private static SchedulerHandler HANDLER;

    /**
     * Gets the scheduler handle for region profiler hooks
     *
     * @return the handle
     */
    public static SchedulerHandler getHandle() {
        if (HANDLER == null) {
            throw new IllegalStateException("Scheduler has not been initialized yet");
        }
        return HANDLER;
    }

    /**
     * Gets if the server currently supports region profiling or not
     *
     * @return if the region profiler is supported
     */
    public static boolean doesSupportRegionProfiler() {
        return doesSupportRegionProfiler(TickRegions.getScheduler().scheduler);
    }

    /**
     * Gets if the server currently supports region profiling or not with the provided scheduler
     *
     * @return if the region profiler is supported with the scheduler
     */
    public static boolean doesSupportRegionProfiler(final Scheduler scheduler) {
        if (Boolean.getBoolean("Canvas.DisableRegionProfiler")) {
            return false;
        }
        if (scheduler instanceof AffinitySchedulerThreadPool) {
            // if has less than 2 threads, pinning would kill the server
            return scheduler.getCoreThreads().length >= 2;
        }
        return false;
    }

    /**
     * Starts the scheduler for the server
     */
    public static void startScheduler() {
        final Scheduler scheduler = TickRegions.getScheduler().scheduler;
        if (scheduler instanceof EDFSchedulerThreadPool edfSchedulerThreadPool) {
            TickRegionScheduler.LOGGER.info("Starting EDF region scheduler");
            edfSchedulerThreadPool.start();
        }
        if (scheduler instanceof AffinitySchedulerThreadPool affinitySchedulerThreadPool) {
            TickRegionScheduler.LOGGER.info("Starting AFFINITY region scheduler");
            affinitySchedulerThreadPool.start();
        }
        if (doesSupportRegionProfiler()) {
            TickRegionScheduler.LOGGER.info("Region profiling marked as supported in this environment");
        }
        else TickRegionScheduler.LOGGER.warn("Region profiling not supported in this environment");
    }

    /**
     * Decides the scheduler for instantiation of the scheduler handle
     * <p>
     * It is worth noting that once this method completes, the handle is set
     *
     * @param schedulerType
     *     the configured scheduler type
     * @param initialThreads
     *     the amount of threads allocated
     * @param threadFactory
     *     the factory to construct threads for the scheduler
     *
     * @return the constructed scheduler
     */
    public static @NonNull Scheduler decideScheduler(final TickRegionScheduler.@NonNull SchedulerType schedulerType, final int initialThreads, final ThreadFactory threadFactory) {
        switch (schedulerType) {
            case EDF: {
                HANDLER = new NullHandler();
                return new EDFSchedulerThreadPool(initialThreads, threadFactory);
            }
            case WORK_STEALING: {
                StealingScheduledThreadPool scheduler = new StealingScheduledThreadPool(
                    threadFactory, MoonriseConstants.NUMA_ENABLE ? OSNuma.getNativeInstance() : OSNuma.NoOp.INSTANCE
                );
                scheduler.setFlags(StealingScheduledThreadPool.FLAG_SCHEDULE_EVENLY);
                HANDLER = new NullHandler();
                return scheduler;
            }
            case AFFINITY: {
                final GlobalConfiguration.RegionScheduler.AffinityScheduler affinityConfig = GlobalConfiguration.getInstance().regionScheduler.affinityScheduler;

                long runBufferNanos = (long) (affinityConfig.runTasksBufferMillis * 1_000_000L);
                long stealThresh = affinityConfig.stealThresholdMillis * 1_000_000L;
                boolean enableStealing = affinityConfig.enableWorkStealing;
                boolean enableAffinity = affinityConfig.enableAffinitySchedulerCpuAffinity;
                boolean enableIntermediateTasks = affinityConfig.enableMidTickTasks;

                HANDLER = new AffinityHandler();

                return new AffinitySchedulerThreadPool(
                    initialThreads, threadFactory, runBufferNanos, stealThresh, SchedulerUtil::doesSupportRegionProfiler, enableStealing, enableAffinity, enableIntermediateTasks, (thrown) -> {
                    TickRegionScheduler.LOGGER.error("Uncaught exception in scheduler internals", thrown);

                    CrashReport crashReport = CrashReport.forThrowable(thrown, "Scheduler Internal Exception");
                    MinecraftServer.getServer().fillSystemReport(crashReport.getSystemReport());
                    Path path = MinecraftServer.getServer().getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");
                    if (crashReport.saveToFile(path, ReportType.CRASH)) {
                        TickRegionScheduler.LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
                    }
                    else {
                        TickRegionScheduler.LOGGER.error("We were unable to save this crash report to disk.");
                    }
                    // prevent further ticks from occurring
                    // we CANNOT sync, because WE ARE ON A SCHEDULER THREAD
                    TickRegions.getScheduler().scheduler.halt();

                    MinecraftServer.getServer().stopServer();
                }
                );
            }
            default: {
                throw new IllegalStateException("Unknown scheduler type: " + schedulerType);
            }
        }
    }

    /**
     * The scheduler handle for the server. This is for region profiler hooks to try and help with injection points
     * <p>
     * The only valid implementations of this are {@link io.canvasmc.canvas.tick.SchedulerUtil.NullHandler} and
     * {@link io.canvasmc.canvas.tick.SchedulerUtil.AffinityHandler}. The null handler is for schedulers that do not
     * support region profiling, the affinity handler is specifically for the
     * {@link io.canvasmc.canvas.tick.AffinitySchedulerThreadPool affinity scheduler}, which does support region
     * profiling
     */
    public sealed interface SchedulerHandler permits NullHandler, AffinityHandler {
        /**
         * Gets if the server is actively running a region profiler. If the current scheduler doesn't support region
         * profiling, this is always {@code false}
         *
         * @return {@code false} if not profiling currently, {@code true} otherwise
         */
        boolean isRunningRegionProfiler();

        /**
         * Gets if the server is running a region profiler on the thread specifications provided.
         *
         * @param threadId
         *     the thread id
         * @param threadName
         *     the name of the thread
         *
         * @return if the server is profiling on that thread
         */
        boolean isRunningRegionProfilerOnThread(final long threadId, final String threadName);

        /**
         * Transfers the pinning state from one region to another, if a pinning state is present in the {@code from}
         * region
         *
         * @param from
         *     the region we are taking the state from
         * @param to
         *     the region we are moving the state to
         */
        void tryTransferPinningState(TickRegionScheduler.@NonNull RegionScheduleHandle from, TickRegionScheduler.RegionScheduleHandle to);

        /**
         * Callback for two regions merging together
         *
         * @param from
         *     the {@code from} region
         * @param to
         *     the region we are merging into
         * @param level
         *     the level associated with the two regions
         */
        void onRegionMerge(TickRegions.TickRegionData from, TickRegions.TickRegionData to, ServerLevel level);

        /**
         * Callback for when a region splits into multiple regions
         *
         * @param from
         *     the original region
         * @param into
         *     the region(s) being split into
         * @param level
         *     the level
         */
        void onRegionSplit(TickRegions.TickRegionData from, Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> into, ServerLevel level);

        /**
         * Callback for when a region is destroyed
         *
         * @param region
         *     the region being destroyed
         */
        void onRegionDestroy(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region);

        /**
         * Callback for when a region is marked inactive
         *
         * @param region
         *     the region now being marked as inactive
         */
        void onRegionInactive(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region);
    }

    public static final class NullHandler implements SchedulerHandler {
        @Override
        public boolean isRunningRegionProfiler() {
            return false;
        }

        @Override
        public boolean isRunningRegionProfilerOnThread(final long threadId, final String threadName) {
            return false;
        }

        @Override
        public void tryTransferPinningState(final TickRegionScheduler.@NonNull RegionScheduleHandle from, final TickRegionScheduler.RegionScheduleHandle to) {
        }

        @Override
        public void onRegionMerge(final TickRegions.TickRegionData from, final TickRegions.TickRegionData to, final ServerLevel level) {
        }

        @Override
        public void onRegionSplit(final TickRegions.TickRegionData from, final Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> into, final ServerLevel level) {
        }

        @Override
        public void onRegionDestroy(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region) {
        }

        @Override
        public void onRegionInactive(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region) {
        }
    }

    public static final class AffinityHandler implements SchedulerHandler {
        @Override
        public boolean isRunningRegionProfiler() {
            return RegionProfiler.isProfiling();
        }

        @Override
        public boolean isRunningRegionProfilerOnThread(final long threadId, final String threadName) {
            if (isRunningRegionProfiler()) {
                AffinitySchedulerThreadPool.TickThreadRunner threadRunner = RegionProfiler.STATE.get().threadRunner();
                return threadRunner.getRunnerThread().getName().equalsIgnoreCase(threadName);
            }
            return false;
        }

        @Override
        public void tryTransferPinningState(final TickRegionScheduler.@NonNull RegionScheduleHandle from, final TickRegionScheduler.RegionScheduleHandle to) {
            if (!isRunningRegionProfiler() || !(TickRegions.getScheduler().scheduler instanceof AffinitySchedulerThreadPool affinitySchedulerThreadPool)) {
                return;
            }
            AffinitySchedulerThreadPool.TickThreadRunner threadRunner = affinitySchedulerThreadPool.getCurrentTickThreadRunner();
            // if not linked to the previous, don't try and change
            if (!threadRunner.isLinkedTo(from)) return;
            threadRunner.unlink();
            threadRunner.link(to, true);
        }

        @Override
        public void onRegionMerge(final TickRegions.TickRegionData from, final TickRegions.TickRegionData to, final ServerLevel level) {
            if (!isRunningRegionProfiler()) return;
            AffinitySchedulerThreadPool.TickThreadRunner threadRunner = RegionProfiler.STATE.get().threadRunner();
            if (!threadRunner.isLinkedTo(from.tickHandle)) return;
            tryTransferPinningState(from.tickHandle, to.tickHandle);
        }

        // note: we can safely cast to region pinner here, since if we are linked at this point
        //       then it wouldn't be a global tick, it would be a region tick we are profiling
        //       because the global tick can't call split
        @Override
        public void onRegionSplit(final TickRegions.TickRegionData from, final Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> into, final ServerLevel level) {
            if (!isRunningRegionProfiler()) return;
            AffinitySchedulerThreadPool.TickThreadRunner threadRunner = RegionProfiler.STATE.get().threadRunner();
            if (!threadRunner.isLinkedTo(from.tickHandle)) return;
            ChunkPos center = ((RegionScheduleHandlePinner.RegionPinner) RegionProfiler.STATE.get().handlePinner()).getCenter();
            tryTransferPinningState(from.tickHandle, into.get(center.longKey()).getData().tickHandle);
        }

        // both destroy and inactive only happen on split and merge
        @Override
        public void onRegionDestroy(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region) {
            tryDestroyLink(region);
        }

        @Override
        public void onRegionInactive(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region) {
            tryDestroyLink(region);
        }

        private void tryDestroyLink(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region) {
            final TickRegions.TickRegionData data = region.getData();
            if (!isRunningRegionProfiler()) return;
            AffinitySchedulerThreadPool.TickThreadRunner threadRunner = RegionProfiler.STATE.get().threadRunner();
            if (data.tickHandle.state != null && threadRunner.isLinkedTo(data.tickHandle)) {
                threadRunner.unlink();
            }
        }
    }
}
