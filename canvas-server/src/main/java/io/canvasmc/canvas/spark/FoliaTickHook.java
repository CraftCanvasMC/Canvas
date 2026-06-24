package io.canvasmc.canvas.spark;

import io.canvasmc.canvas.threadedregions.SchedulerUtil;
import me.lucko.spark.paper.common.tick.AbstractTickHook;
import me.lucko.spark.paper.common.tick.TickHook;

public class FoliaTickHook extends AbstractTickHook implements TickHook {

    FoliaTickHook() {
    }

    @Override
    public void start() {
    }

    @Override
    public void close() {
    }

    @Override
    public void onTick() {
        if (SchedulerUtil.getHandle().isRunningRegionProfiler()) {
            final Thread thread = Thread.currentThread();
            if (!SchedulerUtil.getHandle().isRunningRegionProfilerOnThread(thread.threadId(), thread.getName())) return;
            super.onTick();
        }
    }
}
