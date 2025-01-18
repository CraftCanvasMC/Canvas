package io.canvasmc.canvas.server.level;

import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.server.LevelTickProcessor;
import io.canvasmc.canvas.server.VisibleAfterSpin;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.debugchart.SampleLogger;
import net.minecraft.util.debugchart.TpsDebugDimensions;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.spigotmc.WatchdogThread;

public abstract class MinecraftServerWorld extends ReentrantBlockableEventLoop<TickTask> implements WatchdogWatcher, TickRateManagerInstance {
    private static final Logger LOGGER = LogManager.getLogger("MinecraftServerWorld");
    public final double[] recentTps = new double[4];
    public final MinecraftServer.RollingAverage tps5s = new MinecraftServer.RollingAverage(5);
    public final MinecraftServer.RollingAverage tps1 = new MinecraftServer.RollingAverage(60);
    public final MinecraftServer.RollingAverage tps5 = new MinecraftServer.RollingAverage(60 * 5);
    public final MinecraftServer.RollingAverage tps15 = new MinecraftServer.RollingAverage(60 * 15);
    protected final ServerTickRateManager tickRateManager;
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

    public MinecraftServerWorld(final String name) {
        super(name);
        this.tickRateManager = new ServerTickRateManager(this);
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

    public void spin() {
        try {
            if (!(this instanceof ServerLevel serverLevel)) {
                throw new RuntimeException("MinecraftServerWorld#spin() was called from a non-ServerLevel instance!");
            }
            LOGGER.info("[ThreadedServer] Spinning ServerLevel, {}", serverLevel.dimension().location());

            this.running = true;
            this.owner = Thread.currentThread();
            WatchdogThread.tickLevel(serverLevel);
            if (Config.INSTANCE.useLevelThreadsAsChunkSourceMain) serverLevel.chunkSource.mainThread = this.owner;
            Arrays.fill(this.recentTps, 20);
            this.prepared = true;

            // Block the current thread until the MinecraftServer has started the first tick.
            this.managedBlock(() -> MinecraftServer.getServer().isTicking());
            ticking = true;

            tickSection = Util.getNanos();
            nextTickTimeNanos = Util.getNanos();
            while (running) {
                this.tickSection = blockLevel(tickSection, serverLevel, serverLevel::tick);
            }
        } catch (Throwable throwable) {
            //noinspection removal
            if (throwable instanceof ThreadDeath) {
                MinecraftServer.LOGGER.error("World thread terminated by WatchDog due to hard crash", throwable);
                return;
            }
            MinecraftServer.LOGGER.error("Encountered an unexpected exception", throwable);
            CrashReport crashreport = MinecraftServer.constructOrExtractCrashReport(throwable);

            MinecraftServer.getServer().fillSystemReport(crashreport.getSystemReport());
            Path path = MinecraftServer.getServer().getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");

            if (crashreport.saveToFile(path, ReportType.CRASH)) {
                MinecraftServer.LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
            } else {
                MinecraftServer.LOGGER.error("We were unable to save this crash report to disk.");
            }

            MinecraftServer.getServer().onServerCrash(crashreport);

            try {
                MinecraftServer.getServer().stopped = true;
                MinecraftServer.getServer().stopServer();
            } catch (Throwable throwable3) {
                MinecraftServer.LOGGER.error("Exception stopping the server(via level thread)", throwable3);
            } finally {
                if (MinecraftServer.getServer().services.profileCache() != null) {
                    MinecraftServer.getServer().services.profileCache().clearExecutor();
                }
            }
        } finally {
            LOGGER.info("Successfully terminated level {}", this.level().dimension().location().toString());
        }

    }

    public void stopSpin() {
        running = false;
    }

    public long getTickSection() {
        return tickSection;
    }

    public int getCurrentTick() {
        return currentTick;
    }

    public long blockLevel(long tickSection, final ServerLevel serverLevel, final @NotNull LevelTickProcessor tickProcessor) {
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
                    MinecraftServer.LOGGER.warn("Can't keep up! Is the level overloaded? Running {}ms or {} ticks behind on level-thread: {}", j / TimeUtil.NANOSECONDS_PER_MILLISECOND, k, serverLevel.dimension().location());
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
        WatchdogThread.tickLevel(serverLevel);
        tickProcessor.process(flag ? () -> false : this::haveTime, tickCount);

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

        LockSupport.parkNanos(level(), j);
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
        if (super.pollTask()) {
            return true;
        } else {
            boolean ret = false;
            if (tickRateManager().isSprinting() || this.haveTime()) {
                ServerLevel worldserver = level();

                if (worldserver.getChunkSource().pollTask()) {
                    ret = true;
                }
            }

            return ret;
        }
    }

    public ServerLevel level() {
        return (ServerLevel) this;
    }

    @Override
    public String getName() {
        return "level(" + level().dimension().location().getPath() + ")";
    }

    public boolean isTicking() {
        return ticking;
    }

    @Override
    public CommandSourceStack createCommandSourceStack() {
        return MinecraftServer.getServer().createCommandSourceStack();
    }

    @Override
    public void onTickRateChanged() {
        MinecraftServer.getServer().onTickRateChanged();
    }

    @Override
    public void broadcastPacketsToPlayers(final Packet<?> packet) {
        for (final Player player : this.level().players()) {
            ((ServerPlayer) player).connection.send(packet);
        }
    }

    @Override
    public void skipTickWait() {
        this.delayedTasksMaxNextTickTimeNanos = Util.getNanos();
        this.nextTickTimeNanos = Util.getNanos();
    }

    public boolean isLevelThread() {
        return Thread.currentThread() instanceof LevelThread;
    }
}
