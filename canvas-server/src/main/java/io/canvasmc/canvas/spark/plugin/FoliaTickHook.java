package io.canvasmc.canvas.spark.plugin;

import io.canvasmc.canvas.threadedregions.SchedulerUtil;
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
        final SchedulerUtil.SchedulerHandler handler = SchedulerUtil.getHandle();
        if (handler.isRunningRegionProfiler()) {
            final Thread thread = Thread.currentThread();
            if (!handler.isRunningRegionProfilerOnThread(thread.threadId(), thread.getName())) {
                return;
            }
            super.onTick();
        }
    }
}
