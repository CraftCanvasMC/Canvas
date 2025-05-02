package io.canvasmc.canvas.command.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.canvasmc.canvas.command.CommandInstance;
import java.util.concurrent.TimeUnit;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SyncloadCommand implements CommandInstance {

    @Override
    public LiteralCommandNode<CommandSourceStack> register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(
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
