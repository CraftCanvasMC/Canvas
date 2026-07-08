package io.canvasmc.canvas.world.chunk;

/**
 * A group of {@link ChunkSystemExecutor} queues that share a single priority-ordered task stream, allowing tasks from
 * multiple queues to be executed in priority order by a thread pool.
 *
 * @author dueris
 */
public interface StreamGroup {

    /**
     * Creates and registers a new {@link ChunkSystemExecutor} queue in this group.
     *
     * @return the new executor.
     */
    ChunkSystemExecutor createExecutor();
}
