package io.canvasmc.canvas.world.chunk;

import ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor;

/**
 * A {@link ca.spottedleaf.concurrentutil.executor.PrioritisedExecutor} queue belonging to a
 * {@link io.canvasmc.canvas.world.chunk.StreamGroup}, with additional lifecycle functions for removing the queue from
 * its group outside shutdown
 *
 * @author dueris
 */
public interface ChunkSystemExecutor extends PrioritisedExecutor {
    /**
     * Removes this executor from its {@link io.canvasmc.canvas.world.chunk.StreamGroup} without shutting it down or
     * waiting for any queued tasks to be executed.
     */
    void halt();

    /**
     * Returns whether this executor is currently scheduled to run or is running tasks, or, if not halted, whether it is
     * simply not yet shut down.
     *
     * @return {@code true} if this executor is still considered active.
     */
    boolean isActive();
}
