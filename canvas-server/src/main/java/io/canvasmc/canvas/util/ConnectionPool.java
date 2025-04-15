package io.canvasmc.canvas.util;

import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.region.ServerRegions;
import io.canvasmc.canvas.server.network.ConnectionHandlePhases;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import org.agrona.collections.ObjectHashSet;
import org.jetbrains.annotations.NotNull;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class ConnectionPool {
    private final Set<Connection> backend = new CopyOnWriteArraySet<>();
    private final MinecraftServer server;

    public ConnectionPool(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public Set<Connection> getFull() {
        return backend;
    }

    public Set<Connection> getConnectionsForRegion(ServerRegions.@NotNull WorldTickData regionData) {
        Set<Connection> retVal = new ObjectHashSet<>();
        if (regionData.region == null) {
            if (Config.INSTANCE.ticking.enableThreadedRegionizing) throw new RuntimeException("Cannot pull connections from world data with regionizing enabled!");
            // world data was provided
            for (final Connection connection : this.backend) {
                if (connection.getPhase().equals(ConnectionHandlePhases.PLAY) && connection.getPlayer().serverLevel() == regionData.world) retVal.add(connection);
            }
        } else {
            // region is not null, isolate per region
            for (final Connection connection : this.backend) {
                if (connection.getPlayer() == null || regionData.world != connection.getPlayer().serverLevel()) continue; // ensure player isn't null and ensure the player is actually in this world
                long packed = connection.getPlayer().chunkPosition().longKey;
                if (connection.getPhase().equals(ConnectionHandlePhases.PLAY) && regionData.region.getOwnedChunks().contains(packed)) retVal.add(connection);
            }
        }
        return retVal;
    }

    public void disconnect(Connection connection) {
        this.backend.remove(connection);
    }

    public void connect(Connection connection) {
        this.backend.add(connection);
    }
}
