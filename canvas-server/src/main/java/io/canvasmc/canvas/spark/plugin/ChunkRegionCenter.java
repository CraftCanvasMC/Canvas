package io.canvasmc.canvas.spark.plugin;

public record ChunkRegionCenter(long id, int centerBlockX, int centerBlockZ) {
    public static final ChunkRegionCenter UNKNOWN = new ChunkRegionCenter(-1L, 0, 0);
}
