package io.canvasmc.canvas.server;

import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.RollingAverage;
import io.canvasmc.canvas.TickTimes;
import io.canvasmc.canvas.scheduler.TickLoopScheduler;
import io.canvasmc.canvas.scheduler.WrappedTickLoop;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportType;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TimeUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.canvasmc.canvas.scheduler.TickLoopScheduler.TIME_BETWEEN_TICKS;

public abstract class AbstractTickLoop extends TickLoopScheduler.AbstractTick implements WrappedTickLoop {
    public static final Object SLEEP_HANDLE = new Object();
    public final MinecraftServer server;
    public final Logger LOGGER;
    public final RollingAverage tps5s = new RollingAverage(5);
    public final RollingAverage tps10s = new RollingAverage(10);
    public final RollingAverage tps15s = new RollingAverage(15);
    public final RollingAverage tps1m = new RollingAverage(60);
    public final TickTimes tickTimes5s = new TickTimes(100);
    public final TickTimes tickTimes10s = new TickTimes(200);
    public final TickTimes tickTimes15s = new TickTimes(300);
    public final TickTimes tickTimes60s = new TickTimes(1200);
    protected final String name;
    protected final String debugName;
    public int tickCount;
    public long nextTickTimeNanos;
    public boolean mayHaveDelayedTasks;
    public long delayedTasksMaxNextTickTimeNanos;
    public boolean lagging = false;
    public boolean prepared = false;
    public int currentTick;
    public long lastTickNanos = Util.getNanos();
    protected volatile boolean running = false;
    protected volatile boolean ticking = false;
    protected long tickSection = 0;
    protected long lastTick = 0;
    protected long preTickNanos = 0L;
    protected long postTickNanos = 0L;
    protected long lastOverloadWarningNanos;
    protected long lastNanoTickTime = 0L;
    protected volatile boolean isSleeping = false;
    protected boolean wasSleeping = false;
    private volatile boolean processingTick = false;

    public AbstractTickLoop(final String formalName, final String debugName) {
        super();
        LOGGER = LoggerFactory.getLogger(formalName);
        this.name = formalName;
        this.debugName = debugName;
        this.server = MinecraftServer.getServer();
        MinecraftServer.getThreadedServer().loops.add(this);
    }

    public static @NotNull AbstractTickLoop getByName(String name) {
        for (final AbstractTickLoop loop : MinecraftServer.getThreadedServer().loops) {
            if (loop.name.equalsIgnoreCase(name)) {
                return loop;
            }
        }
        throw new IllegalArgumentException("Unable to locate AbstractTickLoop of state '" + name + "'");
    }

    public synchronized void scheduleToPool() {
        if (this.running) return;
        this.running = true;
        this.prepared = true;
        tickSection = Util.getNanos();
        nextTickTimeNanos = Util.getNanos();
        this.setScheduledStart(System.nanoTime() + TIME_BETWEEN_TICKS);
        TickLoopScheduler.getInstance().scheduler.schedule(this);
    }

    public void stopSpin() {
        this.running = false;
        this.prepared = false;
    }

    @Override
    public boolean blockTick() {
        long startNanos;
        long nanosecondsOverload;
        if (shutdown) {
            return false;
        }
        if (isSleeping) {
            synchronized (SLEEP_HANDLE) {
                if (!wasSleeping) LOGGER.info("Pausing tick-loop '{}'", this.debugName);
                wasSleeping = true;
                this.nextTickTimeNanos = Util.getNanos();
                this.lastOverloadWarningNanos = this.nextTickTimeNanos;
                startNanos = Util.getNanos();
                tickTps(startNanos);
                tickMspt(startNanos);
                return true;
            }
        } else if (wasSleeping) {
            wasSleeping = false;
            LOGGER.info("Waking tick-loop '{}'", this.debugName);
        }
        ticking = true;
        if (processingTick) {
            // another thread is running this tick, hard-crash, now.
            Throwable throwable = new RuntimeException("recursive tick call detected");
            MinecraftServer.LOGGER.error("Encountered an unexpected exception", throwable);
            CrashReport crashReport = MinecraftServer.constructOrExtractCrashReport(throwable);
            CrashReportCategory crashReportCategory = crashReport.addCategory("TickLoop");
            crashReportCategory.setDetail("FormalName", this.name);
            crashReportCategory.setDetail("DebugName", this.debugName);
            crashReportCategory.setDetail("TickCount", this.tickCount);
            crashReportCategory.setDetail("CurrentThread", this.owner);
            crashReportCategory.setDetail("LastThread", this.lastOwningThread);
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
        int processedPolledCount = 0;
        while (this.pollInternal() && !shutdown) processedPolledCount++;

        if (tickRateManager().isSprinting() && tickRateManager().checkShouldSprintThisTick()) {
            nanosecondsOverload = 0L;
            this.nextTickTimeNanos = Util.getNanos();
            this.lastOverloadWarningNanos = this.nextTickTimeNanos;
        } else {
            nanosecondsOverload = tickRateManager().nanosecondsPerTick();
            long diff = Util.getNanos() - this.nextTickTimeNanos;

            if (diff > MinecraftServer.OVERLOADED_THRESHOLD_NANOS + 20L * nanosecondsOverload && this.nextTickTimeNanos - this.lastOverloadWarningNanos >= MinecraftServer.OVERLOADED_WARNING_INTERVAL_NANOS + 100L * nanosecondsOverload) {
                long ticksBehind = diff / nanosecondsOverload;

                if (server.server.getWarnOnOverload()) {
                    MinecraftServer.LOGGER.warn("Can't keep up! Is the {} overloaded? Running {}ms or {} ticks behind", this.debugName, diff / TimeUtil.NANOSECONDS_PER_MILLISECOND, ticksBehind);
                    if (Config.INSTANCE.broadcastServerTicksBehindToOps) {
                        for (final ServerPlayer player : server.getPlayerList().players) {
                            if (player.getBukkitEntity().isOp()) {
                                player.getBukkitEntity().sendMessage(Component.text(String.format("Can't keep up! Is the %s overloaded? Running %sms or %s ticks behind", this.debugName, diff / TimeUtil.NANOSECONDS_PER_MILLISECOND, ticksBehind), TextColor.color(255, 161, 14), TextDecoration.BOLD));
                            }
                        }
                    }
                }
                this.nextTickTimeNanos += ticksBehind * nanosecondsOverload;
                this.lastOverloadWarningNanos = this.nextTickTimeNanos;
            }
        }

        startNanos = Util.getNanos();
        tickTps(startNanos);

        boolean doesntHaveTime = nanosecondsOverload == 0L;

        lastTick = startNanos;
        postTickNanos = nanosecondsOverload;
        this.nextTickTimeNanos += nanosecondsOverload;

        this.lastTickNanos = Util.getNanos();
        this.preTickNanos = this.lastTickNanos;
        this.blockTick(doesntHaveTime ? () -> false : this::haveTime, tickCount++);

        this.lastNanoTickTime = Util.getNanos() - preTickNanos;

        this.mayHaveDelayedTasks = true;
        this.delayedTasksMaxNextTickTimeNanos = Math.max(Util.getNanos() + postTickNanos, this.nextTickTimeNanos);
        if (!org.purpurmc.purpur.PurpurConfig.tpsCatchup) {
            this.nextTickTimeNanos = lastTick + postTickNanos;
            this.delayedTasksMaxNextTickTimeNanos = nextTickTimeNanos;
        }
        tickMspt(startNanos);
        processingTick = false;
        return this.running; // respect if the tick retired the loop
    }

    private void tickMspt(long start) {
        long totalProcessNanos = Util.getNanos() - start;
        this.tickTimes5s.add(this.tickCount, totalProcessNanos);
        this.tickTimes10s.add(this.tickCount, totalProcessNanos);
        this.tickTimes15s.add(this.tickCount, totalProcessNanos);
        this.tickTimes60s.add(this.tickCount, totalProcessNanos);
    }

    private void tickTps(long start) {
        if (++currentTick % MinecraftServer.SAMPLE_INTERVAL == 0) {
            final long diff = start - tickSection;
            final java.math.BigDecimal currentTps = MinecraftServer.TPS_BASE.divide(new java.math.BigDecimal(diff), 30, java.math.RoundingMode.HALF_UP);
            tps5s.add(currentTps, diff);
            tps1m.add(currentTps, diff);
            tps15s.add(currentTps, diff);
            tps10s.add(currentTps, diff);

            lagging = tps5s.getAverage() < org.purpurmc.purpur.PurpurConfig.laggingThreshold;
            tickSection = start;
        }
    }

    protected abstract void blockTick(BooleanSupplier hasTimeLeft, int tickCount);

    @Override
    public void managedBlock(@NotNull BooleanSupplier stopCondition) {
        server.managedBlock(() -> MinecraftServer.throwIfFatalException() && stopCondition.getAsBoolean());
    }

    public double getNanoSecondsFromLastTick() {
        return this.lastNanoTickTime;
    }

    public @NotNull TickTask wrapRunnable(@NotNull Runnable runnable) {
        if (server.hasStopped && Thread.currentThread().equals(server.shutdownThread)) {
            runnable.run();
            runnable = () -> {
            };
        }
        return new TickTask(this.tickCount, runnable);
    }

    protected boolean haveTime() {
        return server.forceTicks || Util.getNanos() < (this.mayHaveDelayedTasks ? this.delayedTasksMaxNextTickTimeNanos : this.nextTickTimeNanos);
    }

    public ServerTickRateManager tickRateManager() {
        return server.tickRateManager();
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public boolean isTicking() {
        return this.ticking;
    }

    public String location() {
        return this.getDebugName();
    }

    @Override
    public @NotNull Component debugInfo() {
        return Component.empty();
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

    @Override
    public boolean isSleeping() {
        return isSleeping;
    }

    @Override
    public boolean shouldSleep() {
        return true;
    }

    public Thread getRunningThread() {
        return this.owner;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String getFormalName() {
        return getName();
    }

    @Override
    public String getDebugName() {
        return debugName;
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
    public void pushTask(final Runnable task) {
        this.scheduleOnMain(task);
    }

    @Override
    public void retire() {
        TickLoopScheduler.getInstance().scheduler.tryRetire(this);
        stopSpin();
    }
}
