package io.canvasmc.canvas.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NullMarked;

@NullMarked
public class FasterRandomSource implements BitRandomSource {

    private static final RandomGeneratorFactory<RandomGenerator> FACTORY = RandomGeneratorFactory.of("Xoroshiro128PlusPlus");
    private RandomGenerator rng;

    public FasterRandomSource(long seed) {
        this.rng = FACTORY.create(seed);
    }

    @Contract(" -> new")
    @Override
    public final RandomSource fork() {
        return new FasterRandomSource(this.nextLong());
    }

    @Contract(" -> new")
    @Override
    public final PositionalRandomFactory forkPositional() {
        return new FasterRandomSourcePositionalRandomFactory(this.nextLong());
    }

    @Override
    public final void setSeed(long seed) {
        this.rng = FACTORY.create(seed);
    }

    @Override
    public final double nextGaussian() {
        return rng.nextGaussian();
    }

    @Override
    public final int next(int bits) {
        return (int) (nextLong() >>> (64 - bits));
    }

    @Override
    public final int nextInt() {
        return rng.nextInt();
    }

    @Override
    public final int nextInt(int bound) {
        return rng.nextInt(bound);
    }

    @Override
    public final long nextLong() {
        return rng.nextLong();
    }

    @Override
    public final boolean nextBoolean() {
        return rng.nextBoolean();
    }

    @Override
    public final float nextFloat() {
        return rng.nextFloat();
    }

    @Override
    public final double nextDouble() {
        return rng.nextDouble();
    }

    public static class FasterRandomSourcePositionalRandomFactory implements PositionalRandomFactory {
        private final long seed;

        public FasterRandomSourcePositionalRandomFactory(long seed) {
            this.seed = seed;
        }

        @Override
        public RandomSource fromHashOf(String seed) {
            int i = seed.hashCode();
            return new FasterRandomSource((long) i ^ this.seed);
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new FasterRandomSource(seed);
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            //noinspection deprecation
            long l = Mth.getSeed(x, y, z);
            long m = l ^ this.seed;
            return new FasterRandomSource(m);
        }

        @VisibleForTesting
        @Override
        public void parityConfigString(StringBuilder info) {
            info.append("FasterRandomSourcePositionalRandomFactory{").append(this.seed).append("}");
        }
    }
}
