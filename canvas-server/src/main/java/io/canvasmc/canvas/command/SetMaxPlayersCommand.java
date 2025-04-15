package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SetMaxPlayersCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("setmaxplayers")
                .requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.admin.command.setmaxplayers"))
                .then(argument("count", IntegerArgumentType.integer(0)).executes(context -> {
                    MinecraftServer server = MinecraftServer.getServer();
                    final Integer newSize = context.getArgument("count", Integer.class);
                    server.server.setMaxPlayers(newSize);
                    context.getSource().sendSystemMessage(Component.literal("Set max player count to ").append(Component.literal("" + newSize).setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA))));
                    return 1;
                }))
        );
    }
}
