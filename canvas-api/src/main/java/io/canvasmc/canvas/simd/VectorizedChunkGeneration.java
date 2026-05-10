package io.canvasmc.canvas.simd;

import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

public final class VectorizedChunkGeneration {

    private static final VectorSpecies<Integer> I_SPEC = IntVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> PAIR_SPEC = IntVector.SPECIES_64;

    private static final ThreadLocal<int[]> PAIR_X = ThreadLocal.withInitial(() -> new int[PAIR_SPEC.length()]);
    private static final ThreadLocal<int[]> PAIR_Y = ThreadLocal.withInitial(() -> new int[PAIR_SPEC.length()]);
    private static final ThreadLocal<int[]> PAIR_Z = ThreadLocal.withInitial(() -> new int[PAIR_SPEC.length()]);
    private static final ThreadLocal<int[]> PAIR_OUT = ThreadLocal.withInitial(() -> new int[PAIR_SPEC.length()]);

    private static volatile boolean startupConfigured;
    private static volatile boolean enabled;

    private VectorizedChunkGeneration() {
    }

    public static void configureStartup(final boolean enabledInConfig, final boolean simdEnabled) {
        if (startupConfigured) {
            return;
        }
        enabled = enabledInConfig && simdEnabled;
        startupConfigured = true;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int preferredVectorBitSize() {
        return I_SPEC.vectorBitSize();
    }

    public static boolean hasAvx3fCompatibleWidth() {
        return I_SPEC.vectorBitSize() >= 256;
    }

    public static long squaredDistancePair(
        final int dx0, final int dy0, final int dz0,
        final int dx1, final int dy1, final int dz1
    ) {
        if (!enabled) {
            final int dist0 = dx0 * dx0 + dy0 * dy0 + dz0 * dz0;
            final int dist1 = dx1 * dx1 + dy1 * dy1 + dz1 * dz1;
            return ((long) dist0 << 32) | (dist1 & 0xffffffffL);
        }

        final int[] x = PAIR_X.get();
        final int[] y = PAIR_Y.get();
        final int[] z = PAIR_Z.get();
        x[0] = dx0;
        x[1] = dx1;
        y[0] = dy0;
        y[1] = dy1;
        z[0] = dz0;
        z[1] = dz1;

        final IntVector dx = IntVector.fromArray(PAIR_SPEC, x, 0);
        final IntVector dy = IntVector.fromArray(PAIR_SPEC, y, 0);
        final IntVector dz = IntVector.fromArray(PAIR_SPEC, z, 0);
        final IntVector dist = dx.mul(dx).add(dy.mul(dy)).add(dz.mul(dz));

        final int[] out = PAIR_OUT.get();
        dist.intoArray(out, 0);
        return ((long) out[0] << 32) | (out[1] & 0xffffffffL);
    }

    public static void squaredDistances3d(
        final int[] absoluteX,
        final int[] absoluteY,
        final int[] absoluteZ,
        final int count,
        final int originX,
        final int originY,
        final int originZ,
        final int[] out
    ) {
        final int length = Math.min(count, Math.min(Math.min(absoluteX.length, absoluteY.length), Math.min(absoluteZ.length, out.length)));
        if (length <= 0) {
            return;
        }

        if (!enabled) {
            for (int i = 0; i < length; i++) {
                final int dx = absoluteX[i] - originX;
                final int dy = absoluteY[i] - originY;
                final int dz = absoluteZ[i] - originZ;
                out[i] = dx * dx + dy * dy + dz * dz;
            }
            return;
        }

        final int speciesLength = I_SPEC.length();
        final int upperBound = length - (length % speciesLength);
        final IntVector ox = IntVector.broadcast(I_SPEC, originX);
        final IntVector oy = IntVector.broadcast(I_SPEC, originY);
        final IntVector oz = IntVector.broadcast(I_SPEC, originZ);

        int i = 0;
        for (; i < upperBound; i += speciesLength) {
            final IntVector dx = IntVector.fromArray(I_SPEC, absoluteX, i).sub(ox);
            final IntVector dy = IntVector.fromArray(I_SPEC, absoluteY, i).sub(oy);
            final IntVector dz = IntVector.fromArray(I_SPEC, absoluteZ, i).sub(oz);
            final IntVector dist = dx.mul(dx).add(dy.mul(dy)).add(dz.mul(dz));
            dist.intoArray(out, i);
        }

        if (i < length) {
            final VectorMask<Integer> mask = I_SPEC.indexInRange(i, length);
            final IntVector dx = IntVector.fromArray(I_SPEC, absoluteX, i, mask).sub(ox);
            final IntVector dy = IntVector.fromArray(I_SPEC, absoluteY, i, mask).sub(oy);
            final IntVector dz = IntVector.fromArray(I_SPEC, absoluteZ, i, mask).sub(oz);
            final IntVector dist = dx.mul(dx).add(dy.mul(dy)).add(dz.mul(dz));
            dist.intoArray(out, i, mask);
        }
    }
}
