package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.canvasmc.canvas.server.level.distance.WorldSpecificViewDistancePersistentState;
import io.canvasmc.canvas.server.level.distance.command.CommandUtils;
import io.canvasmc.canvas.server.level.distance.component.WorldSpecificViewDistanceComponents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class SimulationDistanceCommand implements CommandInstance {

    @Override
    public  LiteralCommandNode<CommandSourceStack> register(@NotNull CommandDispatcher<CommandSourceStack> commandDispatcher) {
        return commandDispatcher.register(
            literal("simulationdistance")
                .then(literal("set")
                    .requires((src) -> src.hasPermission(3, "canvas.world.command.simulationdistance"))
                    .then(literal("global")
                        .then(argument("simulationDistance", IntegerArgumentType.integer(0))
                            .executes(this::setGlobalSimulationDistance)))
                    .then(argument("dimension", DimensionArgument.dimension())
                        .then(argument("simulationDistance", IntegerArgumentType.integer(0))
                            .executes(this::setWorldSimulationDistance))))
                .then(literal("get")
                    .then(literal("global")
                        .executes(this::getGlobalSimulationDistance))
                    .then(argument("dimension", DimensionArgument.dimension())
                        .executes(this::getWorldSimulationDistance))));
    }

    public int setWorldSimulationDistance(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int simDist = IntegerArgumentType.getInteger(ctx, "simulationDistance");
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = DimensionArgument.getDimension(ctx, "dimension");

        WorldSpecificViewDistancePersistentState state = WorldSpecificViewDistancePersistentState.getFrom(level);
        state.setLocalSimulationDistance(simDist);

        for (ServerPlayer spe : level.players()) {
            spe.moonrise$getChunkLoader()
                .updateClientSimulationDistance(simDist == 0 ? level.getServer().getPlayerList().getSimulationDistance() : simDist - 1);
        }

        level.getChunkSource().setSimulationDistance(simDist == 0 ? level.getServer().getPlayerList().getSimulationDistance() : simDist - 1);

        src.sendSuccess(() -> CommandUtils.getMessage(
            "Set simulation distance of world %s to %d",
            CommandUtils.getRegistryId(level), simDist), true);
        return 1;
    }

    public int getWorldSimulationDistance(@NotNull CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerLevel w = DimensionArgument.getDimension(ctx, "dimension");

        WorldSpecificViewDistancePersistentState state = WorldSpecificViewDistancePersistentState.getFrom(w);
        int simDist = state.getLocalSimulationDistance();

        if (simDist != 0) {
            src.sendSuccess(() -> CommandUtils.getMessage(
                "Simulation distance of world %s is %d",
                CommandUtils.getRegistryId(w), simDist), false);
        } else {
            src.sendSuccess(() -> CommandUtils.getMessage("Simulation distance of world %s is unspecified (currently %d)",
                CommandUtils.getRegistryId(w), src.getServer().getPlayerList().getSimulationDistance() + 1), false);
        }

        return 1;
    }

    public int getGlobalSimulationDistance(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int simDist = src.getServer().getPlayerList().getSimulationDistance() + 1;

        src.sendSuccess(() -> CommandUtils.getMessage("Server-wide simulation distance is currently %d", simDist), false);

        return 1;
    }

    public int setGlobalSimulationDistance(@NotNull CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int simDist = IntegerArgumentType.getInteger(ctx, "simulationDistance");

        if (src.getServer().isDedicatedServer()) {
            src.getServer().getPlayerList().setSimulationDistance(simDist - 1);

            src.sendSuccess(() -> CommandUtils.getMessage("Set server-wide simulation distance to %d", simDist), true);
        } else {
            var component = WorldSpecificViewDistanceComponents.GLOBAL_DISTANCES.get(src.getServer().getWorldData());

            component.globalSimulationDistance = simDist;

            if (simDist != 0) {
                src.sendSuccess(() -> CommandUtils.getMessage("Set save simulation distance to %d", simDist), true);
            } else {
                src.sendSuccess(() -> CommandUtils.getMessage("Unset save simulation distance"), true);
            }
        }

        return 1;
    }


}
