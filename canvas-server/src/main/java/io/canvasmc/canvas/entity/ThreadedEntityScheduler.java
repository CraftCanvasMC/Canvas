package io.canvasmc.canvas.entity;

import com.destroystokyo.paper.event.server.ServerExceptionEvent;
import com.destroystokyo.paper.exception.ServerInternalException;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.server.TickLoopConstantsUtils;
import io.canvasmc.canvas.server.VisibleAfterSpin;
import io.canvasmc.canvas.server.level.WatchdogWatcher;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.WatchdogThread;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

// runs at tick-rate matching the main thread. will not have independent rate, just independent scheduling
public class ThreadedEntityScheduler extends ReentrantBlockableEventLoop<TickTask> implements WatchdogWatcher {
    public static final ThreadGroup THREAD_GROUP = new ThreadGroup("entity");
    private static final Logger LOGGER = LogManager.getLogger("ThreadedEntityScheduler");
    public final double[] recentTps = new double[4];
    public final MinecraftServer.RollingAverage tps5s = new MinecraftServer.RollingAverage(5);
    public final MinecraftServer.RollingAverage tps1 = new MinecraftServer.RollingAverage(60);
    public final MinecraftServer.RollingAverage tps5 = new MinecraftServer.RollingAverage(60 * 5);
    public final MinecraftServer.RollingAverage tps15 = new MinecraftServer.RollingAverage(60 * 15);
    private final long catchupTime = 0;
    public volatile long lastWatchdogTick;
    public int tickCount;
    public boolean waitingForNextTick;
    public long nextTickTimeNanos;
    public long idleTimeNanos;
    public boolean mayHaveDelayedTasks;
    public long delayedTasksMaxNextTickTimeNanos;
    public boolean lagging = false;
    public boolean running = false;
    public boolean prepared = false;
    public int currentTick;
    protected Thread owner;
    private long tickSection = 0;
    private long lastOverloadWarningNanos;
    private long taskExecutionStartNanos;
    private long lastTick = 0;
    private long lastNanoTickTime = 0L;
    private long preTickNanos = 0L;
    private long postTickNanos = 0L;
    private long lastMidTickExecute;
    private long lastMidTickExecuteFailure;
    private volatile boolean ticking = false;

    public ThreadedEntityScheduler(@NotNull final String name) {
        super(name);
    }

    public void tickEntities() {
        for (final ServerLevel level : MinecraftServer.getServer().getAllLevels()) {
            level.entityTickList.forEach((entity) -> {
                if (Config.shouldCheckMasks && Config.COMPILED_LOCATIONS.contains(entity.getTypeLocation())) {
                    int lived = entity.tickCount;
                    if (!entity.getMask().shouldTick || lived % entity.getMask().tickRate != 0) {
                        return;
                    }
                }
                if (!entity.isRemoved()) {
                    if (!level.tickRateManager().isEntityFrozen(entity)) {
                        entity.checkDespawn();
                        Entity vehicle = entity.getVehicle();
                        if (vehicle != null) {
                            if (!vehicle.isRemoved() && vehicle.hasPassenger(entity)) {
                                return;
                            }

                            entity.stopRiding();
                        }

                        try {
                            level.tickNonPassenger(entity);
                        } catch (Throwable var6) {
                            final String msg = String.format("Entity threw exception at %s:%s,%s,%s", entity.level().getWorld().getName(), entity.getX(), entity.getY(), entity.getZ());
                            MinecraftServer.LOGGER.error(msg, var6);
                            level.getCraftServer().getPluginManager().callEvent(new ServerExceptionEvent(new ServerInternalException(msg, var6)));
                            entity.discard(EntityRemoveEvent.Cause.DISCARD);
                        }
                        level.moonrise$midTickTasks();
                    }
                }
            });
        }
    }

    public void spin() {
        try {
            this.running = true;
            this.owner = Thread.currentThread();
            WatchdogThread.tickEntityThread(this);
            Arrays.fill(this.recentTps, 20);
            this.prepared = true;

            this.managedBlock(() -> MinecraftServer.getServer().isTicking());
            ticking = true;

            tickSection = Util.getNanos();
            nextTickTimeNanos = Util.getNanos();
            while (running) {
                this.tickSection = blockSchedulerTick(tickSection, this::tickEntities);
            }
        } catch (Throwable throwable) {
            TickLoopConstantsUtils.hardCrashCatch(throwable);
        } finally {
            LOGGER.info("Successfully terminated entity scheduler");
        }

    }

    public long blockSchedulerTick(long tickSection, final @NotNull Runnable entityTick) {
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
                    MinecraftServer.LOGGER.warn("Can't keep up! Is the entity scheduler overloaded? Running {}ms or {} ticks behind", j / TimeUtil.NANOSECONDS_PER_MILLISECOND, k);
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
        WatchdogThread.tickEntityThread(this);
        entityTick.run();

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
    public String getName() {
        return "EntityScheduler";
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

        LockSupport.parkNanos("parking entity thread", j);
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

    public ServerTickRateManager tickRateManager() {
        return MinecraftServer.getServer().tickRateManager();
    }
}
