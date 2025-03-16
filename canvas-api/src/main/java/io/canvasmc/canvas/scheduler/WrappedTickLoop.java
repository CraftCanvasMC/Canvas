package io.canvasmc.canvas.scheduler;

import io.canvasmc.canvas.RollingAverage;
import io.canvasmc.canvas.TickTimes;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * A "wrapped" tick-loop, which is a repeating
 * task that ticked in parallel at the normal Minecraft tick-rate
 * while backend is also handling tick times and other
 * useful information
 */
public interface WrappedTickLoop {
    /**
     * Gets the name used in more formal contexts, like "SuperCoolTickLoop"
     *
     * @return formal name
     */
    String getFormalName();

    /**
     * Gets the name used in more debugging contexts, like "super cool tick-loop"
     *
     * @return debug name
     */
    String getDebugName();

    /**
     * Gets if the tick-loop is currently sleeping.
     * If a tick-loop is sleeping, it is currently
     * only ticking tick times(to avoid watchdog yelling at us)
     * and nothing else.
     * <br>
     * This function is mostly predictable. If the
     * vanilla {@code pauseWhenEmptySeconds} property in the {@code server.properties}
     * is enabled, then it sleeps when there are no players actively in the server.
     * If this is an instanceof a {@link io.canvasmc.canvas.LevelAccess}, then
     * the server owner can toggle the {@code emptySleepPerWorlds} option
     * which makes each world have its own pause system, which acts like vanilla
     * but is based off its local player count.
     * <br><br>
     * You can fetch the configuration option like this:
     * <pre>{@code
     * boolean isEmptySleepPerWorld = io.canvasmc.canvas.config.impl.ConfigAccess.get().<Boolean>getField("emptySleepPerWorlds");
     * }</pre>
     *
     * @return if the tick-loop is currently sleeping
     */
    boolean isSleeping();

    /**
     * Gets if the tick-loop can sleep.
     * Some tick-loops, like the join thread, do not
     * sleep <b>ever</b>.
     * <br><br>
     * Overridable when providing the {@link WrappedTick} to {@link MultithreadedTickScheduler#scheduleWrapped(WrappedTick, String, String)}
     *
     * @return if the tick-loop can sleep.
     */
    boolean shouldSleep();

    /**
     * Gets the debug information for this tick-loop.
     * Each tick-loop with Canvas contains debug information
     * specifically tailored to its environment that assists
     * with development with and for Canvas. This is provided
     * to the {@code tps} command for when further debug is
     * requested from the command source
     * <br><br>
     * Overridable when providing the {@link WrappedTick} to {@link MultithreadedTickScheduler#scheduleWrapped(WrappedTick, String, String)}
     *
     * @return the tick-loop debug information
     */
    @NotNull Component debugInfo();

    /**
     * Gets if the tick-loop is in a state where
     * it can tick.
     *
     * @return if the tick-loop is ticking
     */
    boolean isTicking();

    /**
     * Gets the {@link Logger} for the tick-loop.
     * The name of the logger is the same as {@link WrappedTickLoop#getFormalName()}
     *
     * @return the logger for the tick-loop
     */
    Logger getLogger();

    /**
     * Gets the tps data from the last 5 seconds.
     * See {@link RollingAverage#getAverage()}
     *
     * @return tps data from the last 5 seconds
     */
    RollingAverage getTps5s();

    /**
     * Gets the tps data from the last 10 seconds.
     * See {@link RollingAverage#getAverage()}
     *
     * @return tps data from the last 10 seconds
     */
    RollingAverage getTps10s();

    /**
     * Gets the tps data from the last 15 seconds.
     * See {@link RollingAverage#getAverage()}
     *
     * @return tps data from the last 15 seconds
     */
    RollingAverage getTps15s();

    /**
     * Gets the tps data from the last 1 minute.
     * See {@link RollingAverage#getAverage()}
     *
     * @return tps data from the last 1 minute
     */
    RollingAverage getTps1m();

    /**
     * Gets the tick times from the last 5 seconds.
     * This includes data like the thread utilization and mspt.
     * See {@link TickTimes#getAverage()} and {@link TickTimes#getUtilization()}
     *
     * @return the tick times in the last 5 seconds
     */
    TickTimes getTickTimes5s();

    /**
     * Gets the tick times from the last 10 seconds.
     * This includes data like the thread utilization and mspt.
     * See {@link TickTimes#getAverage()} and {@link TickTimes#getUtilization()}
     *
     * @return the tick times in the last 10 seconds
     */
    TickTimes getTickTimes10s();

    /**
     * Gets the tick times from the last 15 seconds.
     * This includes data like the thread utilization and mspt.
     * See {@link TickTimes#getAverage()} and {@link TickTimes#getUtilization()}
     *
     * @return the tick times in the last 15 seconds
     */
    TickTimes getTickTimes15s();

    /**
     * Gets the tick times from the last 1 minute.
     * This includes data like the thread utilization and mspt.
     * See {@link TickTimes#getAverage()} and {@link TickTimes#getUtilization()}
     *
     * @return the tick times in the last 1 minute
     */
    TickTimes getTickTimes60s();

    /**
     * Blocks the current thread until a stop condition becomes true.
     * This runs any pending tasks scheduled to the main thread,
     * which can be literally anything, but does this while blocking
     * the current thread waiting for the condition to be completed.
     * <br><br>
     * It is not recommended to do this, as this can execute potentially
     * unsafe code(probably from a plugin).
     *
     * @param stopCondition the condition to stop blocking
     */
    void managedBlock(BooleanSupplier stopCondition);

    /**
     * Pushes a task to the queue for the tick-loop.
     * The task queue is guaranteed to be emptied before
     * every tick. The tasks scheduled here cannot be accessed
     * from another tick-loop, meaning that whatever thread
     * the tick is running on is the only thread that can access
     * the tasks.
     *
     * @param task the schedulable task
     */
    void pushTask(Runnable task);

    /**
     * Gets the tick count, which increases by 1
     * for every tick. This is also passed to {@link WrappedTick#blockTick(WrappedTickLoop, BooleanSupplier, int)}
     * <br><br>
     * If called during {@link WrappedTick#blockTick(WrappedTickLoop, BooleanSupplier, int)}, this returns 1 value more
     * than the provided 'tickCount' argument.
     * @return the tick count
     */
    int getTickCount();

    /**
     * Retires the current tick-loop, marking it
     * as canceled and doesn't reschedule.
     * <br><br>
     * If attempted to reschedule this instance, the scheduler
     * will throw an {@link IllegalStateException} because it was already scheduled previously.
     * If you want to dock/undock the tick-loop, use {@link WrappedTickLoop#sleep()} and {@link WrappedTickLoop#wake()},
     * which properly handles docking/undocking the tick-loop from ticking.
     */
    void retire();

    /**
     * Undocks the tick-loop from the scheduler, making it not tick.
     * <br><br>
     * Sleeping doesn't completely pause the tick-loop, but instead marks the state of the
     * thread to not run the {@link WrappedTick#blockTick(WrappedTickLoop, BooleanSupplier, int)}, or
     * poll tick tasks. It purely ticks timings and that's it.
     */
    void sleep();

    /**
     * Docks the tick-loop to the scheduler, making it tick.
     * <br><br>
     * By waking up, it marks the tick-loop as tickable, so it processes
     * the {@link WrappedTick#blockTick(WrappedTickLoop, BooleanSupplier, int)} and tick tasks.
     */
    void wake();

    /**
     * The tick processor for this tick-loop
     */
    @FunctionalInterface
    interface WrappedTick {
        /**
         * Processes the tick-loop
         * @param loop the owning tick-loop
         * @param hasTimeLeft if the tick has time left. determination of if the loop has time left is as follows:
         *                    <pre>{@code
         *                    // if the tick-loop is sprinting, the 'nanosecondsOverload' is 0.
         *                    // if it isn't sprinting, it returns the nanoseconds per tick.
         *                    nanosecondsOverload == 0L ? () -> false : this::haveTime
         *                    // haveTime() is determined by if the server is either forcing ticks,
         *                    // or if the current time(in nanoseconds) is less than the scheduled next tick time(in nanoseconds)
         *                    }</pre>
         * @param tickCount the current tick count
         */
        void blockTick(WrappedTickLoop loop, BooleanSupplier hasTimeLeft, int tickCount);

        /**
         * Debug information overload for the tick-loop.
         * See {@link WrappedTickLoop#debugInfo()}
         * @return debug information for the tick-loop
         */
        default Component debugInfo() {
            return Component.empty();
        }

        /**
         * Overload for if the tick-loop should sleep.
         * See {@link WrappedTickLoop#shouldSleep()}
         * @return if the tick-loop should sleep
         */
        default boolean shouldSleep() {
            return true;
        }
    }
}
