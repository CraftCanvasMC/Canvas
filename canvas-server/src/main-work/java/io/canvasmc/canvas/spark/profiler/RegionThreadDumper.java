package io.canvasmc.canvas.spark.profiler;

import io.canvasmc.canvas.tick.SchedulerUtil;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import me.lucko.spark.paper.common.sampler.ThreadDumper;
import me.lucko.spark.paper.common.util.ThreadFinder;
import me.lucko.spark.paper.proto.SparkSamplerProtos;

/**
 * A {@link ThreadDumper} implementation that works similar to the {@link ThreadDumper.Regex} dumper, however it will
 * return the tracking thread <i>only</i> if a region profiler is currently running.
 *
 * @author dueris
 */
public class RegionThreadDumper implements ThreadDumper {
    private final ThreadFinder threadFinder = new ThreadFinder();
    private final Map<Long, Boolean> cache = new HashMap<>();
    private final Pattern regionThreadNamePattern = Pattern.compile("Folia Region Scheduler Thread #\\d+", Pattern.CASE_INSENSITIVE);

    @Override
    public boolean isThreadIncluded(long threadId, String threadName) {
        if (SchedulerUtil.getHandle().isRunningRegionProfiler()) {
            return SchedulerUtil.getHandle().isRunningRegionProfilerOnThread(threadId, threadName);
        }

        // get from cache or compute+cache
        return this.cache.computeIfAbsent(threadId,
            id -> this.regionThreadNamePattern.matcher(threadName).matches());
    }

    @Override
    public ThreadInfo[] dumpThreads(ThreadMXBean threadBean) {
        return this.threadFinder.getThreads().filter((thread) -> this.isThreadIncluded(thread.threadId(), thread.getName())).map((thread) -> threadBean.getThreadInfo(thread.threadId(), Integer.MAX_VALUE)).filter(Objects::nonNull).toArray(ThreadInfo[]::new);
    }

    @Override
    public SparkSamplerProtos.SamplerMetadata.ThreadDumper getMetadata() {
        // SPECIFIC, WE ARE AN INTERCHANGEABLE SYSTEM
        return SparkSamplerProtos.SamplerMetadata.ThreadDumper.newBuilder().setType(SparkSamplerProtos.SamplerMetadata.ThreadDumper.Type.SPECIFIC).build();
    }
}
