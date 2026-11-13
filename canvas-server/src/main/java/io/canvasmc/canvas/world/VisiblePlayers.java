package io.canvasmc.canvas.world;

import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.entity.PlayerVisibilityCap;
import io.papermc.paper.threadedregions.RegionizedWorldData;
import io.papermc.paper.threadedregions.TickRegionScheduler;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.jspecify.annotations.Nullable;

/**
 * The other players a viewer may be sent while {@code players.max-visible} or a viewer limit applies.
 * Owned by the viewer and only touched by the region that owns them. Holds entity ids, not players.
 */
public final class VisiblePlayers {

    // ticks between choosing the shown players again
    public static final int RECOMPUTE_INTERVAL_TICKS = 20;

    // players already shown rank 25% closer, so two players at the edge of the cap don't keep swapping
    public static final double SHOWN_DISTANCE_FACTOR = 0.75D;

    private static final Comparator<Candidate> NEAREST_FIRST = Comparator.comparingDouble(Candidate::distanceSquared);

    // the last plugin callbacks that threw, so each is only logged once
    private static volatile @Nullable Object failedPairExemption;
    private static volatile @Nullable Object failedViewerLimit;

    static {
        register(PlayerVisibilityCap.BYPASS_PERMISSION, "Shows every player in worlds with players.max-visible set");
        register(PlayerVisibilityCap.EXEMPT_PERMISSION, "Is shown to every player in worlds with players.max-visible set");
    }

    private IntOpenHashSet shown = new IntOpenHashSet();
    private int counted;
    private boolean unlimited;
    private int computedTick;
    private int computedMax;
    private int limit;

    private VisiblePlayers() {
    }

    private static void register(final String name, final String description) {
        try {
            Bukkit.getPluginManager().addPermission(new Permission(name, description, PermissionDefault.FALSE));
        } catch (final IllegalArgumentException ignored) {
        }
    }

    /**
     * Checks if the viewer may be sent the target. Only called by the region owning the viewer, for a target the
     * viewer would be sent without the cap.
     *
     * @param max the {@code players.max-visible} value of their world
     * @return {@code false} if the target is capped out for the viewer
     */
    public static boolean allows(final ServerPlayer viewer, final ServerPlayer target, final int max) {
        VisiblePlayers visible = viewer.canvas$visiblePlayers;

        if (visible == null) {
            visible = viewer.canvas$visiblePlayers = new VisiblePlayers();
            visible.recompute(viewer, max);
        } else {
            final int age = viewer.tickCount - visible.computedTick;

            if (age >= RECOMPUTE_INTERVAL_TICKS || age < 0 || visible.computedMax != max) {
                visible.recompute(viewer, max);
            }
        }

        if (visible.unlimited || visible.shown.contains(target.getId())) {
            return true;
        }

        // below the cap, a player coming into range is shown right away
        if (visible.counted < visible.limit && !isCarried(target)) {
            visible.shown.add(target.getId());
            visible.counted++;
            return true;
        }

        return false;
    }

    // a player riding another player is shown with its carrier
    private static boolean isCarried(final ServerPlayer target) {
        return target.isPassenger() && target.getRootVehicle() instanceof ServerPlayer;
    }

    private void recompute(final ServerPlayer viewer, final int max) {
        this.computedTick = viewer.tickCount;
        this.computedMax = max;

        final RegionizedWorldData worldData = TickRegionScheduler.getCurrentRegionizedWorldData();

        final int own = ownLimit(viewer);
        final boolean capped = max > 0 && !viewer.getBukkitEntity().hasPermission(PlayerVisibilityCap.BYPASS_PERMISSION);

        // the bypass permission only lifts the world cap, not the viewer's own limit
        if (own >= 0) {
            this.limit = capped ? Math.min(max, own) : own;
        } else {
            this.limit = capped ? max : -1;
        }

        this.unlimited = this.limit < 0 || worldData == null || worldData.world != viewer.level();

        if (this.unlimited) {
            this.shown = new IntOpenHashSet();
            this.counted = 0;
            return;
        }

        final IntOpenHashSet next = new IntOpenHashSet();
        final List<Candidate> ranked = new ArrayList<>();
        final List<ServerPlayer> carried = new ArrayList<>();
        final Entity viewerRoot = viewer.getRootVehicle();

        for (final ServerPlayer target : worldData.getLocalPlayers()) {
            if (target == viewer) {
                continue;
            }

            final ChunkMap.TrackedEntity tracker = target.moonrise$getTrackedEntity();

            // players that would not be sent anyway don't take up a place
            if (tracker == null || !tracker.canvas$updatePlayer(viewer, true)) {
                continue;
            }

            final Entity targetRoot = target.getRootVehicle();

            if (targetRoot == viewerRoot) {
                next.add(target.getId());
                continue;
            }

            if (isCarried(target)) {
                carried.add(target);
                continue;
            }

            if (target.getBukkitEntity().hasPermission(PlayerVisibilityCap.EXEMPT_PERMISSION)) {
                next.add(target.getId());
                continue;
            }

            double distanceSquared = viewer.distanceToSqr(target);

            if (tracker.seenBy.contains(viewer.connection)) {
                distanceSquared *= SHOWN_DISTANCE_FACTOR * SHOWN_DISTANCE_FACTOR;
            }

            ranked.add(new Candidate(target, distanceSquared));
        }

        if (ranked.size() > this.limit) {
            ranked.sort(NEAREST_FIRST);
        }

        final BiPredicate<Player, Player> pairExemption = PlayerVisibilityCap.getPairExemption();

        for (int i = 0, len = ranked.size(); i < len; ++i) {
            final ServerPlayer target = ranked.get(i).player();

            if (i < this.limit || isPairExempt(pairExemption, viewer, target)) {
                next.add(target.getId());
            }
        }

        for (final ServerPlayer target : carried) {
            if (next.contains(target.getRootVehicle().getId())) {
                next.add(target.getId());
            }
        }

        this.shown = next;
        this.counted = Math.min(ranked.size(), this.limit);
    }

    private static int ownLimit(final ServerPlayer viewer) {
        final ToIntFunction<Player> viewerLimit = PlayerVisibilityCap.getViewerLimit();

        if (viewerLimit == null) {
            return -1;
        }

        try {
            return viewerLimit.applyAsInt(viewer.getBukkitEntity());
        } catch (final Throwable thrown) {
            if (failedViewerLimit != viewerLimit) {
                failedViewerLimit = viewerLimit;
                GlobalConfiguration.LOGGER.error("Max visible players viewer limit threw, ignoring it", thrown);
            }

            return -1;
        }
    }

    private static boolean isPairExempt(final @Nullable BiPredicate<Player, Player> pairExemption, final ServerPlayer viewer, final ServerPlayer target) {
        if (pairExemption == null) {
            return false;
        }

        try {
            return pairExemption.test(viewer.getBukkitEntity(), target.getBukkitEntity());
        } catch (final Throwable thrown) {
            if (failedPairExemption != pairExemption) {
                failedPairExemption = pairExemption;
                GlobalConfiguration.LOGGER.error("Max visible players pair exemption threw, ignoring it", thrown);
            }

            return false;
        }
    }

    private record Candidate(ServerPlayer player, double distanceSquared) {
    }
}
