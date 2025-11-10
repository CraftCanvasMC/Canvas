package io.canvasmc.canvas.event;

import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when an entity is about to portal from one dimension to another.
 * <br>
 * This <b>*can*</b> be a player!
 */
public class EntityPortalAsyncEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final World from;
    private final World to;
    private final PortalType type;

    private boolean cancelled;

    @ApiStatus.Internal
    public EntityPortalAsyncEvent(@NotNull Entity entity, @NotNull World from, @NotNull World to, @NotNull PortalType type) {
        super(entity);
        this.from = from;
        this.to = to;
        this.type = type;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    /**
     * Gets the location that this entity moved from
     *
     * @return Location this entity moved from
     */
    @NotNull
    public World getFrom() {
        return this.from;
    }

    /**
     * Gets the location that this entity moved to
     *
     * @return Location the entity moved to
     */
    @NotNull
    public World getTo() {
        return this.to;
    }

    /**
     * Get the portal type relating to this event.
     *
     * @return the portal type
     */
    public @NotNull PortalType getPortalType() {
        return this.type;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }
}
