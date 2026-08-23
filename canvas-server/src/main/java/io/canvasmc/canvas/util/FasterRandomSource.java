package io.canvasmc.canvas.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.BitRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.jspecify.annotations.NullMarked;

/**
 * This class is derived from the Leaf faster RNG patch as linked below:
 * <p>
 * <a
 * href="https://github.com/Winds-Studio/Leaf/blob/ver/26.2/leaf-server/src/main/java/org/dreeam/leaf/util/math/random/FasterRandomSource.java">FasterRandomSource.java</a>
 * <p>
 * The original patch files:
 * <ol>
 *     <li><a href="https://github.com/Winds-Studio/Leaf/blob/ver/26.2/leaf-server/paper-patches/features/0037-Faster-random-generator.patch">paper-patches/...</a></li>
 *     <li><a href="https://github.com/Winds-Studio/Leaf/blob/ver/26.2/leaf-server/minecraft-patches/features/0121-Faster-random-generator.patch">minecraft-patches/...</a></li>
 * </ol>
 * <p>
 * As stated in <a href="https://github.com/Winds-Studio/Leaf/blob/ver/26.2/LICENSE.md">Leaf/LICENSE.md</a>, their
 * patches are licensed under MIT unless indicated differently in their header, meaning the license for the original
 * patch this class is from is licensed under <a href="https://opensource.org/license/MIT">MIT</a>
 *
 * @author Winds-Studio/Leaf, HaHaWTH
 */
@NullMarked
public class FasterRandomSource implements BitRandomSource {

    private static final RandomGeneratorFactory<RandomGenerator> FACTORY = RandomGeneratorFactory.of("Xoroshiro128PlusPlus");
    private RandomGenerator rng;

    public FasterRandomSource(final long seed) {
        this.rng = FACTORY.create(seed);
    }

    @Override
    public RandomSource fork() {
        return new FasterRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new FasterRandomSourcePositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(final long seed) {
        this.rng = FACTORY.create(seed);
    }

    @Override
    public double nextGaussian() {
        return rng.nextGaussian();
    }

    @Override
    public void consumeCount(final int count) {
        for (int i = 0; i < count; i++) {
            this.rng.nextLong();
        }
    }

    @Override
    public int nextInt(final int origin, final int bound) {
        return rng.nextInt(origin, bound);
    }

    @Override
    public int next(final int bits) {
        return (int) (nextLong() >>> (64 - bits));
    }

    @Override
    public int nextInt() {
        return rng.nextInt();
    }

    @Override
    public int nextInt(final int bound) {
        return rng.nextInt(bound);
    }

    @Override
    public long nextLong() {
        return rng.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return rng.nextBoolean();
    }

    @Override
    public float nextFloat() {
        return rng.nextFloat();
    }

    @Override
    public double nextDouble() {
        return rng.nextDouble();
    }

    public double nextGaussian(final double mean, final double stddev) {
        return rng.nextGaussian(mean, stddev);
    }

    private record FasterRandomSourcePositionalRandomFactory(long seed) implements PositionalRandomFactory {

        @Override
        public RandomSource fromHashOf(final String seed) {
            int i = seed.hashCode();
            return new FasterRandomSource((long) i ^ this.seed);
        }

        @Override
        public RandomSource fromSeed(final long seed) {
            return new FasterRandomSource(seed);
        }

        @Override
        public RandomSource at(final int x, final int y, final int z) {
            //noinspection deprecation
            return new FasterRandomSource(Mth.getSeed(x, y, z) ^ this.seed);
        }

        @VisibleForTesting
        @Override
        public void parityConfigString(final StringBuilder info) {
            info.append("FasterRandomSourcePositionalRandomFactory{").append(this.seed).append("}");
        }
    }
}
