package io.canvasmc.canvas.server.level.distance.component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.storage.WorldData;

public class WorldSpecificViewDistanceComponents {
    public static final Map<WorldData, GlobalDistanceComponent> GLOBAL_DISTANCES = new ConcurrentHashMap<>();
}
