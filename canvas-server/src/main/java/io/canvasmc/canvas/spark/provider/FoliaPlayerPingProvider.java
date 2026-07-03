package io.canvasmc.canvas.spark.provider;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import me.lucko.spark.paper.common.monitor.ping.PlayerPingProvider;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Unmodifiable;

public record FoliaPlayerPingProvider(Server server) implements PlayerPingProvider {

    @Unmodifiable
    @Override
    public Map<String, Integer> poll() {
        final ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
        for (final Player player : this.server.getOnlinePlayers()) {
            builder.put(player.getName(), player.getPing());
        }
        return builder.build();
    }
}
