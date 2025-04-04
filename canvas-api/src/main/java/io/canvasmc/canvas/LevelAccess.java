package io.canvasmc.canvas;

import io.canvasmc.canvas.scheduler.WrappedTickLoop;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitScheduler;
import java.util.concurrent.Callable;

public interface LevelAccess extends WrappedTickLoop {
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
