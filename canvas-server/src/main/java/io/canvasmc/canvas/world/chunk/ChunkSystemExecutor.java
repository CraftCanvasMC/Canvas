package io.canvasmc.canvas.world.chunk;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;

public interface ChunkSystemExecutor extends PrioritisedExecutor {
    void halt();
    boolean isActive();
}
