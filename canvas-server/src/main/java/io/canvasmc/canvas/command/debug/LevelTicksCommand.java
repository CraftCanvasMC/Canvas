package io.canvasmc.canvas.command.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.command.CommandInstance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ticks.LevelTicks;
import org.jetbrains.annotations.NotNull;

import static net.minecraft.commands.Commands.literal;

public class LevelTicksCommand implements CommandInstance {

    @Override
    public LiteralCommandNode<CommandSourceStack> register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        return dispatcher.register(
            literal("levelticks").requires(commandSourceStack -> commandSourceStack.hasPermission(3, "canvas.debug.command.levelticks"))
                .executes(context -> {
                    CommandSourceStack stack = context.getSource();
                    if (!stack.isPlayer()) return 0;
                    for (final ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
                        stack.sendSystemMessage(Component.literal("World:" + level.dimension().location()));
                        printLevelTicks(level.levelTickData.getFluidLevelTicks(), stack);
                        printLevelTicks(level.levelTickData.getBlockLevelTicks(), stack);
                        if (Config.INSTANCE.ticking.enableThreadedRegionizing) {
                            stack.sendSystemMessage(Component.literal("Regions:"));
                            level.regioniser.computeForAllRegionsUnsynchronised((region) -> {
                                stack.sendSystemMessage(Component.literal(region.getData().tickHandle.toString()));
                                printLevelTicks(region.getData().tickData.getFluidLevelTicks(), stack);
                                printLevelTicks(region.getData().tickData.getBlockLevelTicks(), stack);
                            });
                        }
                    }
                    return 1;
                })
        );
    }

    private void printLevelTicks(LevelTicks<?> levelTicks, CommandSourceStack stack) {
        stack.sendSystemMessage(Component.literal("====LevelTicks[block:" + levelTicks.isBlock + ",world:" + levelTicks.isWorldRegion + "]===="));
        ServerPlayer player = stack.getPlayer();
        if (player == null) throw new IllegalStateException("Player was supposed to be non-null");
        long packed = player.chunkPosition().longKey;
        stack.sendSystemMessage(Component.literal("Owns chunk: " + levelTicks.allContainers.containsKey(packed)));
    }
}
