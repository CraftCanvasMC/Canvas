package io.canvasmc.canvas.scheduler;

import ca.spottedleaf.concurrentutil.scheduler.SchedulerThreadPool;
import ca.spottedleaf.concurrentutil.util.TimeUtil;
import ca.spottedleaf.moonrise.common.util.TickThread;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.event.TickSchedulerInitEvent;
import io.canvasmc.canvas.region.ServerRegions;
import io.canvasmc.canvas.server.AbstractTickLoop;
import io.canvasmc.canvas.server.MultiWatchdogThread;
import io.canvasmc.canvas.server.TickLoopConstantsUtils;
import io.canvasmc.canvas.util.NamedAgnosticThreadFactory;
import io.papermc.paper.util.TraceUtil;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static ca.spottedleaf.concurrentutil.scheduler.SchedulerThreadPool.DEADLINE_NOT_SET;

public class TickLoopScheduler implements MultithreadedTickScheduler {
    private static TickLoopScheduler INSTANCE;
    public final SchedulerThreadPool scheduler;

    TickLoopScheduler(final int threads) {
        if (INSTANCE != null) throw new IllegalStateException("tried to build new scheduler when one was already set");
        this.scheduler = new SchedulerThreadPool(threads, new NamedAgnosticThreadFactory<>("tick scheduler", ThreadRunner::new, Config.INSTANCE.ticking.tickLoopThreadPriority));
        INSTANCE = this;
    }

    public static void start() {
        TickLoopScheduler loopScheduler = getInstance();
        loopScheduler.scheduler.start();
        new TickSchedulerInitEvent().callEvent();
        for (final AbstractTickLoop tickLoop : MinecraftServer.getThreadedServer().getTickLoops()) {
            tickLoop.scheduleToPool();
        }
    }

    // uncomment for testing purposes for api
    /* public static void test() {
        ThreadedBukkitServer bukkitServer = ThreadedBukkitServer.getInstance();
        bukkitServer.getScheduler().scheduleWrapped(new WrappedTickLoop.WrappedTick() {
            @Override
            public void blockTick(final WrappedTickLoop loop, final BooleanSupplier hasTimeLeft, final int tickCount) {
                if (tickCount % 20 == 0) {
                    loop.getLogger().info("Hello World! The tick count is: {}", loop.getTickCount());
                }
            }

            @Override
            public Component debugInfo() {
                return Component.text("Hello World!", TextColor.color(200, 20, 100), TextDecoration.OBFUSCATED);
            }
        }, "TestLoop", "test loop");
    } */

    public static TickLoopScheduler getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new TickLoopScheduler(Config.INSTANCE.ticking.allocatedSchedulerThreadCount);
        }
        return INSTANCE;
    }

    public void dumpAliveThreadTraces(final String reason) {
        for (final Thread thread : this.scheduler.getThreads()) {
            if (thread.isAlive()) {
                TraceUtil.dumpTraceForThread(thread, reason);
            }
        }
    }

    // Note: only a region can call this
    public static void setTickingData(ServerRegions.WorldTickData data) {
        if (!(Thread.currentThread() instanceof ThreadRunner runner)) {
            throw new RuntimeException("Unable to set ticking data of a non thread-runner");
        }
        runner.threadLocalTickData = data;
    }

    @Contract(pure = true)
    public static ServerRegions.@Nullable WorldTickData getCurrentTickData() {
        if (Thread.currentThread() instanceof ThreadRunner runner) {
            return runner.threadLocalTickData;
        }
        return null;
    }

    public static class ThreadRunner extends TickThread {
        public volatile ServerRegions.WorldTickData threadLocalTickData;
        public ThreadRunner(final ThreadGroup group, final Runnable run, final String name) {
            super(group, run, name);
        }
    }

    @Override
    public void schedule(final Tick tick) {
        this.scheduler.schedule(new AbstractTick() {
            @Override
            public boolean blockTick() {
                return tick.blockTick();
            }
        });
    }

    @Override
    public WrappedTickLoop scheduleWrapped(final WrappedTickLoop.WrappedTick tick, final String formalName, final String debugName) {
        AbstractTickLoop tickLoop = new AbstractTickLoop(formalName, debugName) {
            @Override
            protected void blockTick(final BooleanSupplier hasTimeLeft, final int tickCount) {
                int processedPolledCount = 0;
                while (this.pollInternal() && !shutdown) processedPolledCount++;
                tick.blockTick(this, hasTimeLeft, tickCount);
            }

            @Override
            public @NotNull Component debugInfo() {
                return tick.debugInfo();
            }

            @Override
            public boolean shouldSleep() {
                return tick.shouldSleep();
            }
        };
        tickLoop.scheduleToPool();
        return tickLoop;
    }

    public static abstract class AbstractTick extends SchedulerThreadPool.SchedulableTick implements Tick {
        private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        private final Schedule tickSchedule;
        protected Thread owner;
        protected Thread lastOwningThread;
        protected volatile boolean shutdown = false;
        private boolean hasInitSchedule = false;
        protected long tickStart = 0L;

        public AbstractTick() {
            this.setScheduledStart(System.nanoTime() + TIME_BETWEEN_TICKS);
            this.tickSchedule = new Schedule(DEADLINE_NOT_SET);
        }

        @Override
        public boolean runTick() {
            try {
                this.lastOwningThread = this.owner;
                this.owner = Thread.currentThread(); // this can change whenever, we update before every single tick
                tickStart = System.nanoTime();
                if (!hasInitSchedule) {
                    this.tickSchedule.setLastPeriod(System.nanoTime());
                    hasInitSchedule = true;
                }
                final MultiWatchdogThread.RunningTick runningTick = new MultiWatchdogThread.RunningTick(tickStart, this, Thread.currentThread());
                MultiWatchdogThread.WATCHDOG.dock(runningTick);
                final int tickCount = Math.max(1, this.tickSchedule.getPeriodsAhead(TIME_BETWEEN_TICKS, tickStart));
                boolean shouldReschedule = blockTick();
                MultiWatchdogThread.WATCHDOG.undock(runningTick);
                if (!shouldReschedule) {
                    // retire tick
                    TickLoopScheduler.getInstance().scheduler.tryRetire(this);
                }
                final long tickEnd = System.nanoTime();
                this.tickSchedule.advanceBy(tickCount, TIME_BETWEEN_TICKS);
                setScheduledStart(TimeUtil.getGreatestTime(tickEnd, this.tickSchedule.getDeadline(TIME_BETWEEN_TICKS)));
                return shouldReschedule;
            } catch (Exception e) {
                TickLoopConstantsUtils.hardCrashCatch(e);
                return false; // we aren't rescheduling...
            }
        }

        @Override
        public boolean hasTasks() {
            return !tasks.isEmpty();
        }

        @Override
        public Boolean runTasks(final BooleanSupplier canContinue) {
            int i = 0;
            while (this.pollInternal() && !shutdown) i++;
            return true;
        }

        @Override
        public abstract boolean blockTick();

        public void scheduleOnMain(Runnable runnable) {
            tasks.add(runnable);
        }

        public void execute(Runnable runnable) {
            scheduleOnMain(runnable);
        }

        // polls and runs a task
        protected boolean pollInternal() {
            @Nullable Runnable task = this.tasks.poll();
            if (task != null) {
                task.run();
                return true;
            }
            // no more tasks left
            return false;
        }
    }
}
