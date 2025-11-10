package io.canvasmc.canvas.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;

/**
 * This file is derived from C2ME as part of the moonrise executor rewrite fixes
 * @author ishland
 */
public class CFUtil {
    public static <T> T join(CompletableFuture<T> future) {
        while (!future.isDone()) {
            LockSupport.parkNanos("Waiting for future", 100000L);
        }
        return future.join();
    }
}
