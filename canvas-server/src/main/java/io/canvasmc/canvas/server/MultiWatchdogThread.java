package io.canvasmc.canvas.server;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.scheduler.TickScheduler;
import io.papermc.paper.FeatureHooks;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.configuration.GlobalConfiguration;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spigotmc.AsyncCatcher;
import org.spigotmc.RestartCommand;
import org.spigotmc.SpigotConfig;

// heavily modified version of the WatchdogThread class allowing multi-registration
// of threads through dedicated registration and docking/undocking
public class MultiWatchdogThread extends TickThread {
    public static final boolean DISABLE_WATCHDOG = Boolean.getBoolean("disable.watchdog");
    public static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));
    public static final Logger LOGGER = LoggerFactory.getLogger("Watchdog");
    private static final Queue<ThreadEntry> REGISTRY = new ConcurrentLinkedQueue<>();
    public static volatile boolean hasStarted;
    public static MultiWatchdogThread WATCHDOG;
    private final long earlyWarningEvery;
    private final long earlyWarningDelay;
    private final LinkedHashSet<RunningTick> ticks = new LinkedHashSet<>();
    private long timeoutTime;
    private boolean restart;
    private volatile boolean stopping;
    private final String BREAK = "------------------------------";

    private MultiWatchdogThread(long timeoutTime, boolean restart) {
        super("MutliLoop Watchdog Thread");
        this.timeoutTime = timeoutTime;
        this.restart = restart;
        this.earlyWarningEvery = Math.min(GlobalConfiguration.get().watchdog.earlyWarningEvery, timeoutTime);
        this.earlyWarningDelay = Math.min(GlobalConfiguration.get().watchdog.earlyWarningDelay, timeoutTime);
    }

    private static long monotonicMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    public static void doStart(int timeoutTime, boolean restart) {
        if (MultiWatchdogThread.WATCHDOG == null) {
            if (timeoutTime <= 0) timeoutTime = 300;
            MultiWatchdogThread.WATCHDOG = new MultiWatchdogThread(timeoutTime * 1000L, restart);
            MultiWatchdogThread.WATCHDOG.start();
        } else {
            MultiWatchdogThread.WATCHDOG.timeoutTime = timeoutTime * 1000L;
            MultiWatchdogThread.WATCHDOG.restart = restart;
        }
    }

    public static void doStop() {
        if (MultiWatchdogThread.WATCHDOG != null) {
            MultiWatchdogThread.WATCHDOG.stopping = true;
        }
    }

    // specifically for dedicated threads, not schedulable tasks.
    // for schedulables, use the 'RunningTick' api
    public static ThreadEntry register(ThreadEntry entry) {
        REGISTRY.add(entry);
        return entry;
    }

    private static void dumpThread(ThreadInfo thread) {
        LOGGER.error("------------------------------");

        if (thread == null) {
            LOGGER.error("Null ThreadInfo.");
            return;
        }
        LOGGER.error("Current Thread: {}", thread.getThreadName());
        LOGGER.error("\tPID: {} | Suspended: {} | Native: {} | State: {}", thread.getThreadId(), thread.isSuspended(), thread.isInNative(), thread.getThreadState());
        if (thread.getLockedMonitors().length != 0) {
            LOGGER.error("\tThread is waiting on monitor(s):");
            for (MonitorInfo monitor : thread.getLockedMonitors()) {
                LOGGER.error("\t\tLocked on:{}", monitor.getLockedStackFrame());
            }
        }
        LOGGER.error("\tStack:");

        for (StackTraceElement stack : io.papermc.paper.util.StacktraceDeobfuscator.INSTANCE.deobfuscateStacktrace(thread.getStackTrace())) {
            LOGGER.error("\t\t{}", stack);
        }
    }

    @Override
    public void run() {
        String brandName = ServerBuildInfo.buildInfo().brandName();
        String importantBrandName = brandName.toUpperCase(Locale.ROOT);
        String repository = "https://github.com/CraftCanvasMC/Canvas/";
        String discord = "https://canvasmc.io/discord";
        main_loop:
        while (!this.stopping) {
            long currentTime = MultiWatchdogThread.monotonicMillis();
            MinecraftServer server = MinecraftServer.getServer();
            for (final ThreadEntry threadEntry : REGISTRY) {
                if (threadEntry.lastTick != 0 && this.timeoutTime > 0 && MultiWatchdogThread.hasStarted && (!server.isRunning() || (currentTime > threadEntry.lastTick + this.earlyWarningEvery && !DISABLE_WATCHDOG))) {
                    boolean isLongTimeout = currentTime > threadEntry.lastTick + this.timeoutTime || (!server.isRunning() && !server.hasStopped() && currentTime > threadEntry.lastTick + 1000);
                    if (!isLongTimeout && (this.earlyWarningEvery <= 0 ||
                        !hasStarted || currentTime < threadEntry.lastEarlyWarning + this.earlyWarningEvery ||
                        currentTime < threadEntry.lastTick + this.earlyWarningDelay))
                        continue;
                    if (!isLongTimeout && server.hasStopped())
                        continue;
                    threadEntry.lastEarlyWarning = currentTime;
                    if (isLongTimeout) {
                        LOGGER.error(BREAK);
                        LOGGER.error("The {} has stopped responding! This is (probably) not a {} bug.", threadEntry.informalName, brandName);
                        LOGGER.error("If you see a plugin in the thread dump below, then please report it to that author");
                        LOGGER.error("\t *Especially* if it looks like HTTP or MySQL operations are occurring");
                        LOGGER.error("If you see a world save or edit, then it means you did far more than your server can handle at once");
                        LOGGER.error("\t If this is the case, consider increasing timeout-time in spigot.yml but note that this will replace the crash with LARGE lag spikes");
                        LOGGER.error("If you are unsure or still think this is a {} bug, please report this to {}issues or the discord, {}", brandName, repository, discord);
                        LOGGER.error("Be sure to include ALL relevant console errors and Minecraft crash reports");
                        LOGGER.error("{} version: {}", brandName, Bukkit.getServer().getVersion());

                        logOverflow();
                    } else {
                        LOGGER.error("--- DO NOT REPORT THIS TO {} - THIS IS NOT A BUG OR A CRASH  - {} ---", importantBrandName, Bukkit.getServer().getVersion());
                        LOGGER.error("The {} has not responded for {} seconds! Creating thread dump", threadEntry.informalName, (currentTime - threadEntry.lastTick) / 1000);
                    }
                    LOGGER.error(BREAK);
                    LOGGER.error("{} thread dump (Look for plugins here before reporting to {}!):", threadEntry.formalName, brandName);
                    FeatureHooks.dumpAllChunkLoadInfo(MinecraftServer.getServer(), isLongTimeout);
                    MultiWatchdogThread.dumpThread(ManagementFactory.getThreadMXBean().getThreadInfo(threadEntry.toWatch.threadId(), Integer.MAX_VALUE));
                    LOGGER.error(BREAK);

                    if (isLongTimeout) {
                        LOGGER.error("Entire Thread Dump:");
                        ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
                        for (ThreadInfo thread : threads) {
                            MultiWatchdogThread.dumpThread(thread);
                        }
                    } else {
                        LOGGER.error("--- DO NOT REPORT THIS TO {} - THIS IS NOT A BUG OR A CRASH ---", importantBrandName);
                    }

                    LOGGER.error(BREAK);

                    if (isLongTimeout) {
                        if (!server.hasStopped()) {
                            AsyncCatcher.enabled = false;
                            server.forceTicks = true;
                            if (this.restart) {
                                RestartCommand.addShutdownHook(SpigotConfig.restartScript);
                            }
                            server.abnormalExit = true;
                            server.safeShutdown(false, this.restart);
                            try {
                                //noinspection BusyWait
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (!server.hasStopped()) {
                                server.close();
                            }
                        }
                        break main_loop;
                    }
                }
            }

            if (MinecraftServer.getServer().hasStopped()) {
                continue;
            }

            final List<RunningTick> ticks;
            synchronized (this.ticks) {
                if (this.ticks.isEmpty()) {
                    continue;
                }
                ticks = new ArrayList<>(this.ticks);
            }

            final long now = System.nanoTime();

            for (final RunningTick tick : ticks) {
                final long elapsed = now - tick.lastPrint;
                if (elapsed <= TimeUnit.SECONDS.toNanos(Config.INSTANCE.ticking.watchdogLoggingTime)) {
                    continue;
                }
                tick.lastPrint = now;

                final double totalElapsedS = (double) (now - tick.start) / 1.0E9;

                LOGGER.error("tick handle with name '{}' has not responded in {}s", tick.handle.toString(), ONE_DECIMAL_PLACES.get().format(totalElapsedS));
                logOverflow();

                dumpThread(ManagementFactory.getThreadMXBean().getThreadInfo(tick.thread.threadId(), Integer.MAX_VALUE));
            }

            try {
                //noinspection BusyWait
                sleep(1000);
            } catch (InterruptedException ex) {
                this.interrupt();
            }
        }
    }

    private void logOverflow() {
        if (Level.lastPhysicsProblem != null) {
            LOGGER.error(BREAK);
            LOGGER.error("During the run of the server, a physics stackoverflow was supressed");
            LOGGER.error("near {}", Level.lastPhysicsProblem);
        }

        if (CraftServer.excessiveVelEx != null) {
            LOGGER.error(BREAK);
            LOGGER.error("During the run of the server, a plugin set an excessive velocity on an entity");
            LOGGER.error("This may be the cause of the issue, or it may be entirely unrelated");
            LOGGER.error(CraftServer.excessiveVelEx.getMessage());
            for (StackTraceElement stack : CraftServer.excessiveVelEx.getStackTrace()) {
                LOGGER.error("\t\t{}", stack);
            }
        }
    }

    public void dock(final RunningTick tick) {
        synchronized (this.ticks) {
            this.ticks.add(tick);
        }
    }

    public void undock(final RunningTick tick) {
        synchronized (this.ticks) {
            this.ticks.remove(tick);
        }
    }

    public static final class RunningTick {

        public final long start;
        public final TickScheduler.FullTick<?> handle;
        public final Thread thread;

        private long lastPrint;

        public RunningTick(final long start, final TickScheduler.FullTick<?> handle, final Thread thread) {
            this.start = start;
            this.handle = handle;
            this.thread = thread;
            this.lastPrint = start;
        }
    }

    public static final class ThreadEntry {
        private final Thread toWatch;
        private final String informalName;
        private final BooleanSupplier isTicking;
        private final BooleanSupplier isSleeping;
        public String formalName;
        private volatile long lastTick = 0L;
        private long lastEarlyWarning = 0L;

        public ThreadEntry(Thread toWatch, String informalName, String formalName, BooleanSupplier isTicking, BooleanSupplier isSleeping) {
            this.toWatch = toWatch;
            this.informalName = informalName;
            this.formalName = formalName;
            this.isTicking = isTicking;
            this.isSleeping = isSleeping;
        }

        public void doTick() {
            this.lastTick = MultiWatchdogThread.monotonicMillis();
        }
    }
}
