package io.canvasmc.canvas.spark.plugin;

import io.canvasmc.canvas.threadedregions.profiler.RegionProfiler;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.util.function.Consumer;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.paper.common.monitor.tick.TickStatistics;
import net.minecraft.server.level.ServerLevel;

public class FoliaTickStatistics implements TickStatistics {
    private static final AverageInfo EMPTY_AVERAGE_INFO = new AverageInfo();

    @Override
    public double tps5Sec() {
        return getTpsFor(TimeSpan.TPS_5_SEC);
    }

    @Override
    public double tps10Sec() {
        return getTpsFor(TimeSpan.TPS_10_SEC);
    }

    @Override
    public double tps1Min() {
        return getTpsFor(TimeSpan.TPS_1_MIN);
    }

    @Override
    public double tps5Min() {
        return getTpsFor(TimeSpan.TPS_5_MIN);
    }

    @Override
    public double tps15Min() {
        return getTpsFor(TimeSpan.TPS_15_MIN);
    }

    @Override
    public boolean isDurationSupported() {
        return false;
    }

    @Override
    public DoubleAverageInfo duration10Sec() {
        return EMPTY_AVERAGE_INFO;
    }

    @Override
    public DoubleAverageInfo duration1Min() {
        return EMPTY_AVERAGE_INFO;
    }

    @Override
    public DoubleAverageInfo duration5Min() {
        return EMPTY_AVERAGE_INFO;
    }

    private static double getTpsFor(final TimeSpan span) {
        // check for a profiling region first
        final RegionProfiler.ProfilingState profilingState = RegionProfiler.STATE.get();
        if (profilingState != null) {
            return getTpsFor(profilingState.regionScheduleHandle(), span);
        }

        // if not profiling a specific region, we should average all handles
        final DoubleArrayList tpsCounts = new DoubleArrayList();
        final Consumer<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> forEachRegion =
            (region) -> tpsCounts.add(getTpsFor(region.getData().getRegionSchedulingHandle(), span));

        // global tick should be included
        tpsCounts.add(getTpsFor(RegionizedServer.getGlobalTickData(), span));

        for (final ServerLevel level : RegionizedServer.getInstance().worlds) {
            level.regioniser.computeForAllRegionsUnsynchronised(forEachRegion);
        }

        if (tpsCounts.isEmpty()) {
            // if the tps counts are empty, we should just return
            // the default rate for now
            return TickRegionScheduler.getTickRate();
        }

        // return the average TPS from the collected counts
        return tpsCounts.doubleStream().sum() / tpsCounts.size();
    }

    private static double getTpsFor(final TickRegionScheduler.RegionScheduleHandle scheduleHandle, final TimeSpan span) {
        final long interval = TickRegionScheduler.getTimeBetweenTicks();
        final Double tpsAverage = switch (span) {
            case TPS_1_MIN -> scheduleHandle.tickTimes1m.getTPSAverage(null, interval);
            case TPS_5_MIN -> scheduleHandle.tickTimes5m.getTPSAverage(null, interval);
            case TPS_5_SEC -> scheduleHandle.tickTimes5s.getTPSAverage(null, interval);
            case TPS_10_SEC -> // we don't have a 15s tick time, close enough
                scheduleHandle.tickTimes15s.getTPSAverage(null, interval);
            case TPS_15_MIN -> scheduleHandle.tickTimes15m.getTPSAverage(null, interval);
        };

        // if tpsAverage is null, the time data is empty,
        // just return the default rate in this case
        if (tpsAverage == null) {
            return TickRegionScheduler.getTickRate();
        }

        return tpsAverage;
    }

    private enum TimeSpan {
        TPS_5_SEC,
        TPS_10_SEC,
        TPS_1_MIN,
        TPS_5_MIN,
        TPS_15_MIN
    }

    private static final class AverageInfo implements DoubleAverageInfo {

        @Override
        public double mean() {
            return 0;
        }

        @Override
        public double max() {
            return 0;
        }

        @Override
        public double min() {
            return 0;
        }

        @Override
        public double median() {
            return DoubleAverageInfo.super.median();
        }

        @Override
        public double percentile95th() {
            return DoubleAverageInfo.super.percentile95th();
        }

        @Override
        public double percentile(final double v) {
            return 0;
        }
    }
}
