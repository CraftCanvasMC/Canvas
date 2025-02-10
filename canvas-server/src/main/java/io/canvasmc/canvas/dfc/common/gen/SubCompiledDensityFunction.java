package io.canvasmc.canvas.dfc.common.gen;

import com.google.common.base.Suppliers;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.ducks.IArrayCacheCapable;
import io.canvasmc.canvas.dfc.common.ducks.IBlendingAwareVisitor;
import io.canvasmc.canvas.dfc.common.ducks.ICoordinatesFilling;
import io.canvasmc.canvas.dfc.common.util.ArrayCache;
import io.canvasmc.canvas.dfc.common.vif.EachApplierVanillaInterface;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubCompiledDensityFunction implements DensityFunction {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubCompiledDensityFunction.class);
    private final ISingleMethod singleMethod;
    private final IMultiMethod multiMethod;
    protected final Supplier<DensityFunction> blendingFallback;

    public SubCompiledDensityFunction(ISingleMethod singleMethod, IMultiMethod multiMethod, DensityFunction blendingFallback) {
        this(singleMethod, multiMethod, unwrap(blendingFallback));
    }

    protected SubCompiledDensityFunction(ISingleMethod singleMethod, IMultiMethod multiMethod, Supplier<DensityFunction> blendingFallback) {
        this.singleMethod = (ISingleMethod)Objects.requireNonNull(singleMethod);
        this.multiMethod = (IMultiMethod)Objects.requireNonNull(multiMethod);
        this.blendingFallback = blendingFallback;
    }

    private static Supplier<DensityFunction> unwrap(DensityFunction densityFunction) {
        if (densityFunction instanceof SubCompiledDensityFunction scdf) {
            return scdf.blendingFallback;
        } else {
            return densityFunction != null ? Suppliers.ofInstance(densityFunction) : null;
        }
    }

    public double compute(FunctionContext pos) {
        if (pos.getBlender() != Blender.empty()) {
            DensityFunction fallback = this.getFallback();
            if (fallback == null) {
                throw new IllegalStateException("blendingFallback is no more");
            } else {
                return fallback.compute(pos);
            }
        } else {
            return this.singleMethod.evalSingle(pos.blockX(), pos.blockY(), pos.blockZ(), EvalType.from(pos));
        }
    }

    public void fillArray(double[] densities, ContextProvider applier) {
        if (applier instanceof NoiseChunk sampler) {
            if (sampler.getBlender() != Blender.empty()) {
                DensityFunction fallback = this.getFallback();
                if (fallback == null) {
                    throw new IllegalStateException("blendingFallback is no more");
                }

                fallback.fillArray(densities, applier);
                return;
            }
        }

        if (applier instanceof EachApplierVanillaInterface vanillaInterface) {
            this.multiMethod.evalMulti(densities, vanillaInterface.getX(), vanillaInterface.getY(), vanillaInterface.getZ(), EvalType.from(applier), vanillaInterface.c2me$getArrayCache());
        } else {
            ArrayCache var10000;
            if (applier instanceof IArrayCacheCapable cacheCapable) {
                var10000 = cacheCapable.c2me$getArrayCache();
            } else {
                var10000 = new ArrayCache();
            }

            ArrayCache cache = var10000;
            int[] x = cache.getIntArray(densities.length, false);
            int[] y = cache.getIntArray(densities.length, false);
            int[] z = cache.getIntArray(densities.length, false);
            if (applier instanceof ICoordinatesFilling coordinatesFilling) {
                coordinatesFilling.c2me$fillCoordinates(x, y, z);
            } else {
                for(int i = 0; i < densities.length; ++i) {
                    FunctionContext pos = applier.forIndex(i);
                    x[i] = pos.blockX();
                    y[i] = pos.blockY();
                    z[i] = pos.blockZ();
                }
            }

            this.multiMethod.evalMulti(densities, x, y, z, EvalType.from(applier), cache);
        }
    }

    public DensityFunction mapAll(Visitor visitor) {
        if (this.getClass() != SubCompiledDensityFunction.class) {
            throw new AbstractMethodError();
        } else {
            if (visitor instanceof IBlendingAwareVisitor) {
                IBlendingAwareVisitor blendingAwareVisitor = (IBlendingAwareVisitor)visitor;
                if (blendingAwareVisitor.c2me$isBlendingEnabled()) {
                    DensityFunction fallback1 = this.getFallback();
                    if (fallback1 == null) {
                        throw new IllegalStateException("blendingFallback is no more");
                    }

                    return fallback1.mapAll(visitor);
                }
            }

            boolean modified = false;
            Supplier<DensityFunction> fallback = this.blendingFallback != null ? Suppliers.memoize(() -> {
                DensityFunction densityFunction = (DensityFunction)this.blendingFallback.get();
                return densityFunction != null ? densityFunction.mapAll(visitor) : null;
            }) : null;
            if (fallback != this.blendingFallback) {
                modified = true;
            }

            return modified ? new SubCompiledDensityFunction(this.singleMethod, this.multiMethod, fallback) : this;
        }
    }

    public double minValue() {
        return Double.MIN_VALUE;
    }

    public double maxValue() {
        return Double.MAX_VALUE;
    }

    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        throw new UnsupportedOperationException();
    }

    protected DensityFunction getFallback() {
        return this.blendingFallback != null ? (DensityFunction)this.blendingFallback.get() : null;
    }
}
