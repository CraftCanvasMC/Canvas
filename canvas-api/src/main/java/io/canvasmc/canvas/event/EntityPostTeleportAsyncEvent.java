package io.canvasmc.canvas.event;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/**
 * Called when an entity is finished being teleported from one location to another.
 * <br>
 * This may be as a result of natural causes (Enderman, Shulker), pathfinding (Wolf), or commands (/teleport).
 * <br>
 * This <b>*can*</b> be a player!
 */
public class EntityPostTeleportAsyncEvent extends EntityEvent {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Location from;
    private final Location to;
    private final PlayerTeleportEvent.TeleportCause cause;

    @ApiStatus.Internal
    public EntityPostTeleportAsyncEvent(Entity entity, Location from, @Nullable Location to, PlayerTeleportEvent.TeleportCause cause) {
        super(entity);
        this.from = from;
        this.to = (to != null) ? to : from;
        this.cause = cause;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    /**
     * Gets the location that this entity moved from
     *
     * @return Location this entity moved from
     */
    public Location getFrom() {
        return this.from;
    }

    /**
     * Gets the location that this entity moved to
     *
     * @return Location the entity moved to
     */
    public Location getTo() {
        return this.to;
    }

    /**
     * Gets the cause of the entity teleport
     *
     * @return the teleport cause
     */
    public PlayerTeleportEvent.TeleportCause getCause() {
        return cause;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }
}
