package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class SetMaxPlayersCommand implements CommandInstance {
    @Override
    public LiteralCommandNode<CommandSourceStack> register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(
            literal("setmaxplayers")
                .requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.admin.command.setmaxplayers"))
                .then(argument("count", IntegerArgumentType.integer(0))
                    .executes(context -> {
                        final Integer newSize = context.getArgument("count", Integer.class);
                        setMaxPlayers(newSize, false);
                        context.getSource().sendSystemMessage(Component.literal("Set max player count to ").append(Component.literal("" + newSize).setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA))));
                        return 1;
                    }).then(argument("save", BoolArgumentType.bool()).executes(context -> {
                        final Integer newSize = context.getArgument("count", Integer.class);
                        setMaxPlayers(newSize, BoolArgumentType.getBool(context, "save"));
                        context.getSource().sendSystemMessage(Component.literal("Set max player count to ").append(Component.literal("" + newSize).setStyle(Style.EMPTY.withColor(ChatFormatting.AQUA))));
                        return 1;
                    }))
                )
        );
    }

    public void setMaxPlayers(int max, boolean saveToDisk) {
        MinecraftServer server = MinecraftServer.getServer();
        server.server.setMaxPlayers(max); // set runtime max players
        // modify server-properties
        if (saveToDisk) {
            DedicatedServerProperties properties = ((DedicatedServer) server).getProperties();
            properties.properties.setProperty("max-players", String.valueOf(max));
            ((DedicatedServer) server).settings.forceSave();
        }
    }
}
