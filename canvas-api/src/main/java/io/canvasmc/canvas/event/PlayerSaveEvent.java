package io.canvasmc.canvas.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerSaveEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final boolean isQuit;

    public PlayerSaveEvent(Player player, boolean isQuit) {
        super(player);
        this.isQuit = isQuit;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * If true, the player is being disconnected from the server,
     * otherwise we are just running autosave
     *
     * @return if this is due to disconnect or autosave
     */
    public boolean isQuit() {
        return isQuit;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }
}
