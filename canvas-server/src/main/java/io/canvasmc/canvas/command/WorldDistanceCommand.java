package io.canvasmc.canvas.command;

import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.canvasmc.canvas.chunk.PerWorldDistanceConfig;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class WorldDistanceCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("worlddistance")
            .requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.command.worlddistance"))
            .then(
                argument("type", StringArgumentType.word()).suggests((context, builder) -> {
                    builder.suggest("view");
                    builder.suggest("simulation");
                    return builder.buildFuture();
                }).then(argument("dimension", DimensionArgument.dimension())
                    .executes(context -> {
                        Type type = Type.valueOf(context.getArgument("type", String.class).toUpperCase(Locale.ROOT));
                        ServerLevel world = DimensionArgument.getDimension(context, "dimension");
                        int distance = type.get(world);
                        context.getSource().sendSuccess(() -> Component.literal(type.name + " distance of world '" + world.getWorld().getName() + "' is " + distance), false);
                        return distance;
                    }).then(argument("distance", IntegerArgumentType.integer())
                        .executes(context -> {
                            Type type = Type.valueOf(context.getArgument("type", String.class).toUpperCase(Locale.ROOT));
                            ServerLevel world = DimensionArgument.getDimension(context, "dimension");
                            int distance = Math.min(context.getArgument("distance", int.class), MoonriseConstants.MAX_VIEW_DISTANCE - 3);
                            type.set(world, distance);
                            PerWorldDistanceConfig state = world.serverLevelData.canvas$distanceConfig;
                            int updated = Math.min(
                                (type.equals(Type.VIEW) ?
                                    state.viewDistanceOrDefault() :
                                    state.simulationDistanceOrDefault()) - 1
                                , MoonriseConstants.MAX_VIEW_DISTANCE - 3);

                            switch (type) {
                                case VIEW -> {
                                    for (ServerPlayer serverPlayer : world.players()) {
                                        serverPlayer.connection.send(serverPlayer.moonrise$getChunkLoader().updateClientChunkRadius(updated));
                                    }
                                    world.getChunkSource().setViewDistance(updated);
                                }
                                case SIMULATION -> world.getChunkSource().setSimulationDistance(updated);
                            }
                            context.getSource().sendSuccess(() -> Component.literal("Set " + type.name.toLowerCase() + " distance of world '" + world.getWorld().getName() + "' to " + distance), false);
                            return distance;
                        }))
                )
            ));
    }

    public enum Type {
        VIEW(
            (world) -> world.serverLevelData.canvas$distanceConfig.viewDistanceOrDefault(),
            (world, dist) -> world.serverLevelData.canvas$distanceConfig.viewDistance().set(dist),
            "View"
        ),
        SIMULATION(
            (world) -> world.serverLevelData.canvas$distanceConfig.simulationDistanceOrDefault(),
            (world, dist) -> world.serverLevelData.canvas$distanceConfig.simulationDistance().set(dist),
            "Simulation"
        );
        private final Function<ServerLevel, Integer> getter;
        private final BiConsumer<ServerLevel, Integer> setter;
        private final String name;

        Type(Function<ServerLevel, Integer> getter, BiConsumer<ServerLevel, Integer> setter, String name) {
            this.getter = getter;
            this.setter = setter;
            this.name = name;
        }

        public int get(ServerLevel world) {
            return this.getter.apply(world);
        }

        public void set(ServerLevel world, int dist) {
            this.setter.accept(world, dist);
        }
    }
}
