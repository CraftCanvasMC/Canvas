package io.canvasmc.canvas.spark.plugin;

import me.lucko.spark.paper.common.platform.world.AbstractChunkInfo;

public abstract class AbstractFoliaChunkInfo<E> extends AbstractChunkInfo<E> {

    protected final long foliaRegionId;
    protected final double regionTps;
    protected final double regionMspt;
    protected final double regionUtil;

    protected AbstractFoliaChunkInfo(int x, int z, RegionStats stats) {
        super(x, z);
        this.foliaRegionId = stats.id();
        this.regionTps = stats.tps();
        this.regionMspt = stats.mspt();
        this.regionUtil = stats.util();
    }

    public final long getFoliaRegionId() {
        return this.foliaRegionId;
    }

    public double getRegionMspt() {
        return regionMspt;
    }

    public double getRegionTps() {
        return regionTps;
    }

    public double getRegionUtil() {
        return regionUtil;
    }
}
