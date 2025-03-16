package io.canvasmc.canvas.scheduler;

/**
 * An interface into Canvas' tick scheduler
 */
public interface MultithreadedTickScheduler {
    int TICK_RATE = 20;
    long TIME_BETWEEN_TICKS = 1_000_000_000L / TICK_RATE; // nanoseconds

    /**
     * Schedules a tick to be repeated at the normal Minecraft
     * tick-rate, 20 tps, in parallel with the rest of the server.
     *
     * @param tick the tick
     */
    void schedule(Tick tick);

    /**
     * This is a slightly different implementation of {@link MultithreadedTickScheduler#schedule(Tick)}
     * that wraps the tick in an "Abstract Tick-Loop", which does the following:
     * <ui>
     * <li>Appends the tick loop to the <b>/tps</b> command</li>
     * <li>Registers it to Watchdog</li>
     * <li>Implements tick times handling</li>
     * <li>Implements tick sleeping</li>
     * <li>Implements "task scheduling"</li>
     * </ui>
     * Other than the wrapper, this works exactly the same as {@link MultithreadedTickScheduler#schedule(Tick)}
     * <br><br>
     * Example:
     * <pre>{@code
     * ThreadedBukkitServer bukkitServer = ThreadedBukkitServer.getInstance();
     * bukkitServer.getScheduler().scheduleWrapped(new WrappedTickLoop.WrappedTick() {
     *       @Override
     *       public void blockTick(final WrappedTickLoop loop, final BooleanSupplier hasTimeLeft, final int tickCount) {
     *             if (tickCount % 20 == 0) {
     *                 loop.getLogger().info("Hello World! The tick count is: {}", loop.getTickCount());
     *             }
     *       }
     *
     *       @Override
     *       public Component debugInfo() {
     *            return Component.text("Hello World!", TextColor.color(200, 20, 100), TextDecoration.OBFUSCATED);
     *       }
     * }, "TestLoop", "test loop");
     * }</pre>
     *
     * @param tick       the tick
     * @param formalName This is the formal name for your tick-loop, shown in Watchdog and other references that are more of a clarifying name
     * @param debugName  This is the debug name for your tick-loop, shown in debugging scenarios like "is {@code <debug_name>} overloaded? Running X ticks behind!"
     */
    WrappedTickLoop scheduleWrapped(WrappedTickLoop.WrappedTick tick, String formalName, String debugName);
}
