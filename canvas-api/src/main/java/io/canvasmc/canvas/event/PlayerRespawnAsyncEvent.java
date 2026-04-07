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
 * <p>
 * <b>WARNING:</b> This is called in the region of the initial target respawn location. This <b>can</b> be untrue if
 * the respawn location changes in the event, so it isn't recommended to use
 * {@link PlayerRespawnAsyncEvent#getRespawnLocation()} to find the location for the region context. As such, it isn't
 * recommended to access or modify any region state by using {@link PlayerRespawnAsyncEvent#getRespawnLocation()}, as
 * the respawn location can be modified by a different plugin. By attempting to do this, it can cause numerous issues
 * like crashes and undiagnosable and unrecoverable exceptions.
 *
 * @apiNote It is not recommended to modify the entity state or the region state during this event, as results may
 *     cause issues or be unpredictable. If you need to run actions on the player, schedule to the player's entity
 *     scheduler.
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
