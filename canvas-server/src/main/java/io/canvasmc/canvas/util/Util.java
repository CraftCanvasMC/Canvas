package io.canvasmc.canvas.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public class Util {
    private static final ThreadLocal<XoroshiroRandomSource> xoroshiro = ThreadLocal.withInitial(() -> new XoroshiroRandomSource(0L, 0L));
    private static final ThreadLocal<SingleThreadedRandomSource> simple = ThreadLocal.withInitial(() -> new SingleThreadedRandomSource(0L));

    public static void derive(PositionalRandomFactory deriver, RandomSource random, int x, int y, int z) {
        if (deriver instanceof final XoroshiroRandomSource.XoroshiroPositionalRandomFactory deriver1) {
            final Xoroshiro128PlusPlus implementation = ((XoroshiroRandomSource) random).randomNumberGenerator;
            implementation.seedLo = (Mth.getSeed(x, y, z) ^ deriver1.seedLo);
            implementation.seedHi = (deriver1.seedHi);
            return;
        }
        if (deriver instanceof final LegacyRandomSource.LegacyPositionalRandomFactory deriver1) {
            final SingleThreadedRandomSource random1 = (SingleThreadedRandomSource) random;
            random1.setSeed(Mth.getSeed(x, y, z) ^ deriver1.seed);
            return;
        }
        throw new IllegalArgumentException();
    }

    public static RandomSource getThreadLocalRandom(PositionalRandomFactory deriver) {
        if (deriver instanceof XoroshiroRandomSource.XoroshiroPositionalRandomFactory) {
            return xoroshiro.get();
        }
        if (deriver instanceof LegacyRandomSource.LegacyPositionalRandomFactory) {
            return simple.get();
        }
        throw new IllegalArgumentException();
    }

    @Contract("null -> fail")
    public static @NonNull RandomSource getRandom(PositionalRandomFactory deriver) {
        if (deriver instanceof XoroshiroRandomSource.XoroshiroPositionalRandomFactory) {
            return new XoroshiroRandomSource(0L, 0L);
        }
        if (deriver instanceof LegacyRandomSource.LegacyPositionalRandomFactory) {
            return new SingleThreadedRandomSource(0L);
        }
        throw new IllegalArgumentException();
    }

    /**
     * This method is derived from C2ME as part of the moonrise executor rewrite fixes
     *
     * @author ishland
     */
    public static <T> T joinFuture(@NonNull CompletableFuture<T> future) {
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
