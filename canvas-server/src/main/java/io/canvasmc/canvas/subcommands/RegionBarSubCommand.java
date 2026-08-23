package io.canvasmc.canvas.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.canvasmc.canvas.commands.SubCommand;
import io.canvasmc.canvas.world.RegionResourceBar;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;

public class RegionBarSubCommand implements SubCommand {

    private static final SimpleCommandExceptionType INVALID_PLACEMENT_ARG = new SimpleCommandExceptionType(
        Component.literal("Invalid placement: must be \"action_bar\" or \"boss_bar\".")
    );
    private static final SimpleCommandExceptionType INVALID_BAR_TYPE = new SimpleCommandExceptionType(
        Component.literal("Invalid bar type: must be \"tps_bar\" or \"ram_bar\".")
    );

    @Override
    public String getDescription() {
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
                    final ServerPlayer sourcePlayer = source.getPlayerOrException();
                    final BarType barType = parse(StringArgumentType.getString(context, "type"));

                    sourcePlayer.getBukkitEntity().taskScheduler.scheduleOrExecute((ServerPlayer entityPlayer) -> {
                        toggleRegionBar(source, entityPlayer, barType);
                    });

                    return Command.SINGLE_SUCCESS;
                })
                .then(argument("players", EntityArgument.players())
                    .executes(context -> {
                        final Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "players");
                        final BarType barType = parse(StringArgumentType.getString(context, "type"));

                        for (final ServerPlayer selected : targets) {
                            selected.getBukkitEntity().taskScheduler.scheduleOrExecute((ServerPlayer entityPlayer) -> {
                                toggleRegionBar(context.getSource(), entityPlayer, barType);
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
                            final BarType barType = parse(StringArgumentType.getString(context, "type"));

                            final RegionResourceBar.Placement newPlacement =
                                switch (placementRaw.toLowerCase()) {
                                    case "action_bar" -> RegionResourceBar.Placement.ACTION_BAR;
                                    case "boss_bar" -> RegionResourceBar.Placement.BOSS_BAR;
                                    default -> throw INVALID_PLACEMENT_ARG.create();
                                };

                            for (final ServerPlayer selected : targets) {
                                selected.getBukkitEntity().taskScheduler.scheduleOrExecute((ServerPlayer entityPlayer) -> {
                                    setRegionBarPlacement(contextSource, entityPlayer, newPlacement, placementRaw, barType);
                                });
                            }

                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
            );
    }

    @Override
    public String getName() {
        return "regionbar";
    }

    private static BarType parse(final String str) throws CommandSyntaxException {
        return switch (str.toLowerCase()) {
            case "tps_bar" -> BarType.TPS_BAR;
            case "ram_bar" -> BarType.RAM_BAR;
            default -> throw INVALID_BAR_TYPE.create();
        };
    }

    private static void toggleRegionBar(final CommandSourceStack source, final ServerPlayer entityPlayer, final BarType barType) {
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

    private static void setRegionBarPlacement(
        final CommandSourceStack source,
        final ServerPlayer entityPlayer,
        final RegionResourceBar.Placement newPlacement,
        final String argName,
        final BarType barType
    ) {
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

    private enum BarType {
        TPS_BAR,
        RAM_BAR
    }
}
