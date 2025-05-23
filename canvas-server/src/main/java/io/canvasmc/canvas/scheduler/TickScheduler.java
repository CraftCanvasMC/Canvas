package io.canvasmc.canvas.scheduler;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.RollingAverage;
import io.canvasmc.canvas.TickTimes;
import io.canvasmc.canvas.event.TickSchedulerStartEvent;
import io.canvasmc.canvas.region.ServerRegions;
import io.canvasmc.canvas.server.MultiWatchdogThread;
import io.canvasmc.canvas.server.ThreadedServer;
import io.canvasmc.canvas.util.ConcurrentSet;
import io.canvasmc.canvas.util.IdGenerator;
import io.papermc.paper.threadedregions.ScheduledTaskThreadPool;
import io.papermc.paper.util.TraceUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.util.TimeUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.util.CraftNamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static ca.spottedleaf.concurrentutil.scheduler.SchedulerThreadPool.DEADLINE_NOT_SET;

public class TickScheduler implements MultithreadedTickScheduler {
    public static final Logger LOGGER = LoggerFactory.getLogger("Scheduler");
    private static TickScheduler INSTANCE;
    private final int threadCount;
    public final ScheduledTaskThreadPool scheduler;
    public final DedicatedServer server;
    public final BigDecimal tpsBase;
    private int tickRate;

    public TickScheduler(int threadCount, DedicatedServer server) {
        this.server = server;
        if (INSTANCE != null) throw new IllegalStateException("tried to build new scheduler when one was already set");
        this.threadCount = threadCount;
        this.scheduler = new ScheduledTaskThreadPool(
            new TickThreadFactory(Config.INSTANCE.ticking.tickLoopThreadPriority),
            TimeUnit.MILLISECONDS.toNanos(3L), TimeUnit.MILLISECONDS.toNanos(2L)
        );
        this.setTickRate(20); // default tick rate
        this.tpsBase = new BigDecimal("1E9").multiply(new java.math.BigDecimal(SAMPLE_RATE));
        INSTANCE = this;
    }

    public static TickScheduler getScheduler() {
        if (INSTANCE == null) {
            throw new IllegalStateException("scheduler hasn't been instantiated yet");
        }
        return INSTANCE;
    }

    public void start() {
        TickScheduler tickScheduler = getScheduler();
        tickScheduler.scheduler.setCoreThreads(tickScheduler.threadCount);
        ThreadedServer.LOGGER.info("Tick Scheduler is enabled with {} tick runners allocated", tickScheduler.threadCount);
        new TickSchedulerStartEvent().callEvent();
        for (final FullTick<? extends WrappedTickLoop.WrappedTick> fullTick : FullTick.ALL_REGISTERED) {
            if (fullTick.alreadyScheduled) continue;
            fullTick.scheduleTo(tickScheduler.scheduler);
        }
    }

    public boolean halt(final boolean sync, final long maxWaitNS) {
        this.scheduler.halt();
        if (!sync) {
            return this.scheduler.getAliveThreads().length == 0;
        }

        return this.scheduler.join(maxWaitNS == 0L ? 0L : Math.max(1L, TimeUnit.NANOSECONDS.toMillis(maxWaitNS)));
    }

    public void dumpAliveThreadTraces(final String reason) {
        for (final Thread thread : this.scheduler.getCoreThreads()) {
            if (thread.isAlive()) {
                TraceUtil.dumpTraceForThread(thread, reason);
            }
        }
    }

    @Override
    public WrappedTickLoop scheduleWrapped(final WrappedTickLoop.WrappedTick tick, final NamespacedKey identifier) {
        FullTick<?> tickLoop = new FullTick<>((DedicatedServer) MinecraftServer.getServer(), CraftNamespacedKey.toMinecraft(identifier), tick);
        tickLoop.scheduleTo(this.scheduler);
        return tickLoop;
    }

    @Override
    public @Nullable WrappedTickLoop getTickLoop(final NamespacedKey identifier) {
        ResourceLocation minecraft = CraftNamespacedKey.toMinecraft(identifier);
        for (final FullTick<?> fullTick : FullTick.ALL_REGISTERED) {
            if (fullTick.identifier.equals(minecraft)) return fullTick;
        }
        return null;
    }

    @Override
    public int getThreadCount() {
        return this.threadCount;
    }

    @Override
    public Thread[] getThreads() {
        return this.scheduler.getCoreThreads();
    }

    @Override
    public int getTickRate() {
        return tickRate;
    }

    @Override
    public void setTickRate(final int tickRate) {
        if (tickRate <= 0) throw new IllegalArgumentException("Cannot set non-positive value for tickrate");
        // this will automatically update the schedulers, given they depend on this value
        this.tickRate = tickRate;
        this.server.tickRateManager().setTickRate(tickRate); // update main thread
        // reset tick times, as this is now technically inaccurate now
        for (final FullTick<?> fullTick : FullTick.ALL_REGISTERED) {
            fullTick.tickTimes5s.reset();
            fullTick.tickTimes10s.reset();
            fullTick.tickTimes15s.reset();
            fullTick.tickTimes60s.reset();
            fullTick.tps5s.reset();
            fullTick.tps10s.reset();
            fullTick.tps15s.reset();
            fullTick.tps1m.reset();
        }
    }

    @Override
    public long getTimeBetweenTicks() {
        return 1_000_000_000L / tickRate;
    }

    @Override
    public BigDecimal getTpsBase() {
        return tpsBase;
    }

    // Note: only a region can call this
    public static void setTickingData(ServerRegions.WorldTickData data) {
        if (!(Thread.currentThread() instanceof TickRunner runner)) {
            throw new RuntimeException("Unable to set ticking data of a non thread-runner");
        }
        runner.threadLocalTickData = data;
    }

    public static class TickRunner extends TickThread {
        public volatile ServerRegions.WorldTickData threadLocalTickData;
        public TickRunner(final ThreadGroup group, final Runnable run, final String name) {
            super(group, run, name);
        }
    }

    // implements basically all logic needed for a scheduled tick-loop
    public static class FullTick<T extends WrappedTickLoop.WrappedTick> extends ScheduledTaskThreadPool.SchedulableTick implements WrappedTickLoop, Comparable<FullTick<T>> {
        public static final Set<FullTick<?>> ALL_REGISTERED = new ConcurrentSet<>();
        public static final Object SLEEP_HANDLE = new Object();

        // important
        public final DedicatedServer server;
        protected final ResourceLocation identifier;
        public final T tick;
        private final Schedule tickSchedule;
        public long lastRespondedNanos = Util.getNanos();
        protected long tickStart;
        protected long tickEnd;

        // tick times
        public final RollingAverage tps5s = new RollingAverage(5);
        public final RollingAverage tps10s = new RollingAverage(10);
        public final RollingAverage tps15s = new RollingAverage(15);
        public final RollingAverage tps1m = new RollingAverage(60);
        public final TickTimes tickTimes5s = new TickTimes(100);
        public final TickTimes tickTimes10s = new TickTimes(200);
        public final TickTimes tickTimes15s = new TickTimes(300);
        public final TickTimes tickTimes60s = new TickTimes(1200);

        // misc
        protected volatile boolean isSleeping = false;
        protected boolean wasSleeping;
        public int tickCount;
        public final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile long tickSection;
        private long nextTickTimeNanos;
        private long lastOverloadWarningNanos;
        private boolean hasInitSchedule;
        private boolean processingTick;
        private final long constructorInit;
        public Thread owner;

        // tasks
        public ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        public boolean alreadyScheduled = false;

        public FullTick(DedicatedServer server, ResourceLocation identifier, T tick) {
            this.server = server;
            this.identifier = identifier;
            this.tick = tick;
            this.setScheduledStart(System.nanoTime() + getScheduler().getTimeBetweenTicks());
            this.tickSchedule = new Schedule(DEADLINE_NOT_SET);
            this.constructorInit = Util.getNanos();
            this.tickSection = this.constructorInit;
        }

        @Override
        public boolean runTick() {
            this.owner = Thread.currentThread();
            boolean reschedule = fullTick();
            if (!reschedule) {
                this.retire();
            }
            return reschedule;
        }

        public void bench(@NotNull Runnable tick) {
            this.tickStart = Util.getNanos();
            tick.run();
        }

        public boolean fullTick() {
            // pre-tick
            if (!hasInitSchedule) {
                this.tickSchedule.setLastPeriod(System.nanoTime());
                hasInitSchedule = true;
            }

            if (processingTick) {
                // hard-kill now. recursive call
                Throwable throwable = new RuntimeException("recursive tick call detected");
                MinecraftServer.LOGGER.error("Encountered an unexpected exception", throwable);
                CrashReport crashReport = MinecraftServer.constructOrExtractCrashReport(throwable);
                CrashReportCategory crashReportCategory = crashReport.addCategory("TickLoop");
                crashReportCategory.setDetail("Identifier", this.getLocation());
                crashReportCategory.setDetail("TickCount", this.tickCount);
                MinecraftServer server = MinecraftServer.getServer();

                server.fillSystemReport(crashReport.getSystemReport());
                Path path = server.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");

                if (crashReport.saveToFile(path, ReportType.CRASH)) {
                    MinecraftServer.LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
                } else {
                    MinecraftServer.LOGGER.error("We were unable to save this crash report to disk.");
                }
                server.onServerCrash(crashReport);
                this.retire();
                server.notifyStop = true;
                return false;
            }
            processingTick = true;
            lastRespondedNanos = Util.getNanos();

            // ticking
            {
                // process overload
                long nanosecondsOverload;
                if (!this.server.isPaused() && this.server.tickRateManager().isSprinting() && this.server.tickRateManager().checkShouldSprintThisTick()) {
                    nanosecondsOverload = 0L;
                    this.nextTickTimeNanos = Util.getNanos();
                    this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                } else {
                    nanosecondsOverload = getScheduler().getTimeBetweenTicks();
                    long l1 = Util.getNanos() - this.nextTickTimeNanos;
                    if (l1 > MinecraftServer.OVERLOADED_THRESHOLD_NANOS + 20L * nanosecondsOverload
                        && this.nextTickTimeNanos - this.lastOverloadWarningNanos >= MinecraftServer.OVERLOADED_WARNING_INTERVAL_NANOS + 100L * nanosecondsOverload) {
                        long l2 = l1 / nanosecondsOverload;
                        if (this.server.server.getWarnOnOverload())
                            LOGGER.warn("Can't keep up! Is the {} overloaded? Running {}ms or {} ticks behind", this, l1 / TimeUtil.NANOSECONDS_PER_MILLISECOND, l2);
                        this.nextTickTimeNanos += l2 * nanosecondsOverload;
                        this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                    }
                }

                tickStart = Util.getNanos();
                final int tickCount = Math.max(1, this.tickSchedule.getPeriodsAhead(getScheduler().getTimeBetweenTicks(), tickStart));
                tickTps(tickStart);

                boolean doesntHaveTime = nanosecondsOverload == 0L;

                this.nextTickTimeNanos += nanosecondsOverload;

                // dock watchdog
                final MultiWatchdogThread.RunningTick runningTick = new MultiWatchdogThread.RunningTick(tickStart, this, Thread.currentThread());
                MultiWatchdogThread.WATCHDOG.dock(runningTick);
                // run tick
                if (!isSleeping) {
                    if (wasSleeping) {
                        wasSleeping = false;
                        LOGGER.info("Waking tick-loop '{}'", this.getLocation());
                    }
                    try {
                        tickStart = Util.getNanos();
                        if (!this.tick.blockTick(this, doesntHaveTime ? () -> false : this::haveTime, this.tickCount)) this.retire();
                        tickEnd = Util.getNanos();
                    } catch (Throwable throwable) {
                        MultiWatchdogThread.WATCHDOG.undock(runningTick); // don't continue dock on watchdog if we fail to tick.
                        LOGGER.error("Encountered tick task crash at '{}'", this);
                        //noinspection removal
                        if (throwable instanceof ThreadDeath) {
                            LOGGER.error("Tick task terminated by WatchDog due to hard crash", throwable);
                            return false;
                        }
                        LOGGER.error("", throwable);
                        CrashReport crashreport = MinecraftServer.constructOrExtractCrashReport(throwable);

                        this.server.fillSystemReport(crashreport.getSystemReport());
                        Path path = this.server.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");

                        if (crashreport.saveToFile(path, ReportType.CRASH)) {
                            LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
                        } else {
                            LOGGER.error("We were unable to save this crash report to disk.");
                        }

                        this.retire();
                        this.server.onServerCrash(crashreport);

                        try {
                            this.server.stopped = true;
                            this.server.stopServer();
                        } catch (Throwable throwable3) {
                            LOGGER.error("Exception stopping the server via tick task", throwable3);
                        } finally {
                            if (server.services.profileCache() != null) {
                                server.services.profileCache().clearExecutor();
                            }
                        }
                        return false;
                    }
                } else {
                    if (!wasSleeping) LOGGER.info("Pausing tick-loop '{}'", this.getLocation());
                    wasSleeping = true;
                    tickEnd = Util.getNanos();
                }
                // undock watchdog
                MultiWatchdogThread.WATCHDOG.undock(runningTick);
                // schedule next tick
                this.tickSchedule.advanceBy(tickCount, getScheduler().getTimeBetweenTicks());
                this.setScheduledStart(ca.spottedleaf.concurrentutil.util.TimeUtil.getGreatestTime(tickEnd, this.tickSchedule.getDeadline(getScheduler().getTimeBetweenTicks())));
                this.nextTickTimeNanos = this.getScheduledStart(); // use internal scheduled start from ScheduledTaskThreadPool.SchedulableTick

                // we don't have tps catchup on tick schedulers.
                tickMspt(tickStart);
                processingTick = false;
                return !cancelled.get();
            }
        }

        public void scheduleTo(@NotNull ScheduledTaskThreadPool scheduler) {
            this.setScheduledStart(System.nanoTime() + getScheduler().getTimeBetweenTicks());
            if (alreadyScheduled) {
                throw new RuntimeException("already scheduled " + getIdentifier());
            }
            LOGGER.info("Scheduled tick task '{}' on scheduler", this.identifier);
            alreadyScheduled = true;
            scheduler.schedule(this);
            ALL_REGISTERED.add(this);
        }

        public @NotNull ServerTickRateManager tickRateManager() {
            return this.server.tickRateManager();
        }

        protected boolean haveTime() {
            return server.forceTicks || Util.getNanos() < (this.nextTickTimeNanos);
        }

        @Override
        public boolean hasTasks() {
            return !this.tasks.isEmpty();
        }

        @Override
        public boolean runTasks(final BooleanSupplier canContinue) {
            long tasksStart = Util.getNanos();
            final MultiWatchdogThread.RunningTick runningTick = new MultiWatchdogThread.RunningTick(tasksStart, this, Thread.currentThread());
            try {
                MultiWatchdogThread.WATCHDOG.dock(runningTick);
                boolean runFullTickTasks = runFullTickTasks(canContinue);
                if (!runFullTickTasks) {
                    this.retire();
                }
                return runFullTickTasks;
            } finally {
                MultiWatchdogThread.WATCHDOG.undock(runningTick);
            }
        }

        // Note: final so we don't override, this is for tasks scheduled to the back-bone instance only
        // if tick task implementation want to run a custom task system, override 'runTasks'
        public final boolean runFullTickTasks(BooleanSupplier canContinue) {
            Runnable run;
            while ((run = this.tasks.poll()) != null) {
                if (!canContinue.getAsBoolean()) break;
                run.run();
            }
            return !cancelled.get();
        }

        private void tickMspt(long start) {
            long totalProcessNanos = Util.getNanos() - start;
            this.tickTimes5s.add(this.tickCount, totalProcessNanos);
            this.tickTimes10s.add(this.tickCount, totalProcessNanos);
            this.tickTimes15s.add(this.tickCount, totalProcessNanos);
            this.tickTimes60s.add(this.tickCount, totalProcessNanos);
        }

        private void tickTps(long start) {
            if (++tickCount % SAMPLE_RATE == 0) {
                final long diff = start - tickSection;
                final BigDecimal currentTps = getScheduler().getTpsBase().divide(new BigDecimal(diff), 30, RoundingMode.HALF_UP);

                tps5s.add(currentTps, diff);
                tps1m.add(currentTps, diff);
                tps15s.add(currentTps, diff);
                tps10s.add(currentTps, diff);

                tickSection = start;
            }
        }

        // api utilities
        @Override
        public boolean isTicking() {
            return !this.cancelled.get() && this.tickCount >= 1;
        }

        public ResourceLocation getLocation() {
            return this.identifier;
        }

        @Override
        public @NotNull Component debugInfo() {
            return this.tick.debugInfo();
        }

        @Override
        public void sleep() {
            // make sure we can sleep and aren't sleeping already
            if (!shouldSleep() || isSleeping) return;
            synchronized (SLEEP_HANDLE) {
                isSleeping = true;
            }
        }

        @Override
        public void wake() {
            // don't wake if we aren't sleeping
            if (!isSleeping) return;
            synchronized (SLEEP_HANDLE) {
                isSleeping = false;
            }
        }

        public long getNextScheduledStart() {
            return getScheduledStart();
        }

        @Override
        public boolean isSleeping() {
            return isSleeping;
        }

        @Override
        public boolean shouldSleep() {
            return this.tick.shouldSleep();
        }

        @Override
        public NamespacedKey getIdentifier() {
            return CraftNamespacedKey.fromMinecraft(this.identifier);
        }

        @Override
        public Logger getLogger() {
            return LOGGER;
        }

        @Override
        public RollingAverage getTps1m() {
            return tps1m;
        }

        @Override
        public RollingAverage getTps5s() {
            return tps5s;
        }

        @Override
        public RollingAverage getTps10s() {
            return tps10s;
        }

        @Override
        public RollingAverage getTps15s() {
            return tps15s;
        }

        @Override
        public TickTimes getTickTimes5s() {
            return tickTimes5s;
        }

        @Override
        public TickTimes getTickTimes10s() {
            return tickTimes10s;
        }

        @Override
        public TickTimes getTickTimes15s() {
            return tickTimes15s;
        }

        @Override
        public TickTimes getTickTimes60s() {
            return tickTimes60s;
        }

        @Override
        public int getTickCount() {
            return tickCount;
        }

        @Override
        public void pushTask(final @NotNull Runnable task) {
            this.wake(); // wake from sleep
            this.tasks.add(task);
        }

        @Override
        public void retire() {
            if (Config.INSTANCE.debug.taskRetire) LOGGER.info("Retiring tick task {} from scheduler", this);
            getScheduler().scheduler.cancel(this);
        }

        @Override
        public int compareTo(@NotNull final FullTick o) {
            return Long.compare(this.constructorInit, o.constructorInit);
        }

        public void managedBlock(@NotNull BooleanSupplier stopCondition) {
            server.managedBlock(() -> MinecraftServer.throwIfFatalException() && stopCondition.getAsBoolean());
        }

        @Override
        public boolean cancel() {
            this.cancelled.set(true);
            ALL_REGISTERED.remove(this);
            return super.cancel();
        }

        @Override
        public String toString() {
            return this.identifier.toString();
        }
    }

    public static final class TickThreadFactory implements ThreadFactory {
        private static final IdGenerator ID_GENERATOR = new IdGenerator();
        private final int threadPriority;
        private final ThreadGroup group;

        public TickThreadFactory(int threadPriority) {
            this.threadPriority = threadPriority;
            this.group = new ThreadGroup("tick runner");
        }

        @Override
        public @NotNull Thread newThread(@NotNull final Runnable run) {
            TickRunner runner = new TickRunner(this.group, run, "Tick Runner #" + ID_GENERATOR.poll());
            runner.setPriority(this.threadPriority);
            runner.setUncaughtExceptionHandler((thread, throwable) -> {
                LOGGER.error("Uncaught exception in tick runner '{}'", thread.getName());
                LOGGER.error("", throwable);
            });
            return runner;
        }
    }
}
