package io.canvasmc.canvas.dfc.common.ast;

import io.canvasmc.canvas.dfc.common.vif.EachApplierVanillaInterface;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;

public enum EvalType {
    NORMAL,
    INTERPOLATION;

    private EvalType() {
    }

    public static EvalType from(DensityFunction.FunctionContext pos) {
        return pos instanceof NoiseChunk ? INTERPOLATION : NORMAL;
    }

    public static EvalType from(DensityFunction.ContextProvider applier) {
        if (applier instanceof EachApplierVanillaInterface vif) {
            return vif.getType();
        } else {
            return applier instanceof NoiseChunk ? INTERPOLATION : NORMAL;
        }
    }
}
