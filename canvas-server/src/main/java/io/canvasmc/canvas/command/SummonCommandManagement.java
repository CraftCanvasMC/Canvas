package io.canvasmc.canvas.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.canvasmc.canvas.Config;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

public class SummonCommandManagement {
    private final Queue<Runnable> batches = new ConcurrentLinkedQueue<>();
    private final AtomicInteger count = new AtomicInteger(0);

    public void batch(CommandSourceStack source, Holder.Reference<EntityType<?>> entityType, Vec3 pos, CompoundTag nbt, boolean initialize) {
        count.incrementAndGet();
        batches.add(() -> {
            try {
                SummonCommand.spawnEntity(source, entityType, pos, nbt, initialize, false);
            } catch (CommandSyntaxException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void tickBatch() {
        int batchSize = Config.INSTANCE.summonCommandBatchCount;
        for (int i = 0; i < batchSize && count.get() > 0; i++) {
            Runnable task = batches.poll();
            if (task != null) {
                task.run();
                count.decrementAndGet();
            } else {
                MinecraftServer.LOGGER.error("Warning: Task count and queue size mismatch!");
                count.set(0);
                break;
            }
        }
    }

}
