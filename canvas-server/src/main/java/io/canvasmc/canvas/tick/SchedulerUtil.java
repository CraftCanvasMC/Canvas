package io.canvasmc.canvas.tick;

import ca.spottedleaf.concurrentutil.numa.OSNuma;
import ca.spottedleaf.concurrentutil.scheduler.EDFSchedulerThreadPool;
import ca.spottedleaf.concurrentutil.scheduler.Scheduler;
import ca.spottedleaf.concurrentutil.scheduler.StealingScheduledThreadPool;
import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.spark.profiler.RegionScheduleHandlePinner;
import io.canvasmc.canvas.spark.profiler.SparkRegionProfilerExtension;
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

public class SchedulerUtil {
    private static SchedulerHandler HANDLER;

    public static SchedulerHandler getHandle() {
        if (HANDLER == null) {
            throw new IllegalStateException("Scheduler has not been initialized yet");
        }
        return HANDLER;
    }

    public static boolean doesSupportRegionProfiler() {
        return doesSupportRegionProfiler(TickRegions.getScheduler().scheduler);
    }

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
                long runBufferNanos = (long) (Config.INSTANCE.scheduler.runTasksBufferMillis * 1_000_000L);
                long stealThresh = Config.INSTANCE.scheduler.stealThresholdMillis * 1_000_000L;
                boolean enableStealing = Config.INSTANCE.scheduler.enableWorkStealing;
                boolean enableAffinity = Config.INSTANCE.scheduler.enableAffinitySchedulerCpuAffinity;
                boolean enableIntermediateTasks = Config.INSTANCE.scheduler.enableMidTickTasks;
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

    public sealed interface SchedulerHandler permits NullHandler, AffinityHandler {
        boolean isRunningRegionProfiler();

        boolean isRunningRegionProfilerOnThread(final long threadId, final String threadName);

        void tryTransferPinningState(TickRegionScheduler.@NonNull RegionScheduleHandle from, TickRegionScheduler.RegionScheduleHandle to);

        void onRegionMerge(TickRegions.TickRegionData from, TickRegions.TickRegionData to, ServerLevel world);

        void onRegionSplit(TickRegions.TickRegionData from, Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> into, ServerLevel world);

        void onRegionDestroy(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region);

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
        public void onRegionMerge(final TickRegions.TickRegionData from, final TickRegions.TickRegionData to, final ServerLevel world) {
        }

        @Override
        public void onRegionSplit(final TickRegions.TickRegionData from, final Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> into, final ServerLevel world) {
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
            return SparkRegionProfilerExtension.isProfiling();
        }

        @Override
        public boolean isRunningRegionProfilerOnThread(final long threadId, final String threadName) {
            if (isRunningRegionProfiler()) {
                AffinitySchedulerThreadPool.TickThreadRunner threadRunner = SparkRegionProfilerExtension.STATE.get().threadRunner();
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
        public void onRegionMerge(final TickRegions.TickRegionData from, final TickRegions.TickRegionData to, final ServerLevel world) {
            if (!isRunningRegionProfiler()) return;
            AffinitySchedulerThreadPool.TickThreadRunner threadRunner = SparkRegionProfilerExtension.STATE.get().threadRunner();
            if (!threadRunner.isLinkedTo(from.tickHandle)) return;
            tryTransferPinningState(from.tickHandle, to.tickHandle);
        }

        // note: we can safely cast to region pinner here, since if we are linked at this point
        //       then it wouldn't be a global tick, it would be a region tick we are profiling
        //       because the global tick can't call split
        @Override
        public void onRegionSplit(final TickRegions.TickRegionData from, final Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> into, final ServerLevel world) {
            if (!isRunningRegionProfiler()) return;
            AffinitySchedulerThreadPool.TickThreadRunner threadRunner = SparkRegionProfilerExtension.STATE.get().threadRunner();
            if (!threadRunner.isLinkedTo(from.tickHandle)) return;
            ChunkPos center = ((RegionScheduleHandlePinner.RegionPinner) SparkRegionProfilerExtension.STATE.get().handlePinner()).getCenter();
            tryTransferPinningState(from.tickHandle, into.get(center.longKey).getData().tickHandle);
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
            AffinitySchedulerThreadPool.TickThreadRunner threadRunner = SparkRegionProfilerExtension.STATE.get().threadRunner();
            if (data.tickHandle.state != null && threadRunner.isLinkedTo(data.tickHandle)) {
                threadRunner.unlink();
            }
        }
    }
}
