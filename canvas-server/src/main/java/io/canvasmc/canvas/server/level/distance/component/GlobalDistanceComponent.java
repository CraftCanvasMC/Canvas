package io.canvasmc.canvas.server.level.distance.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;

public class GlobalDistanceComponent {
    public int globalViewDistance = 0;
    public int globalSimulationDistance = 0;

    public void writeToNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("GlobalViewDistance", globalViewDistance);
        tag.putInt("GlobalSimulationDistance", globalSimulationDistance);
    }
}
