package io.canvasmc.canvas.server.chunk;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.util.NamedAgnosticThreadFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class ChunkSendingExecutor {
    private static final ExecutorService SERVICE = Config.INSTANCE.chunks.chunkSending.asyncChunkSending ?
        Config.INSTANCE.chunks.chunkSending.useVirtualThreadExecutorForChunkSenders ?
            Executors.newVirtualThreadPerTaskExecutor() :
            Executors.newFixedThreadPool(
                Config.INSTANCE.chunks.chunkSending.asyncChunkSendingThreadCount,
                new NamedAgnosticThreadFactory<>("chunk_sending", TickThread::new, Thread.NORM_PRIORITY)
            ) : null;

    public static void execute(Runnable runnable, ServerLevel level) {
        runnable = wrapRunnable(runnable, level);
        if (Config.INSTANCE.chunks.chunkSending.asyncChunkSending) {
            SERVICE.submit(runnable);
        } else {
            runnable.run();
        }
    }

    @Contract(pure = true)
    private static @NotNull Runnable wrapRunnable(Runnable runnable, final ServerLevel level) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                MinecraftServer.LOGGER.warn("Failed to send chunk data, attempting retry, if no errors occur related to chunk sending immediately after this, the retry was successful.");
                level.scheduleOnMain(() -> {
                    try {
                        runnable.run();
                    } catch (Throwable failed) {
                        MinecraftServer.LOGGER.error("Failed 2nd attempt for chunk data sending, logging stacktrace.", failed);
                    }
                });
            }
        };
    }
}
