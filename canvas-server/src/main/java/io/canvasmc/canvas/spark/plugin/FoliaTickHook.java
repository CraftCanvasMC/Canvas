package io.canvasmc.canvas.spark.plugin;

import io.canvasmc.canvas.threadedregions.scheduler.callback.SchedulerCallbacks;
import io.papermc.paper.threadedregions.TickRegions;
import me.lucko.spark.paper.common.tick.AbstractTickHook;
import me.lucko.spark.paper.common.tick.TickHook;

public class FoliaTickHook extends AbstractTickHook implements TickHook {

    @Override
    public void start() {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public void onTick() {
        final SchedulerCallbacks callbacks = TickRegions.getScheduler().getSchedulerCallbacks();

        // check if we are profiling at all first
        if (!callbacks.isRegionProfiling()) {
            return;
        }

        final Thread thread = Thread.currentThread();
        if (!callbacks.isProfilingThread(thread.threadId(), thread.getName())) {
            return;
        }

        // is running region profiler on this thread, tick
        super.onTick();
    }
}
