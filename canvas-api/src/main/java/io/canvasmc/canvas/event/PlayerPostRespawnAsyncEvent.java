package io.canvasmc.canvas.event;

import io.papermc.paper.event.player.AbstractRespawnEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Fired after respawning the player, this is guaranteed to be in the correct region context of the player related to
 * the event, and fired on the correct region context. Modifying the respawn location is not allowed. If you wish to
 * modify the location of the respawn from here, schedule a teleport to the player scheduler.
 */
public class PlayerPostRespawnAsyncEvent extends AbstractRespawnEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    public PlayerPostRespawnAsyncEvent(final Player respawnPlayer, final Location respawnLocation, final boolean isBedSpawn, final boolean isAnchorSpawn, final boolean missingRespawnBlock, final PlayerRespawnEvent.RespawnReason respawnReason) {
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
