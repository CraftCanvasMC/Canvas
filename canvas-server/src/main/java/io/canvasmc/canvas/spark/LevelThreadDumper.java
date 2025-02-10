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
import org.jetbrains.annotations.Nullable;

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
        return isThreadIncluded(findThreadByName(threadName));
    }

    public boolean isThreadIncluded(Thread thread) {
        return ThreadedBukkitServer.getInstance().isLevelThread(thread) || thread.equals(mainThread);
    }

    @Override
    public ThreadInfo @NotNull [] dumpThreads(ThreadMXBean threadBean) {
        return this.threadFinder.getThreads()
                                .filter(this::isThreadIncluded)
                                .map((thread) -> threadBean.getThreadInfo(thread.threadId(), Integer.MAX_VALUE))
                                .filter(Objects::nonNull).toArray(ThreadInfo[]::new);
    }

    @Override
    public SparkSamplerProtos.SamplerMetadata.ThreadDumper getMetadata() {
        return SparkSamplerProtos.SamplerMetadata.ThreadDumper.newBuilder().setType(SparkSamplerProtos.SamplerMetadata.ThreadDumper.Type.SPECIFIC)
                                                              .addAllIds(Arrays.stream(ThreadedServer.getLevelIds()).toList()).addIds(mainThreadId).build();
    }

    private static @Nullable Thread findThreadByName(String threadName) {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread.getName().equals(threadName)) {
                return thread;
            }
        }
        return null;
    }
}
