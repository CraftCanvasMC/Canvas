package io.canvasmc.canvas.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class FlowableFluidUtils {

    public static boolean needsPostProcessing(LevelReader world, BlockPos pos, BlockState blockState, FluidState fluidState) {
        if (!fluidState.isSource()) {
            return true;
        }
        return canFlowNormally(world, pos, blockState, fluidState);
    }

    private static boolean canFlowNormally(LevelReader world, BlockPos pos, BlockState blockState, FluidState fluidState) {
        if (fluidState.isEmpty()) return false;

        BlockPos belowPos = pos.below();
        BlockState belowBlockState = world.getBlockState(belowPos);
        FluidState belowFluidState = belowBlockState.getFluidState();
        // very rough filtering
        if (((FlowingFluid) fluidState.getType()).canMaybePassThrough(world, pos, blockState, Direction.DOWN, belowPos, belowBlockState, belowFluidState)) {
            FluidState fluidState3 = getUpdatedState(((FlowingFluid) fluidState.getType()), world, belowPos, belowBlockState);
            if (fluidState3 == null) {
                return true; // shortcut
            }
            Fluid fluid = fluidState3.getType();
            if (belowFluidState.canBeReplacedWith(world, belowPos, fluid, Direction.DOWN) && FlowingFluid.canHoldSpecificFluid(world, belowPos, belowBlockState, fluid)) {
                return true;
            }
        }
        return (fluidState.isSource() || !(((FlowingFluid) fluidState.getType()).isWaterHole(world, pos, blockState, belowPos, belowBlockState))) &&
            canSpreadToSidesNormally(world, pos, blockState, fluidState);
    }

    private static boolean canSpreadToSidesNormally(LevelReader world, BlockPos pos, BlockState blockState, FluidState fluidState) {
        int nextFluidLevel = fluidState.getAmount() - ((FlowingFluid) fluidState.getType()).getDropOff(world);
        if (fluidState.getValue(FlowingFluid.FALLING)) {
            nextFluidLevel = 7;
        }
        if (nextFluidLevel > 0) {
            // getSpread
//            int i = 1000;
//            Map<Direction, FluidState> map = Maps.newEnumMap(Direction.class);
//            SpreadCache spreadCache = null;

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos offsetPos = pos.relative(direction);
                BlockState offsetBlockState = world.getBlockState(offsetPos);
                FluidState offsetFluidState = offsetBlockState.getFluidState();
                if (((FlowingFluid) fluidState.getType()).canMaybePassThrough(world, pos, blockState, direction, offsetPos, offsetBlockState, offsetFluidState)) {
                    FluidState fluidState2 = getUpdatedState((FlowingFluid) fluidState.getType(), world, offsetPos, offsetBlockState);
                    if (fluidState2 == null) {
                        return true; // shortcut
                    }
                    if (FlowingFluid.canHoldSpecificFluid(world, offsetPos, offsetBlockState, fluidState2.getType())) {
                        return true; // shortcut
                    }
                }
            }
        }

        return false;
    }

    private static FluidState getUpdatedState(FlowingFluid receiver, LevelReader world, BlockPos pos, BlockState state) {
        int i = 0;
        int j = 0;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockPos = mutable.setWithOffset(pos, direction);
            BlockState blockState = world.getBlockState(blockPos);
            FluidState fluidState = blockState.getFluidState();
            if (fluidState.getType().isSame(receiver) && FlowingFluid.canPassThroughWall(direction, world, pos, state, blockPos, blockState)) {
                if (fluidState.isSource()) {
                    j++;
                }

                i = Math.max(i, fluidState.getAmount());
            }
        }

//        if (j >= 2 && this.isInfinite(world)) {
//            BlockState blockState2 = world.getBlockState(mutable.set(pos, Direction.DOWN));
//            FluidState fluidState2 = blockState2.getFluidState();
//            if (blockState2.isSolid() || receiver.isMatchingAndStill(fluidState2)) {
//                return receiver.getStill(false);
//            }
//        }
        if (j >= 2) {
            return null; // to not filter this
        }

        BlockPos blockPos2 = mutable.setWithOffset(pos, Direction.UP);
        BlockState blockState3 = world.getBlockState(blockPos2);
        FluidState fluidState3 = blockState3.getFluidState();
        if (!fluidState3.isEmpty() && fluidState3.getType().isSame(receiver) && FlowingFluid.canPassThroughWall(Direction.UP, world, pos, state, blockPos2, blockState3)) {
            return receiver.getFlowing(8, true);
        } else {
            int k = i - receiver.getDropOff(world);
            return k <= 0 ? Fluids.EMPTY.defaultFluidState() : receiver.getFlowing(k, false);
        }
    }

}
