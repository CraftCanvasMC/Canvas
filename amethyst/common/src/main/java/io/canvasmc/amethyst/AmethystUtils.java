package io.canvasmc.amethyst;

import java.util.concurrent.TimeUnit;

public final class AmethystUtils {
    public static final long ONE_S_NANOS = TimeUnit.SECONDS.toNanos(1L);

    private AmethystUtils() {
    }

    public static long packInts(final int high, final int low) {
        return ((long) high << 32) | (low & 0xFFFFFFFFL);
    }

    public static int unpackHigh(final long packed) {
        return (int) (packed >> 32);
    }

    public static int unpackLow(final long packed) {
        return (int) packed;
    }
}
