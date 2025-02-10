package io.canvasmc.canvas.dfc.common.vif;

import io.canvasmc.canvas.dfc.common.ast.AstNode;
import io.canvasmc.canvas.dfc.common.ast.EvalType;
import io.canvasmc.canvas.dfc.common.ast.misc.CacheLikeNode;
import io.canvasmc.canvas.dfc.common.ast.misc.DelegateNode;
import io.canvasmc.canvas.dfc.common.ducks.IFastCacheLike;
import java.util.Objects;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.blending.Blender;

public class AstVanillaInterface implements DensityFunction {
    private final AstNode astNode;
    private final DensityFunction blendingFallback;

    public AstVanillaInterface(AstNode astNode, DensityFunction blendingFallback) {
        this.astNode = (AstNode)Objects.requireNonNull(astNode);
        this.blendingFallback = blendingFallback;
    }

    public double compute(FunctionContext pos) {
        if (pos.getBlender() != Blender.empty()) {
            if (this.blendingFallback == null) {
                throw new IllegalStateException("blendingFallback is no more");
            } else {
                return this.blendingFallback.compute(pos);
            }
        } else {
            return this.astNode.evalSingle(pos.blockX(), pos.blockY(), pos.blockZ(), EvalType.from(pos));
        }
    }

    public void fillArray(double[] densities, ContextProvider applier) {
        if (applier instanceof NoiseChunk sampler) {
            if (sampler.getBlender() != Blender.empty()) {
                if (this.blendingFallback == null) {
                    throw new IllegalStateException("blendingFallback is no more");
                }

                this.blendingFallback.fillArray(densities, applier);
                return;
            }
        }

        if (applier instanceof EachApplierVanillaInterface vanillaInterface) {
            this.astNode.evalMulti(densities, vanillaInterface.getX(), vanillaInterface.getY(), vanillaInterface.getZ(), EvalType.from(applier));
        } else {
            int[] x = new int[densities.length];
            int[] y = new int[densities.length];
            int[] z = new int[densities.length];

            for(int i = 0; i < densities.length; ++i) {
                FunctionContext pos = applier.forIndex(i);
                x[i] = pos.blockX();
                y[i] = pos.blockY();
                z[i] = pos.blockZ();
            }

            this.astNode.evalMulti(densities, x, y, z, EvalType.from(applier));
        }
    }

    public DensityFunction mapAll(Visitor visitor) {
        AstNode transformed = this.astNode.transform((astNode) -> {
            if (astNode instanceof DelegateNode delegateNode) {
                return new DelegateNode(delegateNode.getDelegate().mapAll(visitor));
            } else if (astNode instanceof CacheLikeNode cacheLikeNode) {
                return new CacheLikeNode((IFastCacheLike)cacheLikeNode.getCacheLike().mapAll(visitor), cacheLikeNode.getDelegate());
            } else {
                return astNode;
            }
        });
        DensityFunction blendingFallback1 = this.blendingFallback != null ? this.blendingFallback.mapAll(visitor) : null;
        return transformed == this.astNode && blendingFallback1 == this.blendingFallback ? this : new AstVanillaInterface(transformed, blendingFallback1);
    }

    public double minValue() {
        return this.blendingFallback.minValue();
    }

    public double maxValue() {
        return this.blendingFallback.maxValue();
    }

    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        throw new UnsupportedOperationException();
    }

    public AstNode getAstNode() {
        return this.astNode;
    }

    public DensityFunction getBlendingFallback() {
        return this.blendingFallback;
    }
}
