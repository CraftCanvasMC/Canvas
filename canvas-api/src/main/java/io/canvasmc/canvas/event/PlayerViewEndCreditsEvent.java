package io.canvasmc.canvas.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.ApiStatus;

/**
 * Called when the player enters the exit portal, allows controlling if the end credits will show or not
 */
public class PlayerViewEndCreditsEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean willShowCredits;
    private Result result = Result.DEFAULT;

    @ApiStatus.Internal
    public PlayerViewEndCreditsEvent(final Player player, boolean willShowCredits) {
        super(player);
        this.willShowCredits = willShowCredits;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /**
     * Gets the modified result based on if the event forces the credits to display or not display, or let Vanilla
     * handle it
     *
     * @return the modified result
     */
    public boolean willShowCredits() {
        return result == Result.DEFAULT ? willShowCredits : result == Result.ALLOW;
    }

    /**
     * Sets whether the end credits should display.
     * <p>
     * <ul>
     *     <li>If {@link org.bukkit.event.Event.Result#DEFAULT}, it will display the credits to the player based on {@link PlayerViewEndCreditsEvent#willVanillaShowCredits()}, meaning Vanilla will handle the credits display</li>
     *     <li>If {@link org.bukkit.event.Event.Result#ALLOW}, it will force display the credits to the player</li>
     *     <li>If {@link org.bukkit.event.Event.Result#DENY}, the player will not display the credits</li>
     * </ul>
     *
     * @param result
     *     the event result
     */
    public void setResult(final Result result) {
        this.result = result;
    }

    /**
     * Gets whether Vanilla would display the end credits or not
     *
     * @return the Vanilla result
     */
    public boolean willVanillaShowCredits() {
        return willShowCredits;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
