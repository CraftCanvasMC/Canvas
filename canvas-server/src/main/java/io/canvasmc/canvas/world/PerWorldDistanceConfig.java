package io.canvasmc.canvas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.canvasmc.canvas.util.LockedReference;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import org.jspecify.annotations.NonNull;

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
    public static final Codec<PerWorldDistanceConfig> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codec.INT.optionalFieldOf("ViewDistance").orElse(Optional.empty()).forGetter(PerWorldDistanceConfig::viewDistanceAsOptional),
                Codec.INT.optionalFieldOf("SimulationDistance").orElse(Optional.empty()).forGetter(PerWorldDistanceConfig::simulationDistanceAsOptional)
            )
            .apply(instance, PerWorldDistanceConfig::new)
    );
    public static PerWorldDistanceConfig DEFAULT = new PerWorldDistanceConfig();

    public PerWorldDistanceConfig() {
        this(new LockedReference<>(null), new LockedReference<>(null));
    }

    // this is for the codec, nothing much else
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public PerWorldDistanceConfig(final @NonNull Optional<Integer> view, final @NonNull Optional<Integer> simulation) {
        this(
            view.map(LockedReference::new).orElse(new LockedReference<>(null)),
            simulation.map(LockedReference::new).orElse(new LockedReference<>(null))
        );
    }

    public PerWorldDistanceConfig(final @NonNull LockedReference<Integer> viewDistance, final @NonNull LockedReference<Integer> simulationDistance) {

        // for backwards compat with old system. we used to store
        // the -1 value, so this is a good way to clean this up

        this.viewDistance = viewDistance.getValue() == -1 ? new LockedReference<>(null) : viewDistance;
        this.simulationDistance = simulationDistance.getValue() == -1 ? new LockedReference<>(null) : simulationDistance;
    }

    public int snapshotViewDistance() {
        Integer raw = viewDistance().getValue();
        if (raw == null) {
            return -1;
        }
        return raw;
    }

    public int snapshotSimulationDistance() {
        Integer raw = simulationDistance().getValue();
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
