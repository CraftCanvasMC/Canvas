package io.canvasmc.canvas.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import org.jetbrains.annotations.NotNull;

public record SleepUntilTimeBlockEntityTickInvoker(BlockEntity sleepingBlockEntity, long sleepUntilTickExclusive,
                                                   TickingBlockEntity delegate) implements TickingBlockEntity {

    @Override
    public void tick() {
        //noinspection ConstantConditions
        long tickTime = this.sleepingBlockEntity.getLevel().getGameTime();
        if (tickTime >= this.sleepUntilTickExclusive) {
            ((SleepingBlockEntity) this.sleepingBlockEntity).setTicker(this.delegate);
            this.delegate.tick();
        }
    }

    @Override
    public boolean isRemoved() {
        return this.sleepingBlockEntity.isRemoved();
    }

    @Override
    public @NotNull BlockPos getPos() {
        return this.sleepingBlockEntity.getBlockPos();
    }

    @Override
    public @NotNull String getType() {
        //noinspection ConstantConditions
        return BlockEntityType.getKey(this.sleepingBlockEntity.getType()).toString();
    }
    // Canvas start - Threaded Regions

    @Override
    public @NotNull BlockEntity getTileEntity() {
        return this.sleepingBlockEntity;
    }
    // Canvas end
}
