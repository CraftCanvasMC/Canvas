package io.canvasmc.canvas.world.block;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.NotNull;

public final class BlockTags {
    public static final int RANDOMLY_TICKING = 0x01; // TODO - randomtick opts
    public static final int WATER = 0x02;
    public static final int LAVA = 0x04;
    public static final int CAN_HOLD_ANY_FLUID = 0x08;

    public static final int FLUID = WATER | LAVA;

    public static int init(final @NotNull BlockState state) {
        int i = 0;
        i |= state.getFluidState().is(FluidTags.WATER) ? WATER : 0;
        i |= state.getFluidState().is(FluidTags.LAVA) ? LAVA : 0;
        i |= FlowingFluid.canHoldAnyFluid(state) ? CAN_HOLD_ANY_FLUID : 0;
        i |= state.isRandomlyTicking() ? RANDOMLY_TICKING : 0;
        return i;
    }
}
