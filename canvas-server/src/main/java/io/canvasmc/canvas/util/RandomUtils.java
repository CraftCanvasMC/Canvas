package io.canvasmc.canvas.util;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;
import net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import org.jetbrains.annotations.NotNull;

public class RandomUtils {

    public static @NotNull RandomSource getRandom(PositionalRandomFactory deriver) {
        if (deriver instanceof XoroshiroRandomSource.XoroshiroPositionalRandomFactory) {
            return new XoroshiroRandomSource(0L, 0L);
        }
        if (deriver instanceof LegacyRandomSource.LegacyPositionalRandomFactory) {
            return new SingleThreadedRandomSource(0L);
        }
        throw new IllegalArgumentException();
    }

    private static final ThreadLocal<XoroshiroRandomSource> xoroshiro = ThreadLocal.withInitial(() -> new XoroshiroRandomSource(0L, 0L));
    private static final ThreadLocal<SingleThreadedRandomSource> simple = ThreadLocal.withInitial(() -> new SingleThreadedRandomSource(0L));

    public static void derive(PositionalRandomFactory deriver, RandomSource random, int x, int y, int z) {
        if (deriver instanceof final XoroshiroRandomSource.XoroshiroPositionalRandomFactory deriver1) {
            final Xoroshiro128PlusPlus implementation = ((XoroshiroRandomSource) random).randomNumberGenerator;
            implementation.seedLo = (Mth.getSeed(x, y, z) ^ deriver1.seedLo());
            implementation.seedHi = (deriver1.seedHi());
            return;
        }
        if (deriver instanceof LegacyRandomSource.LegacyPositionalRandomFactory(long seed)) {
            final SingleThreadedRandomSource random1 = (SingleThreadedRandomSource) random;
            random1.setSeed(Mth.getSeed(x, y, z) ^ seed);
            return;
        }
        throw new IllegalArgumentException();
    }
}
