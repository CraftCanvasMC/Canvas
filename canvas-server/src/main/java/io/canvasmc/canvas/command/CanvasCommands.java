package io.canvasmc.canvas.command;

import com.google.common.collect.Sets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.canvasmc.canvas.command.debug.PriorityCommand;
import io.canvasmc.canvas.command.debug.ResendChunksCommand;
import io.canvasmc.canvas.command.debug.SenderInfoCommand;
import io.canvasmc.canvas.command.debug.SyncloadCommand;
import io.canvasmc.canvas.command.debug.TrackingControlCommand;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.Supplier;

import static net.minecraft.commands.Commands.literal;

public final class CanvasCommands {
    private static final Set<LiteralCommandNode<CommandSourceStack>> ALL = Sets.newHashSet();
    private static CommandDispatcher<CommandSourceStack> DISPATCHER;

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        CanvasCommands.DISPATCHER = dispatcher;
        register(SimulationDistanceCommand::new);
        register(ViewDistanceCommand::new);
        register(ResendChunksCommand::new);
        register(SenderInfoCommand::new);
        register(PriorityCommand::new);
        register(SetMaxPlayersCommand::new);
        register(TrackingControlCommand::new);
        register(SyncloadCommand::new);
    }

    private static void register(@NotNull Supplier<? extends CommandInstance> instance) {
        // TODO - /canvas <sub-node> ?
        CommandInstance command = instance.get();
        ALL.add(command.register(DISPATCHER));
    }
}
