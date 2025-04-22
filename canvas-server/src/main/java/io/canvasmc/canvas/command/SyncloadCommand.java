package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SyncloadCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("syncload").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.debug.command.syncload"))
                .then(argument("coords", ColumnPosArgument.columnPos()).executes(context -> {
                    long startNanos = Util.getNanos();
                    ChunkPos pos = ColumnPosArgument.getColumnPos(context, "coords").toChunkPos();
                    LevelChunk chunk = (LevelChunk) context.getSource().getLevel().chunkSource.getChunk(pos.x, pos.z, ChunkStatus.FULL, true);
                    if (chunk == null) {
                        context.getSource().sendFailure(Component.literal("Couldn't syncload chunk"));
                        return 0;
                    }
                    context.getSource().sendSystemMessage(Component.literal("Syncloaded chunk in " + TimeUnit.MILLISECONDS.convert((Util.getNanos() - startNanos), TimeUnit.NANOSECONDS) + "ms"));
                    return 1;
                }))
        );
    }
}
