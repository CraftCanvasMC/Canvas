package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import io.canvasmc.canvas.region.ServerRegions;
import io.papermc.paper.threadedregions.ThreadedRegionizer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.literal;

public class SenderInfoCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("senderinfo").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.debug.command.senderinfo"))
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        player.sendSystemMessage(Component.literal("== Debug Information, for development purposes only, can change at any time =="));
                        player.sendSystemMessage(Component.literal("Owned by NearbyPlayers of level " + player.npr.get().world));
                        player.sendSystemMessage(Component.literal("tick count " + player.tickCount));
                        /* Connection connection = player.connection.connection;
                        ChunkPos chunkPos = player.chunkPosition();
                        ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region = player.serverLevel().regioniser.getRegionAtUnsynchronised(chunkPos.x, chunkPos.z);
                        if (region == null) return 0;
                        if (!region.getData().tickData.activeConnections.contains(connection)) {
                            MinecraftServer.LOGGER.error("player {} doesn't own connection", player);
                        } else {
                            MinecraftServer.LOGGER.info("player {} has connection", player);
                        } */
                        return 0;
                    } else {
                        context.getSource().sendFailure(Component.literal("Only a player can execute this command!"));
                        return 1;
                    }
                })
        );
    }
}
