package io.canvasmc.canvas.dfc.common.ast.dfvisitor;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.jetbrains.annotations.NotNull;

public class StripBlending implements DensityFunction.Visitor {
    public static final StripBlending INSTANCE = new StripBlending();

    private StripBlending() {
    }

    public @NotNull DensityFunction apply(@NotNull DensityFunction densityFunction) {
        return switch (densityFunction) {
            case NoiseChunk.BlendAlpha _ -> DensityFunctions.constant(1.0);
            case NoiseChunk.BlendOffset _ -> DensityFunctions.constant(0.0);
            case DensityFunctions.BlendAlpha _ -> DensityFunctions.constant(1.0);
            case DensityFunctions.BlendOffset _ -> DensityFunctions.constant(0.0);
            default -> densityFunction;
        };
    }
}
