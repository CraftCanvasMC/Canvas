package io.canvasmc.canvas.spark;

import io.canvasmc.canvas.threadedregions.SchedulerUtil;
import me.lucko.spark.paper.common.tick.AbstractTickReporter;
import me.lucko.spark.paper.common.tick.TickReporter;

public class FoliaTickReporter extends AbstractTickReporter implements TickReporter {

    FoliaTickReporter() {
    }

    @Override
    public void start() {
    }

    @Override
    public void close() {
    }

    @Override
    public void onTick(double duration) {
        if (SchedulerUtil.getHandle().isRunningRegionProfiler()) {
            final Thread thread = Thread.currentThread();
            if (!SchedulerUtil.getHandle().isRunningRegionProfilerOnThread(thread.threadId(), thread.getName())) return;
            super.onTick(duration);
        }
    }
}
