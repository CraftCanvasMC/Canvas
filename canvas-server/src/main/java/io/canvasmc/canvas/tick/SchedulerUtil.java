package io.canvasmc.canvas.tick;

import ca.spottedleaf.concurrentutil.numa.OSNuma;
import ca.spottedleaf.concurrentutil.scheduler.EDFSchedulerThreadPool;
import ca.spottedleaf.concurrentutil.scheduler.Scheduler;
import ca.spottedleaf.concurrentutil.scheduler.StealingScheduledThreadPool;
import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.spark.profiler.RegionScheduleHandlePinner;
import io.canvasmc.canvas.spark.profiler.SparkRegionProfilerExtension;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import java.util.concurrent.ThreadFactory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jspecify.annotations.NonNull;

import static io.canvasmc.canvas.Config.LOGGER;

public class SchedulerUtil {
    private static SchedulerHandler HANDLER;

    public static SchedulerHandler getHandle() {
        if (HANDLER == null) {
            throw new IllegalStateException("Scheduler has not been initialized yet");
        }
        return HANDLER;
    }

    public static void startScheduler() {
        final Scheduler scheduler = TickRegions.getScheduler().scheduler;
        if (scheduler instanceof EDFSchedulerThreadPool edfSchedulerThreadPool) {
            edfSchedulerThreadPool.start();
        }
        if (scheduler instanceof CRSThreadPool crsThreadPool) {
            crsThreadPool.start();
        }
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
            case CRS: {
                long runBufferNanos = (long) (Config.INSTANCE.scheduler.runTasksBufferMillis * 1_000_000L);
                long stealThresh = Config.INSTANCE.scheduler.stealThresholdMillis * 1_000_000L;
                CRSThreadPool scheduler = new CRSThreadPool(initialThreads, threadFactory, runBufferNanos, stealThresh);
                // configure if we can use CRS pinning
                if (initialThreads < 2) {
                    LOGGER.warn("Too little threads allocated, cannot enable region profiling capabilities");
                    scheduler.markPinningUnsupported();
                }
                HANDLER = new CRSHandler();
                return scheduler;
            }
            default: {
                throw new IllegalStateException("Unknown scheduler type: " + schedulerType);
            }
        }
    }

    public interface SchedulerHandler {
        boolean isRunningRegionProfiler();

        boolean isRunningRegionProfilerOnThread(final long threadId, final String threadName);

        void tryTransferPinningState(TickRegionScheduler.@NonNull RegionScheduleHandle ret, TickRegionScheduler.RegionScheduleHandle to);

        void onRegionMerge(TickRegions.TickRegionData from, TickRegions.TickRegionData to, ServerLevel world);

        void onRegionSplit(TickRegions.TickRegionData from, Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> into, ServerLevel world);

        void onRegionDestroy(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region);

        void onRegionInactive(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region);
    }

    public static class NullHandler implements SchedulerHandler {
        @Override
        public boolean isRunningRegionProfiler() {
            return false;
        }

        @Override
        public boolean isRunningRegionProfilerOnThread(final long threadId, final String threadName) {
            return false;
        }

        @Override
        public void tryTransferPinningState(final TickRegionScheduler.@NonNull RegionScheduleHandle ret, final TickRegionScheduler.RegionScheduleHandle to) {
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

    @Deprecated(forRemoval = true)
    public static class CRSHandler implements SchedulerHandler {
        @Override
        public boolean isRunningRegionProfiler() {
            if (TickRegions.getScheduler().scheduler instanceof CRSThreadPool) {
                CRSThreadPool.TickThreadRunner threadRunner =
                    SparkRegionProfilerExtension.TRACKING_THREAD.get();
                return threadRunner != null;
            }
            return false;
        }

        @Override
        public boolean isRunningRegionProfilerOnThread(final long threadId, final String threadName) {
            if (TickRegions.getScheduler().scheduler instanceof CRSThreadPool) {
                CRSThreadPool.TickThreadRunner threadRunner =
                    SparkRegionProfilerExtension.TRACKING_THREAD.get();
                return threadRunner.backingThread.getName().equalsIgnoreCase(threadName);
            }
            return false;
        }

        @Override
        public void tryTransferPinningState(final TickRegionScheduler.@NonNull RegionScheduleHandle ret, final TickRegionScheduler.RegionScheduleHandle to) {
            if (ret.state instanceof CRSThreadPool.ScheduledState crsState)
                crsState.pin(((CRSThreadPool.ScheduledState) to.state).getPinnedThreadId(), crsState.schedulerOwnedBy);
        }

        @Override
        public void onRegionMerge(final TickRegions.@NonNull TickRegionData from, final TickRegions.TickRegionData to, final ServerLevel world) {
            TickRegionScheduler.RegionScheduleHandle scheduleHandle = from.getRegionSchedulingHandle();
            CRSThreadPool.TickThreadRunner tickThreadRunner = SparkRegionProfilerExtension.TRACKING_THREAD.get();
            if (scheduleHandle.state != null && ((CRSThreadPool.ScheduledState) scheduleHandle.state).isPinned() && tickThreadRunner != null) {
                ChunkPos pos = new ChunkPos(RegionScheduleHandlePinner.RegionPinner.PROFILING_CHUNKS.getArray()[0]);
                RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                    world, pos.x, pos.z, () -> {
                        ((CRSThreadPool.ScheduledState) scheduleHandle.state).unpin(tickThreadRunner.scheduler);
                        ((CRSThreadPool.ScheduledState) TickRegionScheduler.getCurrentTickingTask().state).pin(tickThreadRunner.id, tickThreadRunner.scheduler);
                    }
                );
            }
        }

        @Override
        public void onRegionSplit(final TickRegions.@NonNull TickRegionData from, final Long2ReferenceOpenHashMap<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> into, final ServerLevel world) {
            TickRegionScheduler.RegionScheduleHandle scheduleHandle = from.getRegionSchedulingHandle();
            CRSThreadPool.TickThreadRunner tickThreadRunner =
                SparkRegionProfilerExtension.TRACKING_THREAD.get();
            if (scheduleHandle.state != null && ((CRSThreadPool.ScheduledState) scheduleHandle.state).isPinned() && tickThreadRunner != null) {
                // a profiler IS running, ON THIS REGION, split time!!
                // we know this is safe because if this is pinned, we have a profiling set of chunks
                long[] curr = RegionScheduleHandlePinner.RegionPinner.PROFILING_CHUNKS.getArray();
                ChunkPos entry0 = new ChunkPos(curr[0]);
                RegionizedServer.getInstance().taskQueue.queueTickTaskQueue(
                    world, entry0.x, entry0.z, () -> {
                        ((CRSThreadPool.ScheduledState) scheduleHandle.state).unpin(tickThreadRunner.scheduler);
                        ((CRSThreadPool.ScheduledState) TickRegionScheduler.getCurrentTickingTask().state).pin(tickThreadRunner.id, tickThreadRunner.scheduler);
                    }
                );
            }
        }

        @Override
        public void onRegionDestroy(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region) {
            TickRegionScheduler.RegionScheduleHandle scheduleHandle = region.getData().getRegionSchedulingHandle();
            CRSThreadPool.TickThreadRunner tickThreadRunner = SparkRegionProfilerExtension.TRACKING_THREAD.get();
            if (tickThreadRunner != null && scheduleHandle.state != null && ((CRSThreadPool.ScheduledState) scheduleHandle.state).isPinned()) {
                ((CRSThreadPool.ScheduledState) scheduleHandle.state).unpin(tickThreadRunner.scheduler);
            }
        }

        @Override
        public void onRegionInactive(final ThreadedRegionizer.@NonNull ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData> region) {
            final TickRegions.TickRegionData data = region.getData();
            CRSThreadPool.TickThreadRunner tickThreadRunner = SparkRegionProfilerExtension.TRACKING_THREAD.get();
            if (tickThreadRunner != null && data.tickHandle.state != null && ((CRSThreadPool.ScheduledState) data.tickHandle.state).isPinned()) {
                ((CRSThreadPool.ScheduledState) data.tickHandle.state).unpin(tickThreadRunner.scheduler);
            }
        }
    }
}
