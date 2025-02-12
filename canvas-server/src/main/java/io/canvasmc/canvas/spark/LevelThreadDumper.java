package io.canvasmc.canvas.spark;

import io.canvasmc.canvas.ThreadedBukkitServer;
import io.canvasmc.canvas.server.ThreadedServer;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Objects;
import me.lucko.spark.paper.common.sampler.ThreadDumper;
import me.lucko.spark.paper.common.util.ThreadFinder;
import me.lucko.spark.paper.proto.SparkSamplerProtos;
import org.jetbrains.annotations.NotNull;

public final class LevelThreadDumper implements ThreadDumper {
    private final ThreadFinder threadFinder = new ThreadFinder();
    private final Thread mainThread;
    private final long mainThreadId;

    public LevelThreadDumper(@NotNull Thread mainThread) {
        this.mainThread = mainThread;
        this.mainThreadId = mainThread.threadId();
    }

    @Override
    public boolean isThreadIncluded(long threadId, String threadName) {
        return mainThread.getName().equalsIgnoreCase(threadName) || ThreadedBukkitServer.getInstance().isLevelThread(threadId) || threadName.startsWith("LevelThread:");
    }

    @Override
    public ThreadInfo @NotNull [] dumpThreads(ThreadMXBean threadBean) {
        return this.threadFinder.getThreads()
                                .filter((thread) -> this.isThreadIncluded(thread.threadId(), thread.getName()))
                                .map((thread) -> threadBean.getThreadInfo(thread.threadId(), Integer.MAX_VALUE))
                                .filter(Objects::nonNull).toArray(ThreadInfo[]::new);
    }

    @Override
    public SparkSamplerProtos.SamplerMetadata.ThreadDumper getMetadata() {
        return SparkSamplerProtos.SamplerMetadata.ThreadDumper.newBuilder().setType(SparkSamplerProtos.SamplerMetadata.ThreadDumper.Type.SPECIFIC)
                                                              .addAllIds(Arrays.stream(ThreadedServer.getLevelIds()).toList()).addIds(mainThreadId).build();
    }

    public Thread getMainThread() {
        return mainThread;
    }
}
