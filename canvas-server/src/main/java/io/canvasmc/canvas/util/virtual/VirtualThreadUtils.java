package io.canvasmc.canvas.util.virtual;

import io.canvasmc.canvas.Config;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

/**
 * @author dueris
 */
public class VirtualThreadUtils {
    public static VirtualThreadService SERVICE = null;

    public static void init() {
        // checks if virtual threads are compatible with the current runtime, and if not, we kill
        // we also run this early, given this is only thread-safe after the first run
        VirtualThreadService service = VirtualThreadService.get();
        if (service == null) {
            throw new IllegalStateException("Unable to run virtual thread service on this version of Java");
        }
        SERVICE = service;
        Config.LOGGER.info(Component.text("Virtual thread service instantiated and ready for run", NamedTextColor.GREEN));
    }

    public static @NotNull ThreadFactory buildFactory(@NotNull Consumer<Thread.Builder.OfVirtual> modifier) {
        Thread.Builder.OfVirtual ofVirtual = Thread.ofVirtual();
        modifier.accept(ofVirtual);
        return ofVirtual.factory();
    }

    public static @NotNull ExecutorService createFixedExecutor(int threads, boolean useVirtual, ThreadFactory fallbackFactory) {
        if (useVirtual) {
            return Executors.newThreadPerTaskExecutor(SERVICE.createFactory());
        }
        return Executors.newFixedThreadPool(threads, fallbackFactory);
    }

    public static @NotNull ExecutorService createFixedExecutor(int threads, boolean useVirtual, ThreadFactory fallbackFactory, ThreadFactory virtualFactory) {
        if (useVirtual) {
            return Executors.newThreadPerTaskExecutor(virtualFactory);
        }
        return Executors.newFixedThreadPool(threads, fallbackFactory);
    }

    public static @NotNull ExecutorService createPerThreadVirtualExecutor(ThreadFactory factory) {
        return Executors.newThreadPerTaskExecutor(factory);
    }
}
