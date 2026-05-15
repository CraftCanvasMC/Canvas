package io.canvasmc.canvas.spark.plugin;

import io.canvasmc.canvas.spark.profiler.RegionProfiler;
import io.canvasmc.canvas.tick.SchedulerUtil;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import io.papermc.paper.threadedregions.TickRegions;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import java.util.function.Consumer;
import me.lucko.spark.api.statistic.misc.DoubleAverageInfo;
import me.lucko.spark.paper.common.monitor.tick.TickStatistics;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.NonNull;

public class FoliaTickStatistics implements TickStatistics {
    private static final AverageInfo EMPTY_AVERAGE_INFO = new AverageInfo();

    private static double getTpsFor(TimeSpan span) {
        // we must include global tick, and all regions
        if (isRunningIndependentRegion()) {
            return getTpsFor(RegionProfiler.STATE.get().regionScheduleHandle(), span);
        }
        DoubleArrayList tpsCounts = new DoubleArrayList();
        tpsCounts.add(getTpsFor(RegionizedServer.getGlobalTickData(), span));
        final Consumer<ThreadedRegionizer.ThreadedRegion<TickRegions.TickRegionData, TickRegions.TickRegionSectionData>> threadedRegionConsumer = (region) -> tpsCounts.add(getTpsFor(region.getData().getRegionSchedulingHandle(), span));
        for (final ServerLevel level : RegionizedServer.getInstance().worlds) {
            level.regioniser.computeForAllRegionsUnsynchronised(threadedRegionConsumer);
        }

        if (tpsCounts.isEmpty()) {
            // well if we use 0 then it shows the server DYING... no likey, just make it -1
            return -1.0D;
        }

        double sum = 0.0D;
        for (int i = 0; i < tpsCounts.size(); i++) {
            sum += tpsCounts.getDouble(i);
        }

        return sum / tpsCounts.size();
    }

    private static double getTpsFor(final TickRegionScheduler.RegionScheduleHandle scheduleHandle, final @NonNull TimeSpan span) {
        final long interval = TickRegionScheduler.getTimeBetweenTicks();
        Double d = switch (span) {
            case TPS_1_MIN -> scheduleHandle.tickTimes1m.getTPSAverage(null, interval);
            case TPS_5_MIN -> scheduleHandle.tickTimes5m.getTPSAverage(null, interval);
            case TPS_5_SEC -> scheduleHandle.tickTimes5s.getTPSAverage(null, interval);
            case TPS_10_SEC -> // CLOSE ENOUGH!!
                scheduleHandle.tickTimes15s.getTPSAverage(null, interval);
            case TPS_15_MIN -> scheduleHandle.tickTimes15m.getTPSAverage(null, interval);
        };
        if (d == null) return TickRegionScheduler.getTickRate();
        else return d;
    }

    private static boolean isRunningIndependentRegion() {
        return SchedulerUtil.getHandle().isRunningRegionProfiler();
    }

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
