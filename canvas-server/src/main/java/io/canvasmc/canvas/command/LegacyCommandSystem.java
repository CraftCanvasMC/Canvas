package io.canvasmc.canvas.command;

import java.util.HashMap;
import java.util.Map;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import org.bukkit.command.Command;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.jetbrains.annotations.NotNull;

@Deprecated(forRemoval = true)
@DefaultQualifier(NonNull.class)
public final class LegacyCommandSystem {

    private static final Map<String, Command> COMMANDS = new HashMap<>();

    private LegacyCommandSystem() {
    }

    public static void registerViaBrigadier(@NotNull CommandDispatcher<CommandSourceStack> commandDispatcher) {
        SimulationDistanceCommand.register(commandDispatcher);
        ViewDistanceCommand.register(commandDispatcher);
    }
}
