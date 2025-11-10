package io.canvasmc.canvas.event;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when an entity is about to be teleported from one location to another.
 * <br>
 * This may be as a result of natural causes (Enderman, Shulker), pathfinding
 * (Wolf), or commands (/teleport).
 * <br>
 * This <b>*can*</b> be a player!
 */
public class EntityTeleportAsyncEvent extends EntityEvent implements Cancellable {

    private static final HandlerList HANDLER_LIST = new HandlerList();

    private final Location from;
    @NotNull
    private final PlayerTeleportEvent.TeleportCause cause;
    private Location to;
    private boolean cancelled;

    @ApiStatus.Internal
    public EntityTeleportAsyncEvent(@NotNull Entity entity, @NotNull Location from, @Nullable Location to, @NotNull PlayerTeleportEvent.TeleportCause cause) {
        super(entity);
        this.from = from;
        this.to = to;
        this.cause = cause;
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
    public Location getFrom() {
        return this.from;
    }

    /**
     * Gets the location that this entity moved to
     *
     * @return Location the entity moved to
     */
    @NotNull
    public Location getTo() {
        return this.to;
    }

    /**
     * Sets the location that this entity moved to
     *
     * @param to New Location this entity moved to
     */
    public void setTo(@NotNull Location to) {
        this.to = to.clone();
    }

    /**
     * Gets the cause of the entity teleport
     *
     * @return the teleport cause
     */
    public @NotNull PlayerTeleportEvent.TeleportCause getCause() {
        return cause;
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
