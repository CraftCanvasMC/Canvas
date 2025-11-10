package io.canvasmc.canvas.util;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;

public record PlayerListBucket(ServerPlayer[] bucket) {
    public void tick(ServerPlayer[] allPlayers) {
        List<ServerPlayer> players = Arrays.asList(allPlayers);

        for (int i = bucket.length - 1; i >= 0; i--) {
            ServerPlayer target = bucket[i];
            target.connection.send(
                new ClientboundPlayerInfoUpdatePacket(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY),
                    com.google.common.collect.Collections2.filter(players, t -> target.getBukkitEntity().canSee(t.getBukkitEntity()))
                )
            );
        }
    }
}
