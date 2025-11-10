package io.canvasmc.canvas.chunk.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

public interface SleepingBlockEntity {

    LevelChunk.RebindableTickingBlockEntityWrapper lithium$getTickWrapper();

    void lithium$setTickWrapper(LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper);

    TickingBlockEntity lithium$getSleepingTicker();

    void lithium$setSleepingTicker(TickingBlockEntity sleepingTicker);

    default void onChange() {}

    default void lithium$startSleeping() {
        if (this.isSleeping()) {
            return;
        }

        LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
        if (tickWrapper == null) {
            return;
        }
        this.lithium$setSleepingTicker(tickWrapper.ticker);
        tickWrapper.rebind(new SleepingTicker(this));
    }

    default void sleepOnlyCurrentTick() {
        TickingBlockEntity sleepingTicker = this.lithium$getSleepingTicker();
        LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
        if (sleepingTicker == null) {
            sleepingTicker = tickWrapper.ticker;
        }
        Level world = ((BlockEntity) this).getLevel();
        tickWrapper.rebind(new SleepUntilTimeBlockEntityTickInvoker((BlockEntity) this, world.getGameTime() + 1, sleepingTicker));
        this.lithium$setSleepingTicker(null);
    }

    default void sleepFor(int ticks) {
        TickingBlockEntity sleepingTicker = this.lithium$getSleepingTicker();
        LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
        if (sleepingTicker == null) {
            sleepingTicker = tickWrapper.ticker;
        }
        Level world = ((BlockEntity) this).getLevel();
        tickWrapper.rebind(new SleepUntilTimeBlockEntityTickInvoker((BlockEntity) this, world.getGameTime() + ticks, sleepingTicker));
        this.lithium$setSleepingTicker(null);
    }

    default void wakeUpNow() {
        TickingBlockEntity sleepingTicker = this.lithium$getSleepingTicker();
        if (sleepingTicker == null) {
            return;
        }
        this.setTicker(sleepingTicker);
        this.lithium$setSleepingTicker(null);
    }

    default void setTicker(TickingBlockEntity delegate) {
        LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
        if (tickWrapper == null) {
            return;
        }
        tickWrapper.rebind(delegate);
    }

    default boolean isSleeping() {
        return this.lithium$getSleepingTicker() != null;
    }

    class SleepingTicker implements TickingBlockEntity {
        private final SleepingBlockEntity sleepingBlockEntity;

        public SleepingTicker(SleepingBlockEntity sleepingBlockEntity) {
            this.sleepingBlockEntity = sleepingBlockEntity;
        }

        public BlockPos getPosForRegionOperation() {
            return ((BlockEntity) this.sleepingBlockEntity).getBlockPos();
        }

        public BlockEntity getTileEntityForRegionOperation() {
            return ((BlockEntity) this.sleepingBlockEntity);
        }

        public void tick() {
        }

        public boolean isRemoved() {
            return false;
        }

        public BlockPos getPos() {
            return null;
        }

        public String getType() {
            return "<lithium_sleeping>";
        }

        @Override
        public BlockEntity getTileEntity() {
            return null;
        }
    }
}
