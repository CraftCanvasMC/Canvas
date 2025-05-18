package io.canvasmc.canvas.command.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.canvasmc.canvas.command.CommandInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import java.util.Objects;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class RandomTeleportCommand implements CommandInstance {

    @Override
    public LiteralCommandNode<CommandSourceStack> register(@NotNull final CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(
            literal("randomteleport").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.debug.command.randomteleport"))
                .then(argument("xbounds", IntegerArgumentType.integer(1)).then(argument("zbounds", IntegerArgumentType.integer(1)).executes(context -> {
                    int xBounds = IntegerArgumentType.getInteger(context, "xbounds");
                    int zBounds = IntegerArgumentType.getInteger(context, "zbounds");
                    if (!context.getSource().isPlayer()) {
                        context.getSource().sendFailure(Component.literal("Must be a player sender"));
                        return 0;
                    }
                    ServerPlayer player = context.getSource().getPlayer();
                    RandomSource randomSource = Objects.requireNonNull(player, "must be run by a player").random;
                    player.teleportTo(randomSource.nextInt(xBounds), player.position().y, randomSource.nextInt(zBounds));
                    return 1;
                })))
        );
    }
}
