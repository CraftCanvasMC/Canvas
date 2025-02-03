package io.canvasmc.canvas.server.level.distance;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;

public class WorldSpecificViewDistancePersistentState extends SavedData {
    public static final String ID = "worldspecificviewdistance";
    public static final SavedData.Factory<WorldSpecificViewDistancePersistentState> TYPE = new Factory<>(
        WorldSpecificViewDistancePersistentState::new,
        WorldSpecificViewDistancePersistentState::fromNbt,
        DataFixTypes.LEVEL
    );

    private int localViewDistance;
    private int localSimulationDistance;

    public static @NotNull WorldSpecificViewDistancePersistentState getFrom(@NotNull ServerLevel w) {
        return getFrom(w.getDataStorage());
    }

    public static @NotNull WorldSpecificViewDistancePersistentState getFrom(@NotNull DimensionDataStorage mgr) {
        return mgr.computeIfAbsent(TYPE, ID);
    }

    public static @NotNull WorldSpecificViewDistancePersistentState fromNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        WorldSpecificViewDistancePersistentState state = new WorldSpecificViewDistancePersistentState();
        state.localViewDistance = tag.getInt("LocalViewDistance");
        state.localSimulationDistance = tag.getInt("LocalSimulationDistance");
        return state;
    }

    public int getLocalViewDistance() {
        return localViewDistance;
    }

    public void setLocalViewDistance(int viewDistance) {
        if (viewDistance != localViewDistance) {
            localViewDistance = viewDistance;
        }
    }

    public int getLocalSimulationDistance() {
        return localSimulationDistance;
    }

    public void setLocalSimulationDistance(int localSimulationDistance) {
        this.localSimulationDistance = localSimulationDistance;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registryLookup) {
        tag.putInt("LocalViewDistance", localViewDistance);
        tag.putInt("LocalSimulationDistance", localSimulationDistance);
        return tag;
    }

    @Override
    public boolean isDirty() {
        return true;
    }
}
