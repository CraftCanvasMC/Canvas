package io.canvasmc.canvas.command.sub;

import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.command.Command;
import io.canvasmc.canvas.world.PerWorldDistanceConfig;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;

/**
 * Command for viewing or setting the view/simulation distance of a specific world.
 * Usage examples:
 * <ul>
 *     <li><code>/canvas worlddistance view minecraft:overworld</code></li>
 *     <li><code>/canvas worlddistance simulation minecraft:the_nether 10</code></li>
 *     <li><code>/worlddistance view minecraft:the_end</code></li>
 *     <li><code>/canvas:worlddistance simulation minecraft:overworld 8</code></li>
 * </ul>
 */
@NullMarked
public class WorldDistanceCommand implements Command {

    @Override
    public String getName() {
        return "worlddistance";
    }

    @Override
    public @Nullable String getDescription() {
        return "Gets or sets the view/simulation distance for a specific world.";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base) {
        return base.requires(stack -> stack.hasPermission(Permissions.COMMANDS_ADMIN, "canvas.command.worlddistance"))
            .then(argument("type", StringArgumentType.word())
                .suggests((_, builder) -> {
                    builder.suggest("view");
                    builder.suggest("simulation");
                    return builder.buildFuture();
                })
                .then(argument("dimension", DimensionArgument.dimension())
                    .executes(context -> {
                        Type type = Type.valueOf(context.getArgument("type", String.class).toUpperCase(Locale.ROOT));
                        ServerLevel level = DimensionArgument.getDimension(context, "dimension");
                        int distance = type.get(level);

                        context.getSource().sendSuccess(
                            () -> Component.literal(type.name + " distance of level \"" + level.getWorld().key().asString() + "\" is " + distance),
                            false
                        );
                        return distance;
                    })
                    .then(argument("distance", IntegerArgumentType.integer())
                        .executes(context -> {
                            Type type = Type.valueOf(context.getArgument("type", String.class).toUpperCase(Locale.ROOT));
                            ServerLevel level = DimensionArgument.getDimension(context, "dimension");
                            int distance = Math.min(context.getArgument("distance", int.class), MoonriseConstants.MAX_VIEW_DISTANCE - 3);

                            if (distance < -1 || distance == 0 || distance == 1) {
                                context.getSource().sendFailure(Component.literal("New value must be above 1, or set to -1 to disable override"));
                                return 0;
                            }

                            type.set(level, distance);
                            PerWorldDistanceConfig state = level.serverLevelData.canvas$distanceConfig;

                            int updated = Math.min(
                                (type.equals(Type.VIEW)
                                    ? state.viewDistanceOrDefault()
                                    : state.simulationDistanceOrDefault()),
                                MoonriseConstants.MAX_VIEW_DISTANCE - 3
                            );

                            switch (type) {
                                case VIEW -> {
                                    for (ServerPlayer serverPlayer : level.players()) {
                                        serverPlayer.connection.send(serverPlayer.moonrise$getChunkLoader().updateClientChunkRadius(updated));
                                    }
                                    level.getChunkSource().setViewDistance(updated);
                                }
                                case SIMULATION -> level.getChunkSource().setSimulationDistance(updated);
                            }

                            GlobalConfiguration.broadcast("Set " + type.name.toLowerCase() + " distance of level \"" + level.getWorld().key().asString() + "\" to " + distance, GlobalConfiguration.INFO);
                            return distance;
                        })
                    )
                )
            );
    }

    /**
     * Enum representing distance configuration types (view or simulation).
     */
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

        public int get(ServerLevel level) {
            return this.getter.apply(level);
        }

        public void set(ServerLevel level, int dist) {
            this.setter.accept(level, dist);
        }
    }
}
