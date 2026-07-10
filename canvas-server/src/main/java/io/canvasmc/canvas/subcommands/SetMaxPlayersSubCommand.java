package io.canvasmc.canvas.subcommands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.GlobalConfiguration;
import io.canvasmc.canvas.commands.SubCommand;
import io.papermc.paper.threadedregions.RegionizedServer;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;

import static net.minecraft.commands.Commands.argument;

public class SetMaxPlayersSubCommand implements SubCommand {

    @Override
    public String getDescription() {
        return "Sets the maximum number of players allowed on this server.";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(final LiteralArgumentBuilder<CommandSourceStack> base, final CommandBuildContext buildContext) {
        return base.then(argument("count", IntegerArgumentType.integer(0))
            .executes(context -> execute(
                IntegerArgumentType.getInteger(context, "count"), false)
            ).then(argument("persist", BoolArgumentType.bool())
                .executes(context -> execute(
                    IntegerArgumentType.getInteger(context, "count"),
                    BoolArgumentType.getBool(context, "persist")
                ))));
    }

    @Override
    public String getName() {
        return "setmaxplayers";
    }

    private static int execute(final int newSize, final boolean persist) {
        RegionizedServer.getInstance().scheduleToOrExecute(() -> {
            setMaxPlayers(newSize, persist);
            GlobalConfiguration.broadcast("Set max player count to " + newSize, GlobalConfiguration.INFO);
        });
        return Command.SINGLE_SUCCESS;
    }

    private static void setMaxPlayers(final int max, final boolean saveToDisk) {
        final MinecraftServer server = MinecraftServer.getServer();
        server.server.setMaxPlayers(max);

        if (saveToDisk && server instanceof DedicatedServer dedicatedServer) {
            DedicatedServerProperties properties = dedicatedServer.getProperties();
            properties.properties.setProperty("max-players", String.valueOf(max));
            dedicatedServer.settings.forceSave();
        }
    }
}
