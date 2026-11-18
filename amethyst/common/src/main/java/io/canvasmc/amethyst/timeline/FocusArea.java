package io.canvasmc.amethyst.timeline;

import io.canvasmc.amethyst.AmethystUtils;

/**
 * A connected, force loaded area followed in area mode, inclusive chunk coordinates. Splits are flood fills over
 * connected sections, so the whole area always stays in one region
 *
 * @param handle
 *     the region owning the area when profiling starts
 *
 * @author dueris
 * @implNote It is expected that all chunks within this area(inclusive) are never to be unloaded for the whole
 *     duration of the profiler
 */
public record FocusArea(String world, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, long handle) {
    public long anchorSection(final int sectionShift) {
        return AmethystUtils.coordinateKey(minChunkX >> sectionShift, minChunkZ >> sectionShift);
    }

    public io.canvasmc.amethyst.proto.FocusArea toProto() {
        return io.canvasmc.amethyst.proto.FocusArea.newBuilder()
            .setWorld(world)
            .setMinChunkX(minChunkX)
            .setMinChunkZ(minChunkZ)
            .setMaxChunkX(maxChunkX)
            .setMaxChunkZ(maxChunkZ)
            .build();
    }
}
