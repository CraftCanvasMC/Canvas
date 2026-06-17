package io.canvasmc.canvas.command.sub;

import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.command.SubCommand;
import io.canvasmc.canvas.util.Util;
import io.canvasmc.canvas.world.PerWorldDistanceConfig;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;

/**
 * Command for viewing or setting the view/simulation distance of a specific world. Usage examples:
 * <ul>
 *     <li><code>/canvas worlddistance view minecraft:overworld</code></li>
 *     <li><code>/canvas worlddistance simulation minecraft:the_nether 10</code></li>
 *     <li><code>/worlddistance view minecraft:the_end</code></li>
 *     <li><code>/canvas:worlddistance simulation minecraft:overworld 8</code></li>
 * </ul>
 */
@NullMarked
public class WorldDistanceSubCommand implements SubCommand {

    private static final SimpleCommandExceptionType MUST_BE_PLAYER = new SimpleCommandExceptionType(
        Component.literal("Must be player to get from current player world")
    );
    private static final SimpleCommandExceptionType ILLEGAL_TYPE_ARG = new SimpleCommandExceptionType(
        Component.literal("Illegal type argument. Must be [\"view\", \"simulation\", \"v\", \"s\", or \"sim\"]")
    );
    private static final SimpleCommandExceptionType INVALID_DISTANCE = new SimpleCommandExceptionType(
        Component.literal("New value must be above 1, or set to -1 to disable override")
    );

    private static int getAndReturnDistance(final CommandContext<CommandSourceStack> context, final ServerLevel level) throws CommandSyntaxException {
        final Type type = Type.from(context.getArgument("type", String.class).toUpperCase(Locale.ROOT));
        return getAndReturnDistance(context, type, level);
    }

    private static int getAndReturnDistance(final CommandContext<CommandSourceStack> context, final Type type, final ServerLevel level) {
        final int distance = type.get(level);

        context.getSource().sendSuccess(
            () -> Component.literal(type.name + " distance of level \"" + Util.getLevelName(level) + "\" is " + distance),
            false
        );
        return distance;
    }

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
            .executes(context -> {
                if (context.getSource().isPlayer()) {
                    // assume we are getting from the player dimension
                    final ServerLevel level = context.getSource().getPlayerOrException().level();
                    return getAndReturnDistance(context, Type.VIEW, level) + getAndReturnDistance(context, Type.SIMULATION, level);
                }
                throw MUST_BE_PLAYER.create();
            })
            .then(argument("type", StringArgumentType.word())
                .suggests((_, builder) -> {
                    builder.suggest("view");
                    builder.suggest("simulation");
                    return builder.buildFuture();
                })
                .executes(context -> {
                    if (context.getSource().isPlayer()) {
                        // assume we are getting from the player dimension
                        return getAndReturnDistance(context, context.getSource().getPlayerOrException().level());
                    }
                    throw MUST_BE_PLAYER.create();
                })
                .then(argument("dimension", DimensionArgument.dimension())
                    .executes(context -> {
                        final Type type = Type.from(context.getArgument("type", String.class).toUpperCase(Locale.ROOT));
                        final ServerLevel level = DimensionArgument.getDimension(context, "dimension");
                        return getAndReturnDistance(context, type, level);
                    })
                    .then(argument("distance", IntegerArgumentType.integer())
                        .executes(context -> {
                            final Type type = Type.from(context.getArgument("type", String.class).toUpperCase(Locale.ROOT));
                            final ServerLevel level = DimensionArgument.getDimension(context, "dimension");
                            final int distance = Math.min(context.getArgument("distance", int.class), MoonriseConstants.MAX_VIEW_DISTANCE - 3);

                            if (distance < -1 || distance == 0 || distance == 1) {
                                throw INVALID_DISTANCE.create();
                            }

                            // update the distance override that we can use later on
                            type.set(level, distance);

                            final PerWorldDistanceConfig state = level.serverLevelData.canvas$distanceConfig;
                            final int updated = Math.min(
                                (type.equals(Type.VIEW)
                                    ? state.viewDistanceOrDefault()
                                    : state.simulationDistanceOrDefault()),
                                MoonriseConstants.MAX_VIEW_DISTANCE - 3
                            );

                            switch (type) {
                                // we go straight through here because FeatureHooks hard-clamps at 32, which is technically wrong
                                // since it should abide by the MAX_VIEW_DISTANCE arg
                                case VIEW -> level.getChunkSource().chunkMap.setServerViewDistance(updated);
                                case SIMULATION ->
                                    level.getChunkSource().chunkMap.getDistanceManager().updateSimulationDistance(updated);
                            }

                            GlobalConfiguration.broadcast("Set " + type.name.toLowerCase() + " distance of level \"" + Util.getLevelName(level) + "\" to " + distance, GlobalConfiguration.INFO);
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

        Type(final Function<ServerLevel, Integer> getter, final BiConsumer<ServerLevel, Integer> setter, final String name) {
            this.getter = getter;
            this.setter = setter;
            this.name = name;
        }

        public static Type from(final String raw) throws CommandSyntaxException {
            final String lower = raw.toLowerCase();
            return switch (lower) {
                case "view", "v" -> VIEW;
                case "simulation", "sim", "s" -> SIMULATION;
                default -> throw ILLEGAL_TYPE_ARG.create();
            };
        }

        public int get(final ServerLevel level) {
            return this.getter.apply(level);
        }

        public void set(final ServerLevel level, final int dist) {
            this.setter.accept(level, dist);
        }
    }
}
