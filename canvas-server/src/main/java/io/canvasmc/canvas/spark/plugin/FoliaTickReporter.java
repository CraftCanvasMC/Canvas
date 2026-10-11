package io.canvasmc.canvas.spark.plugin;

import io.canvasmc.canvas.threadedregions.scheduler.callback.SchedulerCallbacks;
import io.papermc.paper.threadedregions.TickRegions;
import me.lucko.spark.paper.common.tick.AbstractTickReporter;
import me.lucko.spark.paper.common.tick.TickReporter;

public class FoliaTickReporter extends AbstractTickReporter implements TickReporter {

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public void onTick(final double duration) {
        final SchedulerCallbacks callbacks = TickRegions.getScheduler().getSchedulerCallbacks();

        // check if we are profiling at all
        if (!callbacks.isRegionProfiling()) {
            return;
        }

        final Thread thread = Thread.currentThread();
        if (!callbacks.isProfilingThread(thread.threadId(), thread.getName())) {
            return;
        }

        // is running region profiler on this thread, tick
        super.onTick(duration);
    }
}
