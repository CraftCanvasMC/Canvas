package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
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
                        ChunkHolder holder = player.serverLevel().getChunk(player.chunkPosition().x, player.chunkPosition().z).chunkAndHolder.holder();
                        for (final ShortSet shorts : holder.changedBlocksPerSection) {
                            player.sendSystemMessage(Component.literal("In chunk found changed shorts: " + shorts));
                        }
                        player.sendSystemMessage(Component.literal("attempting broadcast..."));
                        holder.broadcastChanges(player.serverLevel().getChunk(player.chunkPosition().x, player.chunkPosition().z).chunkAndHolder.chunk());
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
