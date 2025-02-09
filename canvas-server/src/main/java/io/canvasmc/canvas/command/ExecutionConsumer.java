package io.canvasmc.canvas.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;

@FunctionalInterface
public interface ExecutionConsumer {
    void run(CommandContext<CommandSourceStack> context, CommandSourceStack source) throws CommandSyntaxException;
}
