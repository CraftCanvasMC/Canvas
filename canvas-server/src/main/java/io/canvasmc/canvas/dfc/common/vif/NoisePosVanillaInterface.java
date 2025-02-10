package io.canvasmc.canvas.dfc.common.vif;

import io.canvasmc.canvas.dfc.common.ast.EvalType;
import java.util.Objects;
import net.minecraft.world.level.levelgen.DensityFunction;

public class NoisePosVanillaInterface implements DensityFunction.FunctionContext {
    private final int x;
    private final int y;
    private final int z;
    private final EvalType type;

    public NoisePosVanillaInterface(int x, int y, int z, EvalType type) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = (EvalType)Objects.requireNonNull(type);
    }

    public int blockX() {
        return this.x;
    }

    public int blockY() {
        return this.y;
    }

    public int blockZ() {
        return this.z;
    }

    public EvalType getType() {
        return this.type;
    }
}
