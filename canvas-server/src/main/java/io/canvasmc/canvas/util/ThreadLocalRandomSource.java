package io.canvasmc.canvas.util;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

public final class ThreadLocalRandomSource implements BitRandomSource {

    public static final ThreadLocalRandomSource INSTANCE = new ThreadLocalRandomSource();

    private final PositionalRandomFactory positionalRandomFactory = new ThreadLocalRandomPositionalRandomFactory();

    private ThreadLocalRandomSource() {}

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
        return ThreadLocalRandom.current().nextInt(bound);
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

    private static final class ThreadLocalRandomPositionalRandomFactory implements PositionalRandomFactory {

        @Override
        public RandomSource fromHashOf(final String seed) {
            return ThreadLocalRandomSource.INSTANCE;
        }

        @Override
        public RandomSource fromSeed(final long seed) {
            return ThreadLocalRandomSource.INSTANCE;
        }

        @Override
        public RandomSource at(final int x, final int y, final int z) {
            return ThreadLocalRandomSource.INSTANCE;
        }

        @Override
        public void parityConfigString(final StringBuilder info) {
            info.append("ThreadLocalRandomPositionalRandomFactory{}");
        }
    }
}
