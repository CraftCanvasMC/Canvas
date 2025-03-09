package io.canvasmc.canvas.event;

import org.bukkit.block.data.BlockData;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Represents an event that is triggered when a block's shape is updated.
 * This event can be canceled to prevent the original shape update handling from occurring.
 * <br><br>
 * If canceled, the resulting blockstate will the return value of {@link ShapeUpdateEvent#getBlock()}.
 * If not canceled, the return value of {@link ShapeUpdateEvent#getBlock()} will be passed into the original
 * block shape handling.
 */
public class ShapeUpdateEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private BlockData theBlock;
    private boolean canceled = false;

    public ShapeUpdateEvent(final @NotNull BlockData theBlock) {
        this.theBlock = theBlock;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return canceled;
    }

    @Override
    public void setCancelled(final boolean cancel) {
        this.canceled = cancel;
    }

    /**
     * The block state of the block being updated
     * Mutable with {@link ShapeUpdateEvent#setBlock(BlockData)}
     * <br><br>
     * Defines the end result of the block state if canceled. If not
     * canceled, the original shape handling will mutate the return
     * value of this method
     */
    public @NotNull BlockData getBlock() {
        return theBlock;
    }

    /**
     * Sets the {@link BlockData} for the shape update
     */
    public void setBlock(BlockData data) {
        this.theBlock = data;
    }
}
