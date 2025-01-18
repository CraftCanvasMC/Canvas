package io.canvasmc.canvas.command;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import org.bukkit.command.Command;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
public final class CanvasCommands {

    private static final Map<String, Command> COMMANDS = new HashMap<>();

    private CanvasCommands() {
    }

    public static void registerCommands(final MinecraftServer server) {
        COMMANDS.forEach((s, command) -> server.server.getCommandMap().register(s, "Canvas", command));
    }
}
