package io.canvasmc.canvas.event;

import com.google.common.base.Preconditions;
import io.papermc.paper.event.player.AbstractRespawnEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Called when a player is being respawned, while the player is currently unloaded from the world, but is about to be
 * placed in the world.
 * <p>
 * It is <b>NOT safe</b> to modify the entity state during this event
 * <p>
 * <b>WARNING:</b> This is called in an unknown region state, one that does not own the player in question. It is not
 * safe to ever modify the player state during this event. If you wish to do so, use
 * {@link PlayerPostRespawnAsyncEvent}. Modifying the player state will cause crashes and undiagnosable issues, leaving
 * the server in a corrupt state if done when this event is called.
 *
 * @apiNote It is generally recommended to use {@link io.canvasmc.canvas.event.PlayerPostRespawnAsyncEvent} in favor
 *     over this event, as this event is called between states of the player, meaning the player isn't owned by any
 *     region and is in a fragile state, where if exceptions were thrown, would cause major issues.
 */
public class PlayerRespawnAsyncEvent extends AbstractRespawnEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    @ApiStatus.Internal
    public PlayerRespawnAsyncEvent(final Player respawnPlayer, final Location respawnLocation, final boolean isBedSpawn, final boolean isAnchorSpawn, final boolean missingRespawnBlock, final PlayerRespawnEvent.RespawnReason respawnReason) {
        super(respawnPlayer, respawnLocation, isBedSpawn, isAnchorSpawn, missingRespawnBlock, respawnReason);
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Sets the new respawn location.
     *
     * @param respawnLocation
     *     new location for the respawn
     */
    public void setRespawnLocation(@NotNull Location respawnLocation) {
        Preconditions.checkArgument(respawnLocation != null, "Respawn location can not be null");
        Preconditions.checkArgument(respawnLocation.getWorld() != null, "Respawn world can not be null");

        this.respawnLocation = respawnLocation.clone();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
