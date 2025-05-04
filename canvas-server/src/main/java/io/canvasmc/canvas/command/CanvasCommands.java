package io.canvasmc.canvas.command;

import com.google.common.collect.Sets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.canvasmc.canvas.CanvasBootstrap;
import io.canvasmc.canvas.command.debug.FlySpeedCommand;
import io.canvasmc.canvas.command.debug.LevelTicksCommand;
import io.canvasmc.canvas.command.debug.PriorityCommand;
import io.canvasmc.canvas.command.debug.ResendChunksCommand;
import io.canvasmc.canvas.command.debug.SenderInfoCommand;
import io.canvasmc.canvas.command.debug.SyncloadCommand;
import io.canvasmc.canvas.command.debug.TasksCommand;
import io.canvasmc.canvas.command.debug.TrackingControlCommand;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.DebugMobSpawningCommand;
import net.minecraft.server.commands.DebugPathCommand;
import net.minecraft.server.commands.RaidCommand;
import net.minecraft.server.commands.ServerPackCommand;
import net.minecraft.server.commands.SpawnArmorTrimsCommand;
import net.minecraft.server.commands.WardenSpawnTrackerCommand;
import org.jetbrains.annotations.NotNull;

public final class CanvasCommands {
    private static final Set<LiteralCommandNode<CommandSourceStack>> ALL = Sets.newHashSet();
    private static CommandDispatcher<CommandSourceStack> DISPATCHER;

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher, final CommandBuildContext context) {
        CanvasCommands.DISPATCHER = dispatcher;
        register(SimulationDistanceCommand::new);
        register(ViewDistanceCommand::new);
        register(SetMaxPlayersCommand::new);
        if (CanvasBootstrap.RUNNING_IN_IDE) {
            CanvasBootstrap.LOGGER.info("Registering Canvas debug commands");
            register(ResendChunksCommand::new);
            register(SenderInfoCommand::new);
            register(TrackingControlCommand::new);
            register(SyncloadCommand::new);
            register(PriorityCommand::new);
            register(FlySpeedCommand::new);
            register(TasksCommand::new);
            register(LevelTicksCommand::new);

            CanvasBootstrap.LOGGER.info("Registering Minecraft debug commands");
            RaidCommand.register(dispatcher, context);
            DebugPathCommand.register(dispatcher);
            DebugMobSpawningCommand.register(dispatcher);
            WardenSpawnTrackerCommand.register(dispatcher);
            SpawnArmorTrimsCommand.register(dispatcher);
            ServerPackCommand.register(dispatcher);
        }
    }

    private static void register(@NotNull Supplier<? extends CommandInstance> instance) {
        // TODO - /canvas <sub-node> ?
        CommandInstance command = instance.get();
        ALL.add(command.register(DISPATCHER));
    }
}
