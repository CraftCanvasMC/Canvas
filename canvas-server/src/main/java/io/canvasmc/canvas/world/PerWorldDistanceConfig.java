package io.canvasmc.canvas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.canvasmc.canvas.util.LockedReference;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * Per world view and simulation distance override. When the value is <= 0, it uses the value from the server
 * properties.
 *
 * @param viewDistance
 *     the view distance override
 * @param simulationDistance
 *     the simulation distance override
 */
public record PerWorldDistanceConfig(
    LockedReference<Integer> viewDistance, LockedReference<Integer> simulationDistance
) {
    public static PerWorldDistanceConfig DEFAULT = new PerWorldDistanceConfig(Optional.empty(), Optional.empty());
    public static final Codec<PerWorldDistanceConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("ViewDistance").orElse(Optional.empty()).forGetter(PerWorldDistanceConfig::viewDistanceAsOptional),
        Codec.INT.optionalFieldOf("SimulationDistance").orElse(Optional.empty()).forGetter(PerWorldDistanceConfig::simulationDistanceAsOptional)
    ).apply(i, PerWorldDistanceConfig::new));

    // this is for the codec, nothing much else
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public PerWorldDistanceConfig(final Optional<Integer> view, final Optional<Integer> simulation) {
        this(
            view.map(LockedReference::new).orElse(new LockedReference<>(null)),
            simulation.map(LockedReference::new).orElse(new LockedReference<>(null))
        );
    }

    public PerWorldDistanceConfig(
        final LockedReference<Integer> viewDistance,
        final LockedReference<Integer> simulationDistance
    ) {

        // for backwards compat with old system. we used to store
        // the -1 value, so this is a good way to clean this up

        final Integer view = viewDistance.getValue();
        final Integer simulation = simulationDistance.getValue();

        this.viewDistance = Integer.valueOf(-1).equals(view)
            ? new LockedReference<>(null)
            : viewDistance;

        this.simulationDistance = Integer.valueOf(-1).equals(simulation)
            ? new LockedReference<>(null)
            : simulationDistance;
    }

    public int snapshotViewDistance() {
        final Integer raw = viewDistance().getValue();
        if (raw == null) {
            return -1;
        }
        return raw;
    }

    public int snapshotSimulationDistance() {
        final Integer raw = simulationDistance().getValue();
        if (raw == null) {
            return -1;
        }
        return raw;
    }

    public Optional<Integer> viewDistanceAsOptional() {
        return viewDistance.asOptional();
    }

    public Optional<Integer> simulationDistanceAsOptional() {
        return simulationDistance.asOptional();
    }

    public int viewDistanceOrDefault() {
        final int snapshot = snapshotViewDistance();
        return snapshot <= 0 ? ((DedicatedServer) MinecraftServer.getServer()).settings.getProperties().viewDistance.get() : snapshot;
    }

    public int simulationDistanceOrDefault() {
        final int snapshot = snapshotSimulationDistance();
        return snapshot <= 0 ? ((DedicatedServer) MinecraftServer.getServer()).settings.getProperties().simulationDistance.get() : snapshot;
    }

    public boolean isViewDistanceOverridden() {
        return this.viewDistance().isSet();
    }

    public boolean isSimulationDistanceOverridden() {
        return this.simulationDistance().isSet();
    }

    public void setViewDistance(final int distance) {
        if (distance == -1) {
            viewDistance().unset();
        }
        else {
            viewDistance().swapValue(distance);
        }
    }

    public void setSimulationDistance(final int distance) {
        if (distance == -1) {
            simulationDistance().unset();
        }
        else {
            simulationDistance().swapValue(distance);
        }
    }
}
