package io.canvasmc.canvas.world.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Derived from the Lithium sleeping block entity optimization
 */
public interface SleepingBlockEntity {

    LevelChunk.@Nullable RebindableTickingBlockEntityWrapper lithium$getTickWrapper();

    void lithium$setTickWrapper(final LevelChunk.@Nullable RebindableTickingBlockEntityWrapper tickWrapper);

    @Nullable
    TickingBlockEntity lithium$getSleepingTicker();

    void lithium$setSleepingTicker(final @Nullable TickingBlockEntity sleepingTicker);

    default void onChange() {}

    default void lithium$startSleeping() {
        if (this.isSleeping()) {
            return;
        }

        final LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
        if (tickWrapper == null) {
            return;
        }

        this.lithium$setSleepingTicker(tickWrapper.ticker);
        tickWrapper.rebind(new SleepingTicker(this));
    }

    default void wakeUpNow() {
        final TickingBlockEntity sleepingTicker = this.lithium$getSleepingTicker();
        if (sleepingTicker == null) {
            return;
        }
        this.setTicker(sleepingTicker);
        this.lithium$setSleepingTicker(null);
    }

    default void setTicker(final TickingBlockEntity delegate) {
        final LevelChunk.RebindableTickingBlockEntityWrapper tickWrapper = this.lithium$getTickWrapper();
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

        public SleepingTicker(final SleepingBlockEntity sleepingBlockEntity) {
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
