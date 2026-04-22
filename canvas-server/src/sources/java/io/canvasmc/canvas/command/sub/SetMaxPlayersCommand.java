package io.canvasmc.canvas.command.sub;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.command.Command;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static net.minecraft.commands.Commands.argument;

@NullMarked
public class SetMaxPlayersCommand implements Command {

    private static int execute(int newSize, boolean persist) {
        setMaxPlayers(newSize, persist);
        Config.GLOBAL_BROADCAST.accept("Set max player count to " + newSize);
        return 1;
    }

    private static void setMaxPlayers(int max, boolean saveToDisk) {
        MinecraftServer server = MinecraftServer.getServer();
        server.server.setMaxPlayers(max);

        if (saveToDisk && server instanceof DedicatedServer dedicatedServer) {
            DedicatedServerProperties properties = dedicatedServer.getProperties();
            properties.properties.setProperty("max-players", String.valueOf(max));
            dedicatedServer.settings.forceSave();
        }
    }

    @Override
    public String getName() {
        return "setmaxplayers";
    }

    @Override
    public @Nullable String getDescription() {
        return "Sets the maximum number of players allowed on this server.";
    }

    @Override
    public LiteralArgumentBuilder<CommandSourceStack> construct(LiteralArgumentBuilder<CommandSourceStack> base) {
        return base.then(argument("count", IntegerArgumentType.integer(0))
            .executes(ctx -> execute(
                IntegerArgumentType.getInteger(ctx, "count"), false))
            .then(argument("persist", BoolArgumentType.bool())
                .executes(ctx -> execute(
                    IntegerArgumentType.getInteger(ctx, "count"),
                    BoolArgumentType.getBool(ctx, "persist")))));
    }
}
