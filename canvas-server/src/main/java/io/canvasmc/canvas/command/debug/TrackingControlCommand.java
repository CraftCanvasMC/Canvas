package io.canvasmc.canvas.command.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.canvasmc.canvas.command.CommandInstance;
import io.canvasmc.canvas.entity.tracking.ThreadedTracker;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.literal;

public class TrackingControlCommand implements CommandInstance {

    @Override
    public LiteralCommandNode<CommandSourceStack> register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(
            literal("entitytracking").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.debug.command.entitytracking"))
                .executes(_ -> {
                    ThreadedTracker.canceled.set(!ThreadedTracker.canceled.get());
                    return 1;
                })
        );
    }
}
