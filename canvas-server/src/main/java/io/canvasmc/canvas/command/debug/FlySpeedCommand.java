package io.canvasmc.canvas.command.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.canvasmc.canvas.command.CommandInstance;
import java.util.Objects;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class FlySpeedCommand implements CommandInstance {

    @Override
    public LiteralCommandNode<CommandSourceStack> register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(
            literal("flyspeed").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.debug.command.flyspeed"))
                .then(argument("speed", FloatArgumentType.floatArg(0, 100)).executes(context -> {
                    float speedPercentage = FloatArgumentType.getFloat(context, "speed");
                    if (context.getSource().isPlayer()) {
                        ServerPlayer player = context.getSource().getPlayer();
                        Objects.requireNonNull(player, "player cannot be null").getBukkitEntity().setFlySpeed(speedPercentage / 100);
                    } else context.getSource().sendFailure(Component.literal("Only a player can execute this command!"));
                    return 1;
                }))
        );
    }
}
