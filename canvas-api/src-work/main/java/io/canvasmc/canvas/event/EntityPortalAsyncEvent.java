package io.canvasmc.canvas.event;

import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Called when an entity is about to portal from one dimension to another.
 * <br>
 * This <b>*can*</b> be a player!
 */
public class EntityPortalAsyncEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final World from;
    private final PortalType type;
    private World to;
    private boolean cancelled;

    @ApiStatus.Internal
    public EntityPortalAsyncEvent(Entity entity, World from, World to, PortalType type) {
        super(entity);
        this.from = from;
        this.to = to;
        this.type = type;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    /**
     * Gets the location that this entity moved from
     *
     * @return Location this entity moved from
     */
    public World getFrom() {
        return this.from;
    }

    /**
     * Gets the location that this entity moved to
     *
     * @return Location the entity moved to
     */
    public World getTo() {
        return this.to;
    }

    /**
     * Sets the location that this entity will move to
     *
     * @param to
     *     the location that this entity will move to
     */
    public void setTo(World to) {
        this.to = to;
    }

    /**
     * Get the portal type relating to this event.
     *
     * @return the portal type
     */
    public PortalType getPortalType() {
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

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }
}
