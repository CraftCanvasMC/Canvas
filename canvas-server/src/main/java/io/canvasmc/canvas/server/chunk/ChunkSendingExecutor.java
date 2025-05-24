package io.canvasmc.canvas.server.chunk;

import io.canvasmc.canvas.Config;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class ChunkSendingExecutor {
    private static final ExecutorService SERVICE = Executors.newVirtualThreadPerTaskExecutor();

    public static void execute(ChunkRunnable runnable) {
        runnable = wrapRunnable(runnable);
        if (Config.INSTANCE.chunks.chunkSending.asyncChunkSending) {
            SERVICE.submit(runnable);
        } else {
            runnable.run();
        }
    }

    @Contract(pure = true)
    private static @NotNull ChunkRunnable wrapRunnable(@NotNull ChunkRunnable runnable) {
        return new ChunkRunnable(runnable.chunkX, runnable.chunkZ, runnable.world, () -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                MinecraftServer.LOGGER.warn("Failed to send chunk data, attempting retry, if no errors occur related to chunk sending immediately after this, the retry was successful.");
                Runnable retry = () -> {
                    try {
                        runnable.run();
                    } catch (Throwable failed) {
                        MinecraftServer.LOGGER.error("Failed 2nd attempt for chunk data sending, logging stacktrace.", failed);
                    }
                };
                if (Config.INSTANCE.ticking.enableThreadedRegionizing) {
                    runnable.world.server.threadedServer().taskQueue.queueChunkTask(runnable.world, runnable.chunkX, runnable.chunkZ, retry);
                } else {
                    runnable.world.pushTask(retry);
                }
            }
        });
    }
}
