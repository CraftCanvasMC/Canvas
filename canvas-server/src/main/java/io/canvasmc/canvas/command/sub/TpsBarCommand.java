package io.canvasmc.canvas.command.sub;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.world.RegionizedTpsBar;
import io.canvasmc.canvas.command.Command;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;

@NullMarked
public class TpsBarCommand implements Command {

    private static void toggleTpsBar(CommandSourceStack source, ServerPlayer player) {
        RegionizedTpsBar.DisplayManager display = player.canvas$tpsBarDisplay;
        RegionizedTpsBar.Entry current = display.serializeDisplay();
        RegionizedTpsBar.Entry updated = new RegionizedTpsBar.Entry(!current.enabled(), current.placement());
        display.updateFromEntry(updated);

        String message = (updated.enabled() ? "Enabled" : "Disabled") +
            " TPS bar for " + player.getName().getString();
        source.sendSuccess(() -> Component.literal(message), true);
    }

    private static void setTpsBarPlacement(CommandSourceStack source, ServerPlayer player,
                                           RegionizedTpsBar.Placement newPlacement, String argName) {
        RegionizedTpsBar.DisplayManager display = player.canvas$tpsBarDisplay;
        RegionizedTpsBar.Entry current = display.serializeDisplay();
        RegionizedTpsBar.Entry updated = new RegionizedTpsBar.Entry(current.enabled(), newPlacement);
        display.updateFromEntry(updated);

        String message = "Set TPS bar placement for " + player.getName().getString() +
            " to " + argName;
        source.sendSuccess(() -> Component.literal(message), true);
    }

    @Override
    public String getName() {
        return "tpsbar";
    }

    @Override
    public @Nullable String getDescription() {
        return "Toggles or modifies the TPS display bar for one or more players.";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(LiteralArgumentBuilder<CommandSourceStack> base) {
        if (!Config.INSTANCE.enableTpsBar) {
            return base.executes(ctx -> {
                ctx.getSource().sendFailure(Component.literal("TPS bar is disabled in the config."));
                return 0;
            });
        }

        return base
            .executes(ctx -> {
                CommandSourceStack source = ctx.getSource();
                ServerPlayer player = source.getPlayer();

                if (player == null || !source.isPlayer()) {
                    source.sendFailure(Component.literal("This command must be run by a valid player entity."));
                    return 0;
                }

                toggleTpsBar(source, player);
                return 1;
            })

            .then(argument("players", EntityArgument.players())
                .executes(ctx -> {
                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                    for (ServerPlayer player : players) {
                        toggleTpsBar(ctx.getSource(), player);
                    }
                    return 1;
                })

                .then(argument("placement", StringArgumentType.word())
                    .suggests((context, builder) -> {
                        builder.suggest("action_bar");
                        builder.suggest("boss_bar");
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        CommandSourceStack source = ctx.getSource();
                        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                        String placementArg = StringArgumentType.getString(ctx, "placement").toLowerCase();

                        RegionizedTpsBar.Placement newPlacement;
                        switch (placementArg) {
                            case "action_bar" -> newPlacement = RegionizedTpsBar.Placement.ACTION_BAR;
                            case "boss_bar" -> newPlacement = RegionizedTpsBar.Placement.BOSS_BAR;
                            default -> {
                                source.sendFailure(Component.literal("Invalid placement: must be 'action_bar' or 'boss_bar'."));
                                return 0;
                            }
                        }

                        for (ServerPlayer player : players) {
                            setTpsBarPlacement(source, player, newPlacement, placementArg);
                        }

                        return 1;
                    })
                )
            );
    }
}
