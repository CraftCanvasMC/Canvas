package io.canvasmc.canvas.command.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.stream.Collectors;
import io.canvasmc.canvas.command.CommandInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Chunk;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.literal;

public class ResendChunksCommand implements CommandInstance {

    @Override
    public LiteralCommandNode<CommandSourceStack> register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(
            literal("resendchunks").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.world.command.chunkresend"))
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        PlayerChunkSender sender = player.connection.chunkSender;
                        int resent = 0;
                        for (ChunkPos chunkPos : player.getBukkitEntity().getSentChunks().stream().map(this::bukkitChunk2ChunkPos).collect(Collectors.toSet())) {
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

    public @NotNull ChunkPos bukkitChunk2ChunkPos(@NotNull Chunk chunk) {
        return new ChunkPos(chunk.getX(), chunk.getZ());
    }
}
