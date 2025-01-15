package io.canvasmc.canvas.server.level;

public interface WatchdogWatcher {
    /**
     * Name of the watchdog instance. Either "server" for the main thread
     * or "level(<level id>)" for a level thread
     *
     * @return the name of the watcher
     */
    String getName();

    /**
     * Retrieves the running thread for watchdog to track
     *
     * @return The running thread.
     */
    Thread getRunningThread();
}
