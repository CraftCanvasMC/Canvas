package io.canvasmc.canvas.server.level;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.ServerTickRateManager;

public interface TickRateManagerInstance {
    void broadcastPacketsToPlayers(Packet<?> packet);

    CommandSourceStack createCommandSourceStack();

    void onTickRateChanged();

    ServerTickRateManager tickRateManager();

    void skipTickWait();
}
