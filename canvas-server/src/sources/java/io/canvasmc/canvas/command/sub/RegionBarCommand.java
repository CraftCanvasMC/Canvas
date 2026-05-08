package io.canvasmc.canvas.command.sub;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.command.Command;
import io.canvasmc.canvas.world.RegionResourceBar;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;

@NullMarked
public class RegionBarCommand implements Command {

    private static void toggleRegionBar(final CommandSourceStack source, final ServerPlayer player, final @Nullable BarType barType) {
        if (barType == null) {
            source.sendFailure(Component.literal("Unknown bar type"));
            return;
        }
        RegionResourceBar.DisplayManager display = switch (barType) {
            case RAM_BAR -> player.canvas$ramBarDisplay;
            case TPS_BAR -> player.canvas$tpsBarDisplay;
        };
        RegionResourceBar.Entry current = display.serializeDisplay();
        RegionResourceBar.Entry updated = new RegionResourceBar.Entry(!current.enabled(), current.placement());
        display.updateFromEntry(updated);

        String message = (updated.enabled() ? "Enabled " : "Disabled ") +
            barType.name() + " for " + player.getName().getString();
        source.sendSuccess(() -> Component.literal(message), true);
    }

    private static void setRegionBarPlacement(final CommandSourceStack source, final ServerPlayer player,
                                              final RegionResourceBar.Placement newPlacement, final String argName, final @Nullable BarType barType) {
        if (barType == null) {
            source.sendFailure(Component.literal("Unknown bar type"));
            return;
        }
        RegionResourceBar.DisplayManager display = switch (barType) {
            case RAM_BAR -> player.canvas$ramBarDisplay;
            case TPS_BAR -> player.canvas$tpsBarDisplay;
        };
        RegionResourceBar.Entry current = display.serializeDisplay();
        RegionResourceBar.Entry updated = new RegionResourceBar.Entry(current.enabled(), newPlacement);
        display.updateFromEntry(updated);

        String message = "Set " + barType.name() + " bar placement for " + player.getName().getString() +
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
    public LiteralArgumentBuilder<CommandSourceStack> construct(LiteralArgumentBuilder<CommandSourceStack> base) {
        return base

            .then(argument("type", StringArgumentType.word())
                .suggests((_, builder) -> {
                    for (final BarType val : BarType.values()) {
                        builder.suggest(val.name().toLowerCase());
                    }
                    return builder.buildFuture();
                })
                .executes(ctx -> {
                    CommandSourceStack source = ctx.getSource();
                    ServerPlayer player = source.getPlayer();

                    if (player == null || !source.isPlayer()) {
                        source.sendFailure(Component.literal("This command must be run by a valid player entity."));
                        return 0;
                    }

                    player.getBukkitEntity().taskScheduler.scheduleOrExecute((ServerPlayer entityPlayer) -> {
                        toggleRegionBar(source, entityPlayer, parse(ctx.getArgument("type", String.class)));
                    });
                    return 1;
                })

                .then(argument("players", EntityArgument.players())
                    .executes(ctx -> {
                        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                        for (ServerPlayer player : players) {
                            player.getBukkitEntity().taskScheduler.scheduleOrExecute((ServerPlayer entityPlayer) -> {
                                toggleRegionBar(ctx.getSource(), entityPlayer, parse(ctx.getArgument("type", String.class)));
                            });
                        }
                        return 1;
                    })

                    .then(argument("placement", StringArgumentType.word())
                        .suggests((_, builder) -> {
                            builder.suggest("action_bar");
                            builder.suggest("boss_bar");
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            CommandSourceStack source = ctx.getSource();
                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                            String placementArg = StringArgumentType.getString(ctx, "placement").toLowerCase();

                            RegionResourceBar.Placement newPlacement;
                            switch (placementArg) {
                                case "action_bar" -> newPlacement = RegionResourceBar.Placement.ACTION_BAR;
                                case "boss_bar" -> newPlacement = RegionResourceBar.Placement.BOSS_BAR;
                                default -> {
                                    source.sendFailure(Component.literal("Invalid placement: must be 'action_bar' or 'boss_bar'."));
                                    return 0;
                                }
                            }

                            for (ServerPlayer player : players) {
                                player.getBukkitEntity().taskScheduler.scheduleOrExecute((ServerPlayer entityPlayer) -> {
                                    setRegionBarPlacement(source, entityPlayer, newPlacement, placementArg, parse(ctx.getArgument("type", String.class)));
                                });
                            }

                            return 1;
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
