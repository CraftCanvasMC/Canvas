package io.canvasmc.canvas.util;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

public final class SimpleThreadLocalRandomSource implements BitRandomSource {

    public static final SimpleThreadLocalRandomSource INSTANCE = new SimpleThreadLocalRandomSource();

    private final PositionalRandomFactory positionalRandomFactory = new SimpleThreadLocalRandomPositionalRandomFactory();

    private SimpleThreadLocalRandomSource() {
    }

    @Override
    public int next(final int bits) {
        return ThreadLocalRandom.current().nextInt() >>> (Integer.SIZE - bits);
    }

    @Override
    public int nextInt() {
        return ThreadLocalRandom.current().nextInt();
    }

    @Override
    public int nextInt(final int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException();
        }

        // https://lemire.me/blog/2016/06/27/a-fast-alternative-to-the-modulo-reduction/
        final long value = (long) this.nextInt() & 0xFFFFFFFFL;
        return (int) ((value * (long) bound) >>> Integer.SIZE);
    }

    @Override
    public void setSeed(final long seed) {
        // no-op
    }

    @Override
    public double nextGaussian() {
        return ThreadLocalRandom.current().nextGaussian();
    }

    @Override
    public RandomSource fork() {
        return this;
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return this.positionalRandomFactory;
    }

    private static final class SimpleThreadLocalRandomPositionalRandomFactory implements PositionalRandomFactory {

        @Override
        public RandomSource fromHashOf(final String seed) {
            return SimpleThreadLocalRandomSource.INSTANCE;
        }

        @Override
        public RandomSource fromSeed(final long seed) {
            return SimpleThreadLocalRandomSource.INSTANCE;
        }

        @Override
        public RandomSource at(final int x, final int y, final int z) {
            return SimpleThreadLocalRandomSource.INSTANCE;
        }

        @Override
        public void parityConfigString(final StringBuilder info) {
            info.append("SimpleThreadLocalRandomPositionalRandomFactory{}");
        }
    }
}
