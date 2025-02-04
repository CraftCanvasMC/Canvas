package io.canvasmc.canvas.server.level.chunk;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.util.NamedAgnosticThreadFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChunkSendingExecutor {
    private static final ExecutorService SERVICE = Config.INSTANCE.chunkSending.asyncChunkSending ?
        Config.INSTANCE.chunkSending.useVirtualThreadExecutorForChunkSenders ?
            Executors.newVirtualThreadPerTaskExecutor() :
            Executors.newFixedThreadPool(
                Config.INSTANCE.chunkSending.asyncChunkSendingThreadCount,
                new NamedAgnosticThreadFactory<>("chunk_sending", TickThread::new, Thread.NORM_PRIORITY)
            ) : null;

    public static void execute(Runnable runnable) {
        if (Config.INSTANCE.chunkSending.asyncChunkSending) {
            SERVICE.submit(runnable);
        } else {
            runnable.run();
        }
    }
}
