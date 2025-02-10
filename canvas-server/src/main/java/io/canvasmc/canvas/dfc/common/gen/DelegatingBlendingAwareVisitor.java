package io.canvasmc.canvas.dfc.common.gen;

import io.canvasmc.canvas.dfc.common.ducks.IBlendingAwareVisitor;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.jetbrains.annotations.NotNull;

public class DelegatingBlendingAwareVisitor implements IBlendingAwareVisitor, DensityFunction.Visitor {
    private final DensityFunction.Visitor delegate;
    private final boolean blendingEnabled;

    public DelegatingBlendingAwareVisitor(DensityFunction.Visitor delegate, boolean blendingEnabled) {
        this.delegate = Objects.requireNonNull(delegate);
        this.blendingEnabled = blendingEnabled;
    }

    public @NotNull DensityFunction apply(@NotNull DensityFunction densityFunction) {
        return this.delegate.apply(densityFunction);
    }

    public DensityFunction.@NotNull NoiseHolder visitNoise(DensityFunction.@NotNull NoiseHolder noiseDensityFunction) {
        return this.delegate.visitNoise(noiseDensityFunction);
    }

    public boolean c2me$isBlendingEnabled() {
        return this.blendingEnabled;
    }
}
