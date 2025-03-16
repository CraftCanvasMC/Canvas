package io.canvasmc.canvas.scheduler;

/**
 * Barebones implementation of a tick task
 */
@FunctionalInterface
public interface Tick {
    /**
     * Processes a tick.
     * @return if the task should reschedule or retire.
     */
    boolean blockTick();
}
