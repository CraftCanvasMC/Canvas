package io.canvasmc.canvas.dfc.common;

import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseRouterData;

public interface IDensityFunctionsCaveScaler {

    public static double invokeScaleCaves(double value) {
        return NoiseRouterData.QuantizedSpaghettiRarity.getSphaghettiRarity2D(value);
    }

    public static double invokeScaleTunnels(double value) {
        return NoiseRouterData.QuantizedSpaghettiRarity.getSpaghettiRarity3D(value);
    }

}
