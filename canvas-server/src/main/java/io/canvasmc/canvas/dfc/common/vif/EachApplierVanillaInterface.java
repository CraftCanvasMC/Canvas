package io.canvasmc.canvas.dfc.common.vif;

import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.ducks.IArrayCacheCapable;
import io.canvasmc.canvas.dfc.common.util.ArrayCache;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;

public class EachApplierVanillaInterface implements DensityFunction.ContextProvider, IArrayCacheCapable {
    private final int[] x;
    private final int[] y;
    private final int[] z;
    private final EvalType type;
    private final ArrayCache cache;

    public EachApplierVanillaInterface(int[] x, int[] y, int[] z, EvalType type) {
        this(x, y, z, type, new ArrayCache());
    }

    public EachApplierVanillaInterface(int[] x, int[] y, int[] z, EvalType type, ArrayCache cache) {
        this.x = (int[])Objects.requireNonNull(x);
        this.y = (int[])Objects.requireNonNull(y);
        this.z = (int[])Objects.requireNonNull(z);
        this.type = (EvalType)Objects.requireNonNull(type);
        this.cache = (ArrayCache)Objects.requireNonNull(cache);
    }

    public DensityFunction.FunctionContext forIndex(int index) {
        return new NoisePosVanillaInterface(this.x[index], this.y[index], this.z[index], this.type);
    }

    public void fillAllDirectly(double[] densities, DensityFunction densityFunction) {
        for(int i = 0; i < this.x.length; ++i) {
            densities[i] = densityFunction.compute(this.forIndex(i));
        }

    }

    public int[] getX() {
        return this.x;
    }

    public int[] getY() {
        return this.y;
    }

    public int[] getZ() {
        return this.z;
    }

    public EvalType getType() {
        return this.type;
    }

    public ArrayCache c2me$getArrayCache() {
        return this.cache;
    }
}
