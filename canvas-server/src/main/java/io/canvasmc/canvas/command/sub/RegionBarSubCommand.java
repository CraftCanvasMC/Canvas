package io.canvasmc.canvas.command.sub;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.canvasmc.canvas.command.SubCommand;
import io.canvasmc.canvas.world.RegionResourceBar;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;

@NullMarked
public class RegionBarSubCommand implements SubCommand {

    private static final SimpleCommandExceptionType MUST_BE_PLAYER = new SimpleCommandExceptionType(
        Component.literal("This command must be run by a valid player entity.")
    );
    private static final SimpleCommandExceptionType INVALID_PLACEMENT_ARG = new SimpleCommandExceptionType(
        Component.literal("Invalid placement: must be \"action_bar\" or \"boss_bar\".")
    );

    private static void toggleRegionBar(final CommandSourceStack source, final ServerPlayer entityPlayer, final @Nullable BarType barType) {
        if (barType == null) {
            source.sendFailure(Component.literal("Unknown bar type"));
            return;
        }
        final RegionResourceBar.DisplayManager display = switch (barType) {
            case RAM_BAR -> entityPlayer.canvas$ramBarDisplay;
            case TPS_BAR -> entityPlayer.canvas$tpsBarDisplay;
        };
        final RegionResourceBar.Entry current = display.serializeDisplay();
        final RegionResourceBar.Entry updated = new RegionResourceBar.Entry(!current.enabled(), current.placement());

        // update the display
        display.updateFromEntry(updated);

        final String message = (updated.enabled() ? "Enabled " : "Disabled ") +
            barType.name() + " for " + entityPlayer.getName().getString();
        source.sendSuccess(() -> Component.literal(message), true);
    }

    private static void setRegionBarPlacement(final CommandSourceStack source, final ServerPlayer entityPlayer,
                                              final RegionResourceBar.Placement newPlacement, final String argName, final @Nullable BarType barType) {
        if (barType == null) {
            source.sendFailure(Component.literal("Unknown bar type"));
            return;
        }
        final RegionResourceBar.DisplayManager display = switch (barType) {
            case RAM_BAR -> entityPlayer.canvas$ramBarDisplay;
            case TPS_BAR -> entityPlayer.canvas$tpsBarDisplay;
        };
        final RegionResourceBar.Entry current = display.serializeDisplay();
        final RegionResourceBar.Entry updated = new RegionResourceBar.Entry(current.enabled(), newPlacement);

        // update the display
        display.updateFromEntry(updated);

        final String message = "Set " + barType.name() + " bar placement for " + entityPlayer.getName().getString() +
            " to " + argName;
        source.sendSuccess(() -> Component.literal(message), true);
    }

    private static @Nullable BarType parse(final String str) {
        return switch (str.toLowerCase()) {
            case "tps_bar" -> BarType.TPS_BAR;
            case "ram_bar" -> BarType.RAM_BAR;
            default -> null;
        };
    }

    @Override
    public String getName() {
        return "regionbar";
    }

    @Override
    public @Nullable String getDescription() {
        return "Toggles or modifies the region bar display for one or more players.";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base, final CommandBuildContext buildContext) {
        return base
            .then(argument("type", StringArgumentType.word())
                .suggests((_, builder) -> {
                    for (final BarType val : BarType.values()) {
                        builder.suggest(val.name().toLowerCase());
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    final CommandSourceStack source = context.getSource();
                    final ServerPlayer sourcePlayer = source.getPlayer();

                    if (sourcePlayer == null || !source.isPlayer()) {
                        throw MUST_BE_PLAYER.create();
                    }

                    sourcePlayer.getBukkitEntity().taskScheduler.scheduleOrExecute((ServerPlayer entityPlayer) -> {
                        toggleRegionBar(source, entityPlayer, parse(context.getArgument("type", String.class)));
                    });

                    return Command.SINGLE_SUCCESS;
                })
                .then(argument("players", EntityArgument.players())
                    .executes(context -> {
                        final Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "players");
                        for (final ServerPlayer selected : targets) {
                            selected.getBukkitEntity().taskScheduler.scheduleOrExecute((ServerPlayer entityPlayer) -> {
                                toggleRegionBar(context.getSource(), entityPlayer, parse(context.getArgument("type", String.class)));
                            });
                        }
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(argument("placement", StringArgumentType.word())
                        .suggests((_, builder) -> {
                            builder.suggest("action_bar");
                            builder.suggest("boss_bar");
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            final CommandSourceStack contextSource = context.getSource();
                            final Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "players");
                            final String placementRaw = StringArgumentType.getString(context, "placement").toLowerCase();

                            final RegionResourceBar.Placement newPlacement =
                                switch (placementRaw.toLowerCase()) {
                                    case "action_bar" -> RegionResourceBar.Placement.ACTION_BAR;
                                    case "boss_bar" -> RegionResourceBar.Placement.BOSS_BAR;
                                    default -> throw INVALID_PLACEMENT_ARG.create();
                                };

                            for (final ServerPlayer selected : targets) {
                                selected.getBukkitEntity().taskScheduler.scheduleOrExecute((ServerPlayer entityPlayer) -> {
                                    setRegionBarPlacement(contextSource, entityPlayer, newPlacement, placementRaw, parse(context.getArgument("type", String.class)));
                                });
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
            );
    }

    private enum BarType {
        TPS_BAR,
        RAM_BAR
    }
}
