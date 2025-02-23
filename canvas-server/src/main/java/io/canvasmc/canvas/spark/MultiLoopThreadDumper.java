package io.canvasmc.canvas.spark;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import me.lucko.spark.paper.common.sampler.ThreadDumper;
import me.lucko.spark.paper.common.util.ThreadFinder;
import me.lucko.spark.paper.proto.SparkSamplerProtos;
import org.jetbrains.annotations.NotNull;

public final class MultiLoopThreadDumper implements ThreadDumper {
    public static final ConcurrentLinkedQueue<String> REGISTRY = new ConcurrentLinkedQueue<>();
    private final ThreadFinder threadFinder = new ThreadFinder();
    private static final int STACK_TRACE_DEPTH = 10;

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
        ThreadInfo[] threads = dumpThreads(ManagementFactory.getThreadMXBean());

        return SparkSamplerProtos.SamplerMetadata.ThreadDumper.newBuilder()
                                                              .setType(SparkSamplerProtos.SamplerMetadata.ThreadDumper.Type.SPECIFIC)
                                                              .addAllIds(validThreadIds(threads))
                                                              .build();
    }

    public List<Long> validThreadIds(ThreadInfo[] threads) {
        return Arrays.stream(threads).map(ThreadInfo::getThreadId).collect(Collectors.toList());
    }

    public static void removeRegistryEntry(String prefix) {
        REGISTRY.remove(prefix);
    }
}
