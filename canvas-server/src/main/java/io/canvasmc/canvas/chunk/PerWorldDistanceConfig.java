package io.canvasmc.canvas.chunk;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.canvasmc.canvas.util.Codecs;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * Per world view and simulation distance override. When the value is <= 0, it uses the value from the server properties.
 *
 * @param viewDistance       the view distance override
 * @param simulationDistance the simulation distance override
 */
public record PerWorldDistanceConfig(AtomicInteger viewDistance, AtomicInteger simulationDistance) {
    private static final AtomicInteger DEFAULT_DISTANCE = new AtomicInteger(-1);
    public static final Codec<PerWorldDistanceConfig> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                Codecs.ATOMIC_INTEGER.fieldOf("ViewDistance").orElse(DEFAULT_DISTANCE).forGetter(PerWorldDistanceConfig::viewDistance),
                Codecs.ATOMIC_INTEGER.fieldOf("SimulationDistance").orElse(DEFAULT_DISTANCE).forGetter(PerWorldDistanceConfig::simulationDistance)
            )
            .apply(instance, PerWorldDistanceConfig::new)
    );
    public static PerWorldDistanceConfig DEFAULT = new PerWorldDistanceConfig(DEFAULT_DISTANCE, DEFAULT_DISTANCE);

    public int viewDistanceOrDefault() {
        return this.viewDistance.get() <= 0 ? ((DedicatedServer) MinecraftServer.getServer()).settings.getProperties().viewDistance : this.viewDistance.get();
    }

    public int simulationDistanceOrDefault() {
        return this.simulationDistance.get() <= 0 ? ((DedicatedServer) MinecraftServer.getServer()).settings.getProperties().simulationDistance : this.simulationDistance.get();
    }

    public boolean isViewDistanceOverridden() {
        return this.viewDistance.get() > 0;
    }

    public boolean isSimulationDistanceOverridden() {
        return this.viewDistance.get() > 0;
    }
}
