package io.canvasmc.canvas.event;

import io.papermc.paper.event.player.AbstractRespawnEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Called when a player is being respawned, while the player is currently unloaded from the world, but is about to be
 * placed in the world.
 * <p>
 * It is <b>NOT safe</b> to modify the entity state during this event
 *
 * @apiNote This is called in the region of the original respawn location
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

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
