package io.canvasmc.canvas.server.level.distance.command;

import io.canvasmc.canvas.server.level.distance.WorldSpecificViewDistancePersistentState;
import net.minecraft.server.level.ServerLevel;

public class DistanceUtils {
    public static int resolveViewDistance(ServerLevel world) {
        WorldSpecificViewDistancePersistentState state = WorldSpecificViewDistancePersistentState.getFrom(world);

        int viewDistance = state.getLocalViewDistance();

        if (viewDistance != 0)
            return viewDistance;

        viewDistance = world.server.settings.getProperties().viewDistance;

        if (viewDistance != 0)
            return viewDistance;

        return world.getServer().getPlayerList().getViewDistance() + 1;
    }

    public static int resolveSimulationDistance(ServerLevel world) {
        WorldSpecificViewDistancePersistentState state = WorldSpecificViewDistancePersistentState.getFrom(world);

        int simDistance = state.getLocalSimulationDistance();

        if (simDistance != 0)
            return simDistance;

        simDistance = world.server.settings.getProperties().simulationDistance;

        if (simDistance != 0)
            return simDistance;

        return world.getServer().getPlayerList().getSimulationDistance() + 1;
    }
}
