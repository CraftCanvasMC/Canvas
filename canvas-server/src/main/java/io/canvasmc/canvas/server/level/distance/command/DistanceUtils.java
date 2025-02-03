package io.canvasmc.canvas.server.level.distance.command;

import io.canvasmc.canvas.server.level.distance.WorldSpecificViewDistancePersistentState;
import io.canvasmc.canvas.server.level.distance.component.GlobalDistanceComponent;
import io.canvasmc.canvas.server.level.distance.component.WorldSpecificViewDistanceComponents;
import net.minecraft.server.level.ServerLevel;

public class DistanceUtils {
    public static int resolveViewDistance(ServerLevel world) {
        WorldSpecificViewDistancePersistentState state = WorldSpecificViewDistancePersistentState.getFrom(world);
        GlobalDistanceComponent globalDist = WorldSpecificViewDistanceComponents.GLOBAL_DISTANCES.get(world.getServer().getWorldData());

        int viewDistance = state.getLocalViewDistance();

        if (viewDistance != 0)
            return viewDistance;

        viewDistance = globalDist.globalViewDistance;

        if (viewDistance != 0)
            return viewDistance;

        return world.getServer().getPlayerList().getViewDistance() + 1;
    }

    public static int resolveSimulationDistance(ServerLevel world) {
        WorldSpecificViewDistancePersistentState state = WorldSpecificViewDistancePersistentState.getFrom(world);
        GlobalDistanceComponent globalDist = WorldSpecificViewDistanceComponents.GLOBAL_DISTANCES.get(world.getServer().getWorldData());

        int simDistance = state.getLocalSimulationDistance();

        if (simDistance != 0)
            return simDistance;

        simDistance = globalDist.globalSimulationDistance;

        if (simDistance != 0)
            return simDistance;

        return world.getServer().getPlayerList().getSimulationDistance() + 1;
    }
}
