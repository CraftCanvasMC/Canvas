package io.canvasmc.canvas.command;

import com.mojang.brigadier.CommandDispatcher;
import io.canvasmc.canvas.entity.tracking.ThreadedTracker;
import net.minecraft.commands.CommandSourceStack;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.literal;

public class TrackingControlCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("entitytracking").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.debug.command.entitytracking"))
                .executes(_ -> {
                    ThreadedTracker.canceled.set(!ThreadedTracker.canceled.get());
                    return 1;
                })
        );
    }
}
