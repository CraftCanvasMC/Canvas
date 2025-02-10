package io.canvasmc.canvas.dfc.common.ducks;

import io.canvasmc.canvas.dfc.common.ast.EvalType;
import net.minecraft.world.level.levelgen.DensityFunction;

public interface IFastCacheLike extends DensityFunction {
    long CACHE_MISS_NAN_BITS = 9222769054270909007L;

    double c2me$getCached(int var1, int var2, int var3, EvalType var4);

    boolean c2me$getCached(double[] var1, int[] var2, int[] var3, int[] var4, EvalType var5);

    void c2me$cache(int var1, int var2, int var3, EvalType var4, double var5);

    void c2me$cache(double[] var1, int[] var2, int[] var3, int[] var4, EvalType var5);

    DensityFunction c2me$getDelegate();

    DensityFunction c2me$withDelegate(DensityFunction var1);
}
