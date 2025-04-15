package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
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
                    server.server.setMaxPlayers(context.getArgument("count", Integer.class));
                    return 1;
                }))
        );
    }
}
