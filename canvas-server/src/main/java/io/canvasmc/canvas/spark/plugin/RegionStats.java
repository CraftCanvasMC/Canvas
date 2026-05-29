package io.canvasmc.canvas.spark.plugin;

public record RegionStats(long id, double tps, double mspt, double util) {
    public static final RegionStats UNKNOWN = new RegionStats(-1L, -1d, -1d, -1d);
}
