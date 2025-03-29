package io.canvasmc.canvas.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SleepingBlockEntity {
    TickingBlockEntity SLEEPING_BLOCK_ENTITY_TICKER = new TickingBlockEntity() {
        public void tick() {
        }

        public boolean isRemoved() {
            return false;
        }

        public @Nullable BlockPos getPos() {
            return null;
        }

        public @NotNull String getType() {
            return "<lithium_sleeping>";
        }
        // Canvas start - Threaded Regions

        @Contract(pure = true)
        @Override
        public @Nullable BlockEntity getTileEntity() {
            return null;
        }
        // Canvas end
    };

    LevelChunk.RebindableTickingBlockEntityWrapper lithium$getTickWrapper();

    void lithium$setTickWrapper(LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper);

    TickingBlockEntity lithium$getSleepingTicker();

    void lithium$setSleepingTicker(TickingBlockEntity sleepingTicker);

    default boolean lithium$startSleeping() {
        if (this.isSleeping()) {
            return false;
        }

        LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
        if (tickWrapper == null) {
            return false;
        }
        this.lithium$setSleepingTicker(tickWrapper.ticker);
        tickWrapper.rebind(SleepingBlockEntity.SLEEPING_BLOCK_ENTITY_TICKER);
        return true;
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
}
