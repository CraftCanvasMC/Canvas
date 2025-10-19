package io.canvasmc.canvas.waypoints;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.moonrise.common.util.TickThread;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.waypoints.ServerWaypointManager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.waypoints.WaypointTransmitter;
import org.jetbrains.annotations.NotNull;

/**
 * A region-threading-safe implementation of the {@link ServerWaypointManager}.
 * <br></br>
 * <b>Note:</b> With Folia's region threading implementation, most players will be spread out
 * very far. As a result, the {@link net.minecraft.world.waypoints.WaypointTransmitter.EntityAzimuthConnection} will be
 * the most used. This is optimized enough for region-threading, but this may be revisited in the future for even faster
 * handling of this.
 */
public class RegionThreadingWaypointManager extends ServerWaypointManager {

    // TODO - define by config?
    private static final double SCALE = 4000.0;

    private final MultiThreadedQueue<WaypointTransmitter> waypoints = new MultiThreadedQueue<>();
    private final MultiThreadedQueue<ServerPlayer> players = new MultiThreadedQueue<>();
    private final ServerLevel world;

    public RegionThreadingWaypointManager(ServerLevel world) {
        this.world = world;
    }

    public boolean isLocatorBarDisabled() {
        return !world.getGameRules().getBoolean(GameRules.RULE_LOCATOR_BAR);
    }

    @Override
    public void trackWaypoint(WaypointTransmitter waypoint) {
        if (isLocatorBarDisabled()) return;
        waypoints.add(waypoint);

        for (ServerPlayer player : players) {
            if (!TickThread.isTickThreadFor(player)) {
                player.getBukkitEntity().taskScheduler.schedule((entity) -> {
                    createConnection((ServerPlayer) entity, waypoint);
                }, null, 0L);
                continue;
            }
            createConnection(player, waypoint);
        }
    }

    private static boolean shouldScheduleBasedOnDistance(@NotNull ServerPlayer origin, ServerPlayer target) {
        final double scaled = origin.distanceTo(target) / SCALE;
        return origin.random.nextDouble() < (1.0 / (1.0 + (scaled * scaled)));
    }

    @Override
    public void updateWaypoint(WaypointTransmitter waypoint) {
        if (isLocatorBarDisabled()) return;
        if (!waypoints.contains(waypoint)) return;

        final ServerPlayer updating = (ServerPlayer) waypoint;

        for (ServerPlayer player : players) {
            if (player == updating) continue;

            if (updating.distanceTo(player) > 332.0F && !shouldScheduleBasedOnDistance(updating, player)) {
                continue;
            }

            if (!TickThread.isTickThreadFor(player)) {
                player.getBukkitEntity().taskScheduler.schedule((entity) -> {
                    updateWaypoint(waypoint, (ServerPlayer) entity);
                }, null, 0L);
                continue;
            }

            updateWaypoint(waypoint, player);
        }
    }

    // Note: this should be scheduled on 'player'
    private void updateWaypoint(WaypointTransmitter waypoint, @NotNull ServerPlayer player) {
        final Object2ObjectOpenHashMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.activeWaypoints;
        final WaypointTransmitter.Connection conn = map.get(waypoint);

        if (conn != null) {
            updateConnection(player, waypoint, conn);
        } else {
            createConnection(player, waypoint);
        }
    }

    @Override
    public void untrackWaypoint(WaypointTransmitter waypoint) {
        for (ServerPlayer player : players) {
            if (!TickThread.isTickThreadFor(player)) {
                player.getBukkitEntity().taskScheduler.schedule((entity) -> {
                    disconnectWaypoint(waypoint, (ServerPlayer) entity);
                }, null, 0L);
                continue;
            }
            disconnectWaypoint(waypoint, player);
        }

        waypoints.remove(waypoint);
    }

    // Note: this should be scheduled on the 'player'
    private void disconnectWaypoint(WaypointTransmitter waypoint, @NotNull ServerPlayer player) {
        final Object2ObjectOpenHashMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.activeWaypoints;
        final WaypointTransmitter.Connection conn = map.remove(waypoint);
        if (conn != null) conn.disconnect();
    }

    @Override
    // Note: this should be called on the 'player'
    public void addPlayer(ServerPlayer player) {
        players.add(player);

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
    public void updatePlayer(@NotNull ServerPlayer player) {
        if (isLocatorBarDisabled()) return;
        final Object2ObjectOpenHashMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.activeWaypoints;

        for (Map.Entry<WaypointTransmitter, WaypointTransmitter.Connection> entry : map.object2ObjectEntrySet()) {
            updateConnection(player, entry.getKey(), entry.getValue());
        }

        for (WaypointTransmitter waypoint : waypoints) {
            if (!map.containsKey(waypoint)) {
                createConnection(player, waypoint);
            }
        }
    }

    @Override
    // Note: this should be called on the 'player'
    public void removePlayer(@NotNull ServerPlayer player) {
        breakConnection(player);

        untrackWaypoint(player);
        players.remove(player);
    }

    @Override
    public void breakAllConnections() {
        for (ServerPlayer player : players) {
            if (!TickThread.isTickThreadFor(player)) {
                player.getBukkitEntity().taskScheduler.schedule((entity) -> {
                    breakConnection((ServerPlayer) entity);
                }, null, 0L);
                continue;
            }
            breakConnection(player);
        }
    }

    // Note: this should be scheduled on the 'player'
    private static void breakConnection(@NotNull ServerPlayer player) {
        final Object2ObjectOpenHashMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.activeWaypoints;
        for (WaypointTransmitter.Connection conn : map.values()) {
            conn.disconnect();
        }
        map.clear();
    }

    @Override
    public void remakeConnections(WaypointTransmitter waypoint) {
        for (ServerPlayer player : players) {
            if (!TickThread.isTickThreadFor(player)) {
                player.getBukkitEntity().taskScheduler.schedule((entity) -> {
                    createConnection((ServerPlayer) entity, waypoint);
                }, null, 0L);
                continue;
            }
            createConnection(player, waypoint);
        }
    }

    @Override
    public Set<WaypointTransmitter> transmitters() {
        return new HashSet<>(waypoints);
    }

    // Note: this should be scheduled on 'player'
    private void createConnection(ServerPlayer player, WaypointTransmitter waypoint) {
        if (player == waypoint) return;

        final Object2ObjectOpenHashMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.activeWaypoints;

        waypoint.makeWaypointConnectionWith(player).ifPresentOrElse(connection -> {
            map.put(waypoint, connection);
            connection.connect();
        }, () -> {
            WaypointTransmitter.Connection existing = map.remove(waypoint);
            if (existing != null) existing.disconnect();
        });
    }

    // Note: this should be scheduled on 'player'
    private void updateConnection(ServerPlayer player, WaypointTransmitter waypoint, WaypointTransmitter.Connection connection) {
        if (player == waypoint) return;

        final Object2ObjectOpenHashMap<WaypointTransmitter, WaypointTransmitter.Connection> map = player.activeWaypoints;

        if (!connection.isBroken()) {
            connection.update();
        } else {
            waypoint.makeWaypointConnectionWith(player).ifPresentOrElse(newConn -> {
                newConn.connect();
                map.put(waypoint, newConn);
            }, () -> {
                connection.disconnect();
                map.remove(waypoint);
            });
        }
    }

    public ServerLevel getWorld() {
        return world;
    }
}
