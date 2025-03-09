package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Chunk;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.literal;

public class ResendChunksCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("resendchunks").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.world.command.chunkresend"))
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        PlayerChunkSender sender = player.connection.chunkSender;
                        int resent = 0;
                        for (ChunkPos chunkPos : player.getBukkitEntity().getSentChunks().stream().map(ResendChunksCommand::bukkitChunk2ChunkPos).collect(Collectors.toSet())) {
                            sender.dropChunk(player, chunkPos);
                            PlayerChunkSender.sendChunk(player.connection, player.serverLevel(), player.level().getChunk(chunkPos.x, chunkPos.z));
                            resent++;
                        }
                        player.sendSystemMessage(Component.literal("Resent " + resent + " chunks to client"));
                        return 0;
                    } else {
                        context.getSource().sendFailure(Component.literal("Only a player can execute this command!"));
                        return 1;
                    }
                })
        );
    }

    public static @NotNull ChunkPos bukkitChunk2ChunkPos(@NotNull Chunk chunk) {
        return new ChunkPos(chunk.getX(), chunk.getZ());
    }
}
