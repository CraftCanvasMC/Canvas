package io.canvasmc.canvas.entity.tnt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import io.canvasmc.canvas.region.ServerRegions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class TNTMergeManager {
    public static final Map<ServerRegions.WorldTickData, AtomicInteger> tntCount = new ConcurrentHashMap<>();

    public static void onEntityUnload(@NotNull Entity entity) {
        if (entity.getType() == EntityType.TNT)
            tntCount.computeIfAbsent(ServerRegions.getTickData(entity.level().level()), (_) -> new AtomicInteger(0)).decrementAndGet();
    }

    public static void onEntityLoad(@NotNull Entity entity) {
        if (entity.getType() == EntityType.TNT)
            tntCount.computeIfAbsent(ServerRegions.getTickData(entity.level().level()), (_) -> new AtomicInteger(0)).incrementAndGet();
    }
}
