package io.canvasmc.canvas.entity.pathfinding;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.canvasmc.canvas.Config;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * used to handle the scheduling of async path processing
 */
public class AsyncPathProcessor {
    private static final Executor pathProcessingExecutor = new ThreadPoolExecutor(
        1,
        Config.INSTANCE.entities.pathfinding.maxProcessors,
        Config.INSTANCE.entities.pathfinding.keepAlive, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        new ThreadFactoryBuilder()
            .setNameFormat("pathfinding")
            .setPriority(Thread.NORM_PRIORITY - 2)
            .build()
    );

    protected static CompletableFuture<Void> queue(@NotNull AsyncPath path) {
        return CompletableFuture.runAsync(path::process, pathProcessingExecutor);
    }

    /**
     * takes a possibly unprocessed path, and waits until it is completed
     * the consumer will be immediately invoked if the path is already processed
     * the consumer will always be called on the main thread
     *
     * @param path            a path to wait on
     * @param afterProcessing a consumer to be called
     */
    public static void awaitProcessing(@Nullable Path path, final @NotNull Level level, Consumer<@Nullable Path> afterProcessing) {
        if (!(level instanceof ServerLevel serverLevel))
            throw new IllegalArgumentException("Level must be a ServerLevel to execute processing.");
        if (path != null && !path.isProcessed() && path instanceof AsyncPath asyncPath) {
            asyncPath.postProcessing(() -> {
                if (MinecraftServer.getThreadedServer().hasStarted()) {
                    // Schedule on level instead of main.
                    serverLevel.scheduleOnThread(serverLevel.wrapRunnable(() -> afterProcessing.accept(path)));
                    return;
                }
                MinecraftServer.getServer().scheduleOnMain(() -> afterProcessing.accept(path));
            });
        } else {
            afterProcessing.accept(path);
        }
    }
}
