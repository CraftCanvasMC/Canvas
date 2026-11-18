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

    public static long coordinateKey(final int x, final int z) {
        return packInts(z, x);
    }

    public static int keyX(final long key) {
        return unpackLow(key);
    }

    public static int keyZ(final long key) {
        return unpackHigh(key);
    }
}
