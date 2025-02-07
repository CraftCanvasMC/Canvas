package io.canvasmc.canvas.server.network;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.canvasmc.canvas.server.TickLoopConstantsUtils;
import io.canvasmc.canvas.server.VisibleAfterSpin;
import io.canvasmc.canvas.server.level.WatchdogWatcher;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
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

public class PlayerJoinThread extends ReentrantBlockableEventLoop<TickTask> implements WatchdogWatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("AsyncPlayerJoinThread");
    private static PlayerJoinThread INSTANCE = null;
    public final double[] recentTps = new double[4];
    public final MinecraftServer.RollingAverage tps5s = new MinecraftServer.RollingAverage(5);
    public final MinecraftServer.RollingAverage tps1 = new MinecraftServer.RollingAverage(60);
    public final MinecraftServer.RollingAverage tps5 = new MinecraftServer.RollingAverage(60 * 5);
    public final MinecraftServer.RollingAverage tps15 = new MinecraftServer.RollingAverage(60 * 15);
    private final ConcurrentLinkedQueue<Connection> activeConnections = new ConcurrentLinkedQueue<>();
    private final long catchupTime = 0;
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
    private volatile boolean running;
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

    public PlayerJoinThread(final String name) {
        super(name);
        INSTANCE = this;
    }

    public static PlayerJoinThread getInstance() {
        return INSTANCE;
    }

    public void start() {
        this.running = true;
        new TickThread(this::spin, "AsyncPlayerJoinThread").start();
    }

    public void stopAcceptingConnections() {
        this.running = false;
    }

    public void run() {
        Iterator<Connection> iterator = this.activeConnections.iterator();

        while (iterator.hasNext()) {
            Connection connection = iterator.next();
            if (connection.getPhase().equals(ConnectionHandlePhases.PLAY)) {
                iterator.remove();
                continue;
            }
            if (!connection.isConnecting()) {
                if (connection.isConnected()) {
                    try {
                        connection.tick();
                    } catch (Exception var7) {
                        if (connection.isMemoryConnection()) {
                            throw new ReportedException(CrashReport.forThrowable(var7, "Ticking memory connection"));
                        }

                        LOGGER.warn("Failed to handle packet for {}", connection.getLoggableAddress(MinecraftServer.getServer().logIPs()), var7);
                        Component component = Component.literal("Internal server error");
                        connection.send(new ClientboundDisconnectPacket(component), PacketSendListener.thenRun(() -> connection.disconnect(component)));
                        connection.setReadOnly();
                    }
                } else {
                    if (connection.preparing) continue;
                    iterator.remove();
                    connection.handleDisconnection();
                }
            }
        }
    }

    public void add(@NotNull Connection connection) {
        setPhase(connection, ConnectionHandlePhases.JOIN);
        this.activeConnections.add(connection);
    }

    public void setPhase(@NotNull Connection connection, ConnectionHandlePhases phase) {
        connection.setPhase(phase);
    }

    public void spin() {
        try {
            this.running = true;
            this.owner = Thread.currentThread();
            Arrays.fill(this.recentTps, 20);
            this.prepared = true;

            this.managedBlock(() -> MinecraftServer.getServer().isTicking());
            ticking = true;

            tickSection = Util.getNanos();
            nextTickTimeNanos = Util.getNanos();
            while (running) {
                this.tickSection = blockSchedulerTick(tickSection, this::run);
            }
        } catch (Throwable throwable) {
            TickLoopConstantsUtils.hardCrashCatch(throwable);
        } finally {
            LOGGER.info("Successfully terminated async join thread");
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
        return "AsyncPlayerJoinThread";
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

        LockSupport.parkNanos("parking join thread", j);
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
