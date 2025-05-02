package io.canvasmc.canvas.spark;

import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import me.lucko.spark.paper.common.sampler.ThreadDumper;
import me.lucko.spark.paper.common.util.ThreadFinder;
import me.lucko.spark.paper.proto.SparkSamplerProtos;
import org.jetbrains.annotations.NotNull;

public final class MultiLoopThreadDumper implements ThreadDumper {
    public static final ConcurrentLinkedQueue<String> REGISTRY = new ConcurrentLinkedQueue<>();
    private static final int STACK_TRACE_DEPTH = 10;
    private final ThreadFinder threadFinder = new ThreadFinder();

    @Override
    public boolean isThreadIncluded(long threadId, String threadName) {
        for (final String prefix : REGISTRY) {
            if (threadName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ThreadInfo @NotNull [] dumpThreads(ThreadMXBean threadBean) {
        return this.threadFinder.getThreads()
            .filter(thread -> isThreadIncluded(thread.threadId(), thread.getName()))
            .map(thread -> threadBean.getThreadInfo(thread.threadId(), STACK_TRACE_DEPTH))
            .filter(Objects::nonNull)
            .toArray(ThreadInfo[]::new);
    }

    @Override
    public SparkSamplerProtos.SamplerMetadata.ThreadDumper getMetadata() {
        return SparkSamplerProtos.SamplerMetadata.ThreadDumper.newBuilder()
            .setType(SparkSamplerProtos.SamplerMetadata.ThreadDumper.Type.SPECIFIC)
            .addAllPatterns(REGISTRY)
            .build();
    }
}
