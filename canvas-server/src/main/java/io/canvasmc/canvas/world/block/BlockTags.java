package io.canvasmc.canvas.world.block;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

public final class BlockTags {
    public static final int RANDOMLY_TICKING = 0x01; // TODO - should we use this ever
    public static final int WATER = 0x02;
    public static final int LAVA = 0x04;
    public static final int CAN_HOLD_ANY_FLUID = 0x08;
    public static final int INTERACTS_WITH_PRECIPITATION = 0x10;

    public static final int FLUID = WATER | LAVA;

    public static int init(final BlockState state) {
        int i = 0;
        i |= state.getFluidState().is(FluidTags.WATER) ? WATER : 0;
        i |= state.getFluidState().is(FluidTags.LAVA) ? LAVA : 0;
        i |= FlowingFluid.canHoldAnyFluid(state) ? CAN_HOLD_ANY_FLUID : 0;
        i |= state.isRandomlyTicking() ? RANDOMLY_TICKING : 0;
        i |= interactsWithPrecipitation(state.getBlock()) ? INTERACTS_WITH_PRECIPITATION : 0;
        return i;
    }

    private static boolean interactsWithPrecipitation(final Block block) {
        return block instanceof CauldronBlock ||
            block instanceof LayeredCauldronBlock ||
            block instanceof LiquidBlock;
    }
}
