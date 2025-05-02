package io.canvasmc.canvas.command.debug;

import ca.spottedleaf.concurrentutil.executor.queue.PrioritisedTaskQueue;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.command.CommandInstance;
import io.canvasmc.canvas.region.ServerRegions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.literal;

public class TasksCommand implements CommandInstance {

    @Override
    public LiteralCommandNode<CommandSourceStack> register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(
            literal("polltasks").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.debug.command.polltasks"))
                .executes(context -> {
                    CommandSourceStack stack = context.getSource();
                    for (final ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
                        // level data
                        stack.sendSystemMessage(Component.literal("Level - " + level.toString()));
                        stack.sendSystemMessage(Component.literal("Global Tasks: " + level.taskQueueRegionData.globalChunkTask.size()));
                        final PrioritisedTaskQueue mainThreadExecutor = level.moonrise$getChunkTaskScheduler().mainThreadExecutor;
                        final long executedTasks = mainThreadExecutor.getTotalTasksExecuted();
                        final long scheduledTasks = mainThreadExecutor.getTotalTasksScheduled();

                        stack.sendSystemMessage(Component.literal("Main-Thread Tasks: " + (executedTasks - scheduledTasks)));
                        stack.sendSystemMessage(Component.literal("ServerChunkCache.MainThreadExecutor Tasks: " + level.chunkSource.mainThreadProcessor.size()));
                        // region data
                        if (Config.INSTANCE.ticking.enableThreadedRegionizing) {
                            stack.sendSystemMessage(Component.literal("Regions:"));
                            level.regioniser.computeForAllRegionsUnsynchronised((region) -> {
                                ServerRegions.WorldTickData tickData = region.getData().tickData;
                                stack.sendSystemMessage(Component.literal("  Total Region Tasks at " + region.getData().tickHandle + ": " + tickData.taskQueueData.size()));
                                stack.sendSystemMessage(Component.literal("  Tick Tasks: " + tickData.taskQueueData.tickTaskSize()));
                                stack.sendSystemMessage(Component.literal("  Chunk Tasks: " + tickData.taskQueueData.chunkTaskSize()));
                            });
                        }
                    }
                    return 1;
                })
        );
    }
}
