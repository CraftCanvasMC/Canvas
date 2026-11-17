package io.canvasmc.canvas.entity;

import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * API for the per-world cap on how many other players each player is shown, {@code players.max-visible} in the
 * Canvas world configuration. A capped out player is not tracked by the viewer.
 */
public final class PlayerVisibilityCap {

    /**
     * A viewer with this permission is not capped.
     */
    public static final String BYPASS_PERMISSION = "canvas.players.max-visible.bypass";

    /**
     * A player with this permission is always shown and does not count towards the cap of any viewer.
     */
    public static final String EXEMPT_PERMISSION = "canvas.players.max-visible.exempt";

    private static volatile @Nullable BiPredicate<Player, Player> pairExemption;
    private static volatile @Nullable ToIntFunction<Player> viewerLimit;

    private PlayerVisibilityCap() {
    }

    /**
     * Sets the predicate for pairs of players that are always shown to each other, like team mates.
     * <p>
     * Called on region threads about once a second per capped viewer, so it must be fast and thread-safe and may only
     * read the two players it is given.
     *
     * @param alwaysShown tests a viewer and a target, or {@code null} to remove it
     */
    public static void setPairExemption(final @Nullable BiPredicate<Player, Player> alwaysShown) {
        pairExemption = alwaysShown;
    }

    /**
     * Gets the predicate that decides which pairs of players belong together
     *
     * @return the current predicate testing a viewer and a target, or {@code null} if none is set
     *
     * @see #setPairExemption(BiPredicate)
     */
    public static @Nullable BiPredicate<Player, Player> getPairExemption() {
        return pairExemption;
    }

    /**
     * Sets the function giving a viewer a limit of their own, like a player that hid other players in a hub. Returns
     * the most players the viewer is shown, or a negative number for no limit. Applies in every world, the lower of
     * this and the world cap wins, and {@link #BYPASS_PERMISSION} does not lift it.
     * <p>
     * Called on region threads about once a second per player, so it must be fast and thread-safe and may only read
     * the player it is given.
     *
     * @param limit gives the limit of a viewer, or {@code null} to remove it
     */
    public static void setViewerLimit(final @Nullable ToIntFunction<Player> limit) {
        viewerLimit = limit;
    }

    /**
     * Gets the function that gives single viewers a limit of their own
     *
     * @return the current function, or {@code null} if none is set
     *
     * @see #setViewerLimit(ToIntFunction)
     */
    public static @Nullable ToIntFunction<Player> getViewerLimit() {
        return viewerLimit;
    }
}
