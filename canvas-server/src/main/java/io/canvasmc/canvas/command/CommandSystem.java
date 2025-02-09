package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.jetbrains.annotations.NotNull;

public class CommandSystem {
    @NotNull
    private final CommandDispatcher<CommandSourceStack> commandDispatcher;

    public CommandSystem(@NotNull CommandDispatcher<CommandSourceStack> commandDispatcher) {
        this.commandDispatcher = commandDispatcher;
    }

    public static void registerCommands(@NotNull CommandDispatcher<CommandSourceStack> commandDispatcher) {
        CommandSystem system = new CommandSystem(commandDispatcher);
        system.registerCommand("threadedtick", new ThreadedTickCommand());
        LegacyCommandSystem.registerViaBrigadier(commandDispatcher);
    }

    public void registerCommand(String name, @NotNull AbstractCommand command) {
        this.commandDispatcher.register(command.build(Commands.literal(name)));
    }
}
