package io.canvasmc.canvas;

import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;
import java.util.concurrent.Callable;

public interface LevelAccess {
    /**
     * Gets the Bukkit {@link World}
     * @return the world associated with this {@link LevelAccess} instance
     */
    World getWorld();
    /**
     * Schedules a task to the level thread
     * @param runnable the tick task
     */
    void scheduleOnThread(Runnable runnable);
    /**
     * Schedules a callable to the level thread, will block the current thread
     * until the callable is done and return the result, or execute immediately
     * if the current thread is the level thread
     * @param callable the callable that will be scheduled
     * @param <V> the return type
     */
    <V> V scheduleOnThread(Callable<V> callable) throws Exception;
    /**
     * Schedules a task to be ran before the next tick
     * @param runnable the tick task
     */
    void scheduleForPreNextTick(Runnable runnable);
    /**
     * Schedules a task to be ran after the next tick, or once the current tick is complete
     * @param runnable the tick task
     */
    void scheduleForPostNextTick(Runnable runnable);
    /**
     * If the level is actively ticking
     * @return true if the level is processing ticks
     */
    boolean isTicking();
    /**
     * Gets the scheduler impl for the level thread
     */
    BukkitScheduler getBukkitScheduler();
}
