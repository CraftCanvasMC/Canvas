package io.canvasmc.canvas.entity.tnt;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

public class TNTMergeManager {
    public static final AtomicInteger tntCount = new AtomicInteger(0);

    public static void onEntityUnload(@NotNull Entity entity) {
        if (entity.getType() == EntityType.TNT) tntCount.decrementAndGet();
    }

    public static void onEntityLoad(@NotNull Entity entity) {
        if (entity.getType() == EntityType.TNT) tntCount.incrementAndGet();
    }
}
