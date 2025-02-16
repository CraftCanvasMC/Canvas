package io.canvasmc.canvas.server;

import ca.spottedleaf.moonrise.common.util.TickThread;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import io.canvasmc.canvas.spark.MultiLoopThreadDumper;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.TickTask;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spigotmc.WatchdogThread;

public abstract class AbstractTickLoop<T extends TickThread, S> extends ReentrantBlockableEventLoop<TickTask> {
    public final Logger LOGGER;
    public final double[] recentTps = new double[4];
    public final MinecraftServer.RollingAverage tps5s = new MinecraftServer.RollingAverage(5);
    public final MinecraftServer.RollingAverage tps1 = new MinecraftServer.RollingAverage(60);
    public final MinecraftServer.RollingAverage tps5 = new MinecraftServer.RollingAverage(60 * 5);
    public final MinecraftServer.RollingAverage tps15 = new MinecraftServer.RollingAverage(60 * 15);
    public final MinecraftServer.TickTimes tickTimes10s = new MinecraftServer.TickTimes(200);
    private final String debugName;
    private final TickThreadConstructor<T, S> constructor;
    public volatile long lastWatchdogTick;
    public int tickCount;
    public boolean waitingForNextTick;
    public long nextTickTimeNanos;
    public long idleTimeNanos;
    public boolean mayHaveDelayedTasks;
    public long delayedTasksMaxNextTickTimeNanos;
    public boolean lagging = false;
    public boolean prepared = false;
    public int currentTick;
    protected Thread owner;
    protected volatile boolean running;
    protected volatile boolean ticking = false;
    private long tickSection = 0;
    private long lastOverloadWarningNanos;
    private long taskExecutionStartNanos;
    private long lastTick = 0;
    private long lastNanoTickTime = 0L;
    private long preTickNanos = 0L;
    private long postTickNanos = 0L;
    private Consumer<T> threadModifier = null;
    private Runnable preblockstart = null;

    public AbstractTickLoop(final String name, final String debugName) {
        //noinspection unchecked
        this(name, debugName, (r, n, _) -> (T) new TickThread(r, n));
    }

    public AbstractTickLoop(final String name, final String debugName, TickThreadConstructor<T, S> constructor) {
        super(name);
        LOGGER = LoggerFactory.getLogger(name);
        this.debugName = debugName;
        this.constructor = constructor;
        WatchdogThread.registerWatcher(this);
        LOGGER.info("Loaded {} to threaded context", debugName);
    }

    public void setThreadModifier(Consumer<T> threadModifier) {
        this.threadModifier = threadModifier;
    }

    public void setPreBlockStart(Runnable preBlockStart) {
        this.preblockstart = preBlockStart;
    }

    public T start(Function<S, AbstractTick> tick) {
        //noinspection unchecked
        S thisAsS = (S) this;
        T thread = this.constructor.construct(() -> this.spin(tick.apply(thisAsS)), this.name(), thisAsS);
        if (this.threadModifier != null) {
            this.threadModifier.accept(thread);
        }
        LOGGER.info("Spinning {} tick-loop on {}", this.debugName, thread.getClass().getSimpleName());
        MultiLoopThreadDumper.REGISTRY.add(thread);
        thread.start();
        return thread;
    }

    public void spin(AbstractTick consumer) {
        try {
            this.running = true;
            this.owner = Thread.currentThread();
            Arrays.fill(this.recentTps, 20);
            this.lastWatchdogTick = WatchdogThread.monotonicMillis();
            if (this.preblockstart != null) {
                this.preblockstart.run();
            }
            this.prepared = true;

            this.managedBlock(() -> MinecraftServer.getServer().isTicking());
            ticking = true;

            tickSection = Util.getNanos();
            nextTickTimeNanos = Util.getNanos();
            while (running) {
                this.tickSection = blockTick(tickSection, consumer);
            }
        } catch (Throwable throwable) {
            TickLoopConstantsUtils.hardCrashCatch(throwable);
        } finally {
            LOGGER.info("Successfully halted tick-loop of {}", this.debugName);
        }

    }

    public void stopSpin(boolean waitUntilComplete) {
        if (!this.running) {
            throw new RuntimeException("Tried to stop spinning a tick loop that hasn't started spinning yet!");
        }
        this.running = false;
        if (waitUntilComplete) {
            Thread currentThread = Thread.currentThread();
            if (currentThread.equals(this.getRunningThread())) {
                return;
            }
            try {
                CompletableFuture<Void> killThreadFuture = CompletableFuture.runAsync(() -> {
                    try {
                        this.getRunningThread().join();
                    } catch (Throwable e) {
                        throw new RuntimeException("Server encountered an unexpected exception when waiting for level thread to terminate!", e);
                    }
                });
                killThreadFuture.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException timeoutException) {
                LOGGER.error("Timed out waiting for {} to terminate, force-killing.", this.debugName);
                this.getRunningThread().interrupt();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public long blockTick(long tickSection, final @NotNull AbstractTick tick) {
        long currentTime;
        long i;

        if (tickRateManager().isSprinting() && tickRateManager().checkShouldSprintThisTick()) {
            i = 0L;
            this.nextTickTimeNanos = Util.getNanos();
            this.lastOverloadWarningNanos = this.nextTickTimeNanos;
        } else {
            i = tickRateManager().nanosecondsPerTick();
            long j = Util.getNanos() - this.nextTickTimeNanos;

            if (j > MinecraftServer.OVERLOADED_THRESHOLD_NANOS + 20L * i && this.nextTickTimeNanos - this.lastOverloadWarningNanos >= MinecraftServer.OVERLOADED_WARNING_INTERVAL_NANOS + 100L * i) {
                long k = j / i;

                if (MinecraftServer.getServer().server.getWarnOnOverload()) {
                    MinecraftServer.LOGGER.warn("Can't keep up! Is the {} overloaded? Running {}ms or {} ticks behind", this.debugName, j / TimeUtil.NANOSECONDS_PER_MILLISECOND, k);
                }
                this.nextTickTimeNanos += k * i;
                this.lastOverloadWarningNanos = this.nextTickTimeNanos;
            }
        }

        currentTime = Util.getNanos();
        if (++currentTick % MinecraftServer.SAMPLE_INTERVAL == 0) {
            final long diff = currentTime - tickSection;
            final java.math.BigDecimal currentTps = MinecraftServer.TPS_BASE.divide(new java.math.BigDecimal(diff), 30, java.math.RoundingMode.HALF_UP);
            tps5s.add(currentTps, diff);
            tps1.add(currentTps, diff);
            tps5.add(currentTps, diff);
            tps15.add(currentTps, diff);

            this.recentTps[0] = tps5s.getAverage();
            this.recentTps[1] = tps1.getAverage();
            this.recentTps[2] = tps5.getAverage();
            this.recentTps[3] = tps15.getAverage();
            lagging = recentTps[0] < org.purpurmc.purpur.PurpurConfig.laggingThreshold;
            tickSection = currentTime;
        }

        boolean flag = i == 0L;

        lastTick = currentTime;
        postTickNanos = i;
        this.nextTickTimeNanos += i;

        this.preTickNanos = Util.getNanos();
        this.lastWatchdogTick = WatchdogThread.monotonicMillis();
        tick.process(flag ? () -> false : this::haveTime, tickCount++);
        this.tickTimes10s.add(this.tickCount, Util.getNanos() - currentTime);

        this.lastNanoTickTime = Util.getNanos() - preTickNanos;

        this.mayHaveDelayedTasks = true;
        this.delayedTasksMaxNextTickTimeNanos = Math.max(Util.getNanos() + postTickNanos, this.nextTickTimeNanos);
        if (!org.purpurmc.purpur.PurpurConfig.tpsCatchup) {
            this.nextTickTimeNanos = lastTick + postTickNanos;
            this.delayedTasksMaxNextTickTimeNanos = nextTickTimeNanos;
        }
        this.startMeasuringTaskExecutionTime();
        this.waitUntilNextTick();
        this.finishMeasuringTaskExecutionTime();
        return tickSection;
    }

    private void startMeasuringTaskExecutionTime() {
        if (MinecraftServer.getServer().isTickTimeLoggingEnabled()) {
            this.taskExecutionStartNanos = Util.getNanos();
            this.idleTimeNanos = 0L;
        }

    }

    private void finishMeasuringTaskExecutionTime() {
        if (MinecraftServer.getServer().isTickTimeLoggingEnabled()) {
            SampleLogger samplelogger = MinecraftServer.getServer().getTickTimeLogger();

            samplelogger.logPartialSample(Util.getNanos() - this.taskExecutionStartNanos - this.idleTimeNanos, TpsDebugDimensions.SCHEDULED_TASKS.ordinal());
            samplelogger.logPartialSample(this.idleTimeNanos, TpsDebugDimensions.IDLE.ordinal());
        }

    }

    @Override
    public @VisibleAfterSpin @NotNull Thread getRunningThread() {
        return this.owner;
    }

    @Override
    public void managedBlock(@NotNull BooleanSupplier stopCondition) {
        super.managedBlock(() -> MinecraftServer.throwIfFatalException() && stopCondition.getAsBoolean());
    }

    public double getNanoSecondsFromLastTick() {
        return this.lastNanoTickTime;
    }

    protected void waitUntilNextTick() {
        this.runAllTasks();
        this.waitingForNextTick = true;

        try {
            this.managedBlock(() -> !this.haveTime());
        } finally {
            this.waitingForNextTick = false;
        }

    }

    @Override
    public void waitForTasks() {
        boolean flag = MinecraftServer.getServer().isTickTimeLoggingEnabled();
        long i = flag ? Util.getNanos() : 0L;
        long j = this.waitingForNextTick ? this.nextTickTimeNanos - Util.getNanos() : 100000L;

        LockSupport.parkNanos(name(), j);
        if (flag) {
            this.idleTimeNanos += Util.getNanos() - i;
        }

    }

    @Override
    public @NotNull TickTask wrapRunnable(@NotNull Runnable runnable) {
        if (MinecraftServer.getServer().hasStopped && Thread.currentThread().equals(MinecraftServer.getServer().shutdownThread)) {
            runnable.run();
            runnable = () -> {
            };
        }
        return new TickTask(this.tickCount, runnable);
    }

    protected boolean shouldRun(@NotNull TickTask ticktask) {
        return ticktask.getTick() + 3 < this.tickCount || this.haveTime();
    }

    protected boolean haveTime() {
        return MinecraftServer.getServer().forceTicks || this.runningTask() || Util.getNanos() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTimeNanos : this.nextTickTimeNanos);
    }

    @Override
    public boolean pollTask() {
        boolean flag = this.pollInternal();

        this.mayHaveDelayedTasks = flag;
        return flag;
    }

    public boolean pollInternal() {
        return super.pollTask();
    }

    public abstract ServerTickRateManager tickRateManager();

    public long getLastWatchdogTick() {
        return lastWatchdogTick;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isTicking() {
        return this.ticking;
    }
}
