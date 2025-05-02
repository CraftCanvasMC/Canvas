package io.canvasmc.canvas.command.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.canvasmc.canvas.command.CommandInstance;
import io.canvasmc.canvas.region.ChunkRegion;
import io.canvasmc.canvas.scheduler.TickScheduler;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.concurrent.TimeUnit;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import static io.canvasmc.canvas.server.ThreadedServer.LOGGER;
import static net.minecraft.commands.Commands.literal;

public class SenderInfoCommand implements CommandInstance {

    @Override
    public LiteralCommandNode<CommandSourceStack> register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(
            literal("senderinfo").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.debug.command.senderinfo"))
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        player.sendSystemMessage(Component.literal("== Debug Information, for development purposes only, can change at any time =="));
                        player.sendSystemMessage(Component.literal("Owned by NearbyPlayers of level " + player.npr.get().world));
                        player.sendSystemMessage(Component.literal("tick count " + player.tickCount));
                        ChunkHolder holder = player.serverLevel().getChunk(player.chunkPosition().x, player.chunkPosition().z).chunkAndHolder.holder();
                        for (final ShortSet shorts : holder.changedBlocksPerSection) {
                            player.sendSystemMessage(Component.literal("In chunk found changed shorts: " + shorts));
                        }
                        player.sendSystemMessage(Component.literal("Last chunk broadcast: " + TimeUnit.SECONDS.convert(Util.getNanos() - holder.lastTickNanos, TimeUnit.NANOSECONDS) + "s ago"));
                        player.sendSystemMessage(Component.literal("attempting broadcast..."));
                        holder.broadcastChanges(player.serverLevel().getChunk(player.chunkPosition().x, player.chunkPosition().z).chunkAndHolder.chunk());
                        player.sendSystemMessage(Component.literal("Current chunk status: " + holder.newChunkHolder.getChunkStatus()));
                        int queuedTasks = 0;
                        for (final TickScheduler.FullTick<?> fullTick : TickScheduler.FullTick.ALL_REGISTERED) {
                            if (!fullTick.tasks.isEmpty()) {
                                LOGGER.error("{} has {} full tick tasks scheduled", fullTick, fullTick.tasks.size());
                            }
                            queuedTasks += fullTick.tasks.size();
                            if (fullTick instanceof ServerLevel level) {
                                if (!level.taskQueueRegionData.globalChunkTask.isEmpty()) {
                                    LOGGER.error("{} has {} global chunk tasks scheduled", fullTick, level.taskQueueRegionData.globalChunkTask.size());
                                }
                                queuedTasks += level.taskQueueRegionData.globalChunkTask.size();
                                if (level.getChunkSource().mainThreadProcessor.getPendingTasksCount() > 0) {
                                    LOGGER.error("{} has {} tasks pending in its main thread processor", fullTick, level.getChunkSource().mainThreadProcessor.getPendingTasksCount());
                                }
                                queuedTasks += level.getChunkSource().mainThreadProcessor.getPendingTasksCount();
                                // public boolean hasNoScheduledTasks() {
                                //         final long executedTasks = this.executedTasks.get();
                                //         final long scheduledTasks = this.scheduledTasks.get();
                                //
                                //         return executedTasks == scheduledTasks;
                                //     }
                                if (((int) (level.moonrise$getChunkTaskScheduler().mainThreadExecutor.getTotalTasksScheduled() - level.moonrise$getChunkTaskScheduler().mainThreadExecutor.getTotalTasksExecuted())) > 0) {
                                    LOGGER.error("{} has {} tasks pending in its chunk task scheduler", fullTick, (int) (level.moonrise$getChunkTaskScheduler().mainThreadExecutor.getTotalTasksScheduled() - level.moonrise$getChunkTaskScheduler().mainThreadExecutor.getTotalTasksExecuted()));
                                }
                                queuedTasks += (int) (level.moonrise$getChunkTaskScheduler().mainThreadExecutor.getTotalTasksScheduled() - level.moonrise$getChunkTaskScheduler().mainThreadExecutor.getTotalTasksExecuted());
                            }
                            if (fullTick instanceof ChunkRegion region) {
                                if (region.region.getData().tickData.taskQueueData.size() > 0) {
                                    LOGGER.error("{} has {} tasks queued in its task queue", region, region.region.getData().tickData.taskQueueData.size());
                                    if (region.owner != null) {
                                        for (final StackTraceElement stackTraceElement : region.owner.getStackTrace()) {
                                            LOGGER.error("\t\t{}", stackTraceElement);
                                        }
                                    }
                                    if (region.world.owner != null) {
                                        for (final StackTraceElement stackTraceElement : region.world.owner.getStackTrace()) {
                                            LOGGER.error("\t\t{}", stackTraceElement);
                                        }
                                    }
                                }
                                queuedTasks += region.region.getData().tickData.taskQueueData.size();
                            }
                        }
                        player.sendSystemMessage(Component.literal("Dumped current pending tasks to console with further debug information."));
                        /* Connection connection = player.connection.connection;
                        ChunkPos chunkPos = player.chunkPosition();
                        ThreadedRegionizer.ThreadedRegion<ServerRegions.TickRegionData, ServerRegions.TickRegionSectionData> region = player.serverLevel().regioniser.getRegionAtUnsynchronised(chunkPos.x, chunkPos.z);
                        if (region == null) return 0;
                        if (!region.getData().tickData.activeConnections.contains(connection)) {
                            MinecraftServer.LOGGER.error("player {} doesn't own connection", player);
                        } else {
                            MinecraftServer.LOGGER.info("player {} has connection", player);
                        } */
                        return 0;
                    } else {
                        context.getSource().sendFailure(Component.literal("Only a player can execute this command!"));
                        return 1;
                    }
                })
        );
    }
}
