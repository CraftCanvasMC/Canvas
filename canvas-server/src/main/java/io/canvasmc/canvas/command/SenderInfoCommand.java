package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.literal;

public class SenderInfoCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("senderinfo").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.debug.command.senderinfo"))
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        player.sendSystemMessage(Component.literal("== Debug Information, for development purposes only, can change at any time =="));
                        player.sendSystemMessage(Component.literal("Owned by NearbyPlayers of level " + player.npr.get().world));
                        player.sendSystemMessage(Component.literal("tick count " + player.tickCount));
                        return 0;
                    } else {
                        context.getSource().sendFailure(Component.literal("Only a player can execute this command!"));
                        return 1;
                    }
                })
        );
    }
}
