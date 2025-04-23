package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

public interface CommandInstance {
    LiteralCommandNode<CommandSourceStack> register(@NotNull CommandDispatcher<CommandSourceStack> commandDispatcher);
}
