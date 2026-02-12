package io.canvasmc.canvas.util;

import org.jspecify.annotations.NonNull;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

public class CFUtil {
    /**
     * This method is derived from C2ME as part of the moonrise executor rewrite fixes
     *
     * @author ishland
     */
    public static <T> T join(@NonNull CompletableFuture<T> future) {
        while (!future.isDone()) {
            LockSupport.parkNanos("Waiting for future", 100000L);
        }
        return future.join();
    }

    /**
     * Waits for the future to be done, or for the wait period to be up
     *
     * @param future
     *     the future to wait for
     * @param unit
     *     the time unit
     * @param wait
     *     low long(based on the time unit) to wait
     *
     * @return {@code true} if the future completed before the timeout, {@code false} otherwise
     *
     * @author dueris
     */
    public static boolean waitFor(@NonNull CompletableFuture<Void> future, @NonNull TimeUnit unit, long wait) {
        long waitInNanos = unit.toNanos(wait);
        long targetNanos = System.nanoTime() + waitInNanos;
        while (!future.isDone()) {
            long remaining = targetNanos - System.nanoTime();
            if (remaining <= 0) break;

            LockSupport.parkNanos("Waiting for future", Math.min(remaining, 1_000_000L));
        }
        return future.isDone();
    }
}
