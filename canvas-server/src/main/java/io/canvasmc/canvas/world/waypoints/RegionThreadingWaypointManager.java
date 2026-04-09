package io.canvasmc.canvas.world.waypoints;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.moonrise.common.util.TickThread;
import io.canvasmc.canvas.Config;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A region-threading-safe implementation of the {@link ServerWaypointManager}.
 * <br></br>
 * <b>Note:</b> With Folia's region threading implementation, most players will be spread out
 * very far. As a result, the {@link net.minecraft.world.waypoints.WaypointTransmitter.EntityAzimuthConnection} will be
 * the most used. This is optimized enough for region-threading, but this may be revisited in the future for even faster
 * handling of this.
 */
public class RegionThreadingWaypointManager extends ServerWaypointManager {

    /**
     * A rough distance scale that defines how often waypoint updates happen between faraway players.
     * <p>
     * The idea here is simple enough: as players get farther apart, updates become less frequent. This system works
     * great for region threading, where players are usually scattered across huge distances.
     * <br><br>
     * In practice, it's used by {@link #shouldScheduleBasedOnDistance(ServerPlayer, ServerPlayer)} like this:
     * <pre>{@code
     * probability = 1 / (1 + (distance / SCALE)^2)
     * }</pre>
     * That means a player right next to you almost always triggers an update, while someone thousands of blocks away
     * might only do so occasionally — depending on how unlucky (or lucky) their random roll is.
     * <br><br>
     * A value of {@code 4000.0} is a decent middle ground. It avoids constant update spam across regions, but still
     * keeps nearby players synced up. The ideal value probably depends on your server’s density and how forgiving you
     * want the update delay to be.
     */
    private static final double SCALE = Config.INSTANCE.waypointUpdateScale;

    private final MultiThreadedQueue<WaypointTransmitter> waypoints = new MultiThreadedQueue<>();
    private final MultiThreadedQueue<ServerPlayer> players = new MultiThreadedQueue<>();
    private final ServerLevel world;

    private static boolean shouldScheduleBasedOnDistance(@NonNull ServerPlayer origin, ServerPlayer target) {
        final double scaled = origin.distanceTo(target) / SCALE;
        return origin.random.nextDouble() < (1.0 / (1.0 + (scaled * scaled)));
    }

    // Note: this should be scheduled on the 'player'
    private static void breakConnection(@NonNull ServerPlayer player) {
        TickThread.ensureTickThread(player, "Cannot update break connection off owning thread of player");
        final Object2ObjectMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.canvas$activeWaypoints;
        for (WaypointTransmitter.Connection conn : map.values()) {
            conn.disconnect();
        }
        map.clear();
    }

    public RegionThreadingWaypointManager(ServerLevel world) {
        this.world = world;
    }

    public boolean isLocatorBarDisabled() {
        return !world.getGameRules().get(GameRules.LOCATOR_BAR);
    }

    @Override
    public void trackWaypoint(@NonNull WaypointTransmitter waypoint) {
        if (isLocatorBarDisabled()) return;
        if (!waypoints.contains(waypoint)) waypoints.add(waypoint);

        for (ServerPlayer player : players) {
            player.canvas$scheduleToOrRun(() -> {
                createConnection(player, waypoint);
            });
        }
    }

    @Override
    public void updateWaypoint(@NonNull WaypointTransmitter waypoint) {
        if (isLocatorBarDisabled()) return;
        if (!waypoints.contains(waypoint)) return;

        final ServerPlayer updating = (ServerPlayer) waypoint;

        for (ServerPlayer player : players) {
            if (player == updating) continue;

            if (updating.distanceTo(player) > 332.0F && !shouldScheduleBasedOnDistance(updating, player)) {
                continue;
            }

            player.canvas$scheduleToOrRun(() -> {
                updateWaypoint(waypoint, player);
            });
        }
    }

    // Note: this should be scheduled on 'player'
    private void updateWaypoint(WaypointTransmitter waypoint, @NonNull ServerPlayer player) {
        TickThread.ensureTickThread(player, "Cannot update waypoint off owning thread of player");
        final Object2ObjectMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.canvas$activeWaypoints;
        final WaypointTransmitter.Connection conn = map.get(waypoint);

        if (conn != null) {
            CallbackHolder callbackHolder = updateConnection(player, waypoint, conn);
            if (callbackHolder != null) callbackHolder.call.run();
        }
        else {
            createConnection(player, waypoint);
        }
    }

    @Override
    public void untrackWaypoint(@NonNull WaypointTransmitter waypoint) {
        for (ServerPlayer player : players) {
            player.canvas$scheduleToOrRun(() -> {
                disconnectWaypoint(waypoint, player);
            });
        }

        waypoints.remove(waypoint);
    }

    // Note: this should be scheduled on the 'player'
    private void disconnectWaypoint(WaypointTransmitter waypoint, @NonNull ServerPlayer player) {
        TickThread.ensureTickThread(player, "Cannot disconnect waypoint off owning thread of player");
        final Object2ObjectMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.canvas$activeWaypoints;
        final WaypointTransmitter.Connection conn = map.remove(waypoint);
        if (conn != null) conn.disconnect();
    }

    @Override
    // Note: this should be called on the 'player'
    public void addPlayer(@NonNull ServerPlayer player) {
        if (!players.contains(player)) players.add(player);

        if (isLocatorBarDisabled()) return;
        for (WaypointTransmitter waypoint : waypoints) {
            createConnection(player, waypoint);
        }

        if (player.isTransmittingWaypoint()) {
            trackWaypoint(player);
        }
    }

    @Override
    // Note: this should be called on the 'player'
    public void updatePlayer(@NonNull ServerPlayer player) {
        if (isLocatorBarDisabled()) return;
        player.canvas$scheduleToOrRun(() -> {
            final Object2ObjectMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.canvas$activeWaypoints;
            final CallbackHolder[] callbackHolders = new CallbackHolder[map.size()];

            int idx = 0;
            for (Map.Entry<WaypointTransmitter, WaypointTransmitter.Connection> entry : map.object2ObjectEntrySet()) {
                CallbackHolder callbackHolder = updateConnection(player, entry.getKey(), entry.getValue());
                if (callbackHolder != null) {
                    callbackHolders[idx] = callbackHolder;
                    idx++;
                }
            }

            for (final CallbackHolder callbackHolder : callbackHolders) {
                if (callbackHolder == null) break;
                callbackHolder.call.run();
            }

            for (WaypointTransmitter waypoint : waypoints) {
                if (!map.containsKey(waypoint)) {
                    createConnection(player, waypoint);
                }
            }
        });
    }

    @Override
    // Note: this should be called on the 'player'
    public void removePlayer(@NonNull ServerPlayer player) {
        breakConnection(player);

        untrackWaypoint(player);
        players.remove(player);
    }

    @Override
    public void breakAllConnections() {
        for (ServerPlayer player : players) {
            player.canvas$scheduleToOrRun(() -> {
                breakConnection(player);
            });
        }
    }

    @Override
    public void remakeConnections(@NonNull WaypointTransmitter waypoint) {
        for (ServerPlayer player : players) {
            player.canvas$scheduleToOrRun(() -> {
                createConnection(player, waypoint);
            });
        }
    }

    @Override
    public @NonNull Set<WaypointTransmitter> transmitters() {
        return new HashSet<>(waypoints);
    }

    // Note: this should be scheduled on 'player'
    private void createConnection(ServerPlayer player, WaypointTransmitter waypoint) {
        if (player == waypoint) return;
        TickThread.ensureTickThread(player, "Cannot create waypoint connection off owning thread of player");

        final Object2ObjectMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.canvas$activeWaypoints;

        waypoint.makeWaypointConnectionWith(player).ifPresentOrElse(connection -> {
            map.put(waypoint, connection);
            connection.connect();
        }, () -> {
            WaypointTransmitter.Connection existing = map.remove(waypoint);
            if (existing != null) existing.disconnect();
        });
    }

    // Note: this should be scheduled on 'player'
    private RegionThreadingWaypointManager.@Nullable CallbackHolder updateConnection(ServerPlayer player, WaypointTransmitter waypoint, WaypointTransmitter.Connection connection) {
        if (player == waypoint) return null;
        TickThread.ensureTickThread(player, "Cannot update waypoint connection off owning thread of player");

        final Object2ObjectMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.canvas$activeWaypoints;

        if (!connection.isBroken()) {
            connection.update();
        }
        else {
            CallbackHolder ref = new CallbackHolder();
            waypoint.makeWaypointConnectionWith(player).ifPresentOrElse(newConn -> ref.call = () -> {
                newConn.connect();
                map.put(waypoint, newConn);
            }, () -> ref.call = () -> {
                connection.disconnect();
                map.remove(waypoint);
            });
            return ref;
        }
        return null;
    }

    public ServerLevel getWorld() {
        return world;
    }

    private static class CallbackHolder {
        /**
         * In usage of this, if the class != null, then this field should be non-null
         */
        Runnable call;
    }
}
