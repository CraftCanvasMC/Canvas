package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.RegionizedTpsBar;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class TpsBarCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        if (!Config.INSTANCE.enableTpsBar) return;
        dispatcher.register(
            literal("tpsbar")
                .requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.command.tpsbar"))
                .executes((context) -> {
                    CommandSourceStack source = context.getSource();
                    final ServerPlayer player = source.getPlayer();
                    if (player == null || !source.isPlayer()) {
                        source.sendFailure(Component.literal("This must be run by a valid player entity"));
                        return 0;
                    }
                    player.setTpsBarEnabled(!player.localEntry.enabled());
                    context.getSource().sendSuccess(() -> Component.literal(
                        (player.localEntry.enabled() ? "Enabled" : "Disabled") +
                            " player " + player.getName().getString() + " tpsbar"), true);
                    return 1;
                }).then(argument("players", EntityArgument.players())
                    .executes((context) -> {
                        final Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
                        for (final ServerPlayer player : players) {
                            player.setTpsBarEnabled(!player.localEntry.enabled());
                            context.getSource().sendSuccess(() -> Component.literal(
                                (player.localEntry.enabled() ? "Enabled" : "Disabled") +
                                    " player " + player.getName().getString() + " tpsbar"), true);
                        }
                        return 1;
                    }).then(argument("placement", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("action_bar");
                            builder.suggest("boss_bar");
                            return builder.buildFuture();
                        })
                        .executes((context) -> {
                            CommandSourceStack source = context.getSource();
                            final Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
                            final String placement = StringArgumentType.getString(context, "placement").toLowerCase();
                            for (final ServerPlayer player : players) {
                                switch (placement) {
                                    case "action_bar" -> player.setTpsBarPlacement(RegionizedTpsBar.Placement.ACTION_BAR);
                                    case "boss_bar" -> player.setTpsBarPlacement(RegionizedTpsBar.Placement.BOSS_BAR);
                                    default -> {
                                        source.sendFailure(Component.literal("Not valid placement"));
                                        return 0;
                                    }
                                }
                                source.sendSuccess(() -> Component.literal("Set player " + player.getName().getString() + " tpsbar placement to " + placement), true);
                            }
                            return 1;
                        })
                    )
                )
        );
    }
}
