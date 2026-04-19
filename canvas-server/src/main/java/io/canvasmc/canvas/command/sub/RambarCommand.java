package io.canvasmc.canvas.command.sub;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.command.Command;
import io.canvasmc.canvas.world.RegionizedRamBar;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;

@NullMarked
public class RambarCommand implements Command {

    private static void toggleRamBar(final CommandSourceStack source, final ServerPlayer player) {
        final RegionizedRamBar.DisplayManager display = player.canvas$ramBarDisplay;
        final RegionizedRamBar.Entry current = display.serializeDisplay();
        final RegionizedRamBar.Entry updated = new RegionizedRamBar.Entry(!current.enabled(), current.placement());
        display.updateFromEntry(updated);

        final String message = (updated.enabled() ? "Enabled" : "Disabled") +
            " RAM bar for " + player.getName().getString();
        source.sendSuccess(() -> Component.literal(message), true);
    }

    private static void setRamBarPlacement(final CommandSourceStack source, final ServerPlayer player,
                                           final RegionizedRamBar.Placement newPlacement, final String argName) {
        final RegionizedRamBar.DisplayManager display = player.canvas$ramBarDisplay;
        final RegionizedRamBar.Entry current = display.serializeDisplay();
        final RegionizedRamBar.Entry updated = new RegionizedRamBar.Entry(current.enabled(), newPlacement);
        display.updateFromEntry(updated);

        final String message = "Set RAM bar placement for " + player.getName().getString() +
            " to " + argName;
        source.sendSuccess(() -> Component.literal(message), true);
    }

    @Override
    public String getName() {
        return "rambar";
    }

    @Override
    public @Nullable String getDescription() {
        return "Toggles or modifies the RAM display bar for one or more players.";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base) {
        if (!Config.INSTANCE.enableRamBar) {
            return base.executes(ctx -> {
                ctx.getSource().sendFailure(Component.literal("RAM bar is disabled in the config."));
                return 0;
            });
        }

        return base
            .executes(ctx -> {
                final CommandSourceStack source = ctx.getSource();
                final ServerPlayer player = source.getPlayer();

                if (player == null || !source.isPlayer()) {
                    source.sendFailure(Component.literal("This command must be run by a valid player entity."));
                    return 0;
                }

                toggleRamBar(source, player);
                return 1;
            })
            .then(argument("players", EntityArgument.players())
                .executes(ctx -> {
                    final Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                    for (final ServerPlayer player : players) {
                        toggleRamBar(ctx.getSource(), player);
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
                        final CommandSourceStack source = ctx.getSource();
                        final Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
                        final String placementArg = StringArgumentType.getString(ctx, "placement").toLowerCase();

                        final RegionizedRamBar.Placement newPlacement;
                        switch (placementArg) {
                            case "action_bar" -> newPlacement = RegionizedRamBar.Placement.ACTION_BAR;
                            case "boss_bar" -> newPlacement = RegionizedRamBar.Placement.BOSS_BAR;
                            default -> {
                                source.sendFailure(Component.literal("Invalid placement: must be 'action_bar' or 'boss_bar'."));
                                return 0;
                            }
                        }

                        for (final ServerPlayer player : players) {
                            setRamBarPlacement(source, player, newPlacement, placementArg);
                        }

                        return 1;
                    })
                )
            );
    }
}

