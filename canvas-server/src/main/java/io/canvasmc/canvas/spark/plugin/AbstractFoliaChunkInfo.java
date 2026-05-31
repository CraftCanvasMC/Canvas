package io.canvasmc.canvas.spark.plugin;

import me.lucko.spark.paper.common.platform.world.AbstractChunkInfo;

public abstract class AbstractFoliaChunkInfo<E> extends AbstractChunkInfo<E> {

    protected final long foliaRegionId;

    protected AbstractFoliaChunkInfo(int x, int z, ChunkRegionCenter center) {
        super(x, z);
        this.foliaRegionId = center.id();
    }

    public final long getFoliaRegionId() {
        return this.foliaRegionId;
    }
}
