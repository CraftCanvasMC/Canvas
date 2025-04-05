package io.canvasmc.canvas.command;

import ca.spottedleaf.moonrise.common.util.MoonriseCommon;
import com.ishland.flowsched.executor.Task;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentLinkedQueue;

import static net.minecraft.commands.Commands.literal;

public class PriorityCommand {
    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            literal("chunkpriority")
                .requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.debug.command.chunkpriority"))
                .executes(context -> {
                    CommandSourceStack stack = context.getSource();
                    for (int i = 0; i < MoonriseCommon.WORKER_POOL.globalWorkQueue.priorities.length; i++) {
                        ConcurrentLinkedQueue<Task> tasks = MoonriseCommon.WORKER_POOL.globalWorkQueue.priorities[i];
                        if (tasks == null) continue;
                        stack.sendSystemMessage(Component.literal("Priority " + i + " count: " + tasks.size()));
                    }
                    return 1;
                })
        );
    }
}
