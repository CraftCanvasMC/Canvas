package io.canvasmc.canvas.server;

import com.google.common.collect.Sets;
import io.canvasmc.canvas.CanvasBootstrap;
import io.canvasmc.canvas.LevelAccess;
import io.canvasmc.canvas.ThreadedBukkitServer;
import io.canvasmc.canvas.scheduler.MultithreadedTickScheduler;
import io.canvasmc.canvas.scheduler.TickLoopScheduler;
import io.canvasmc.canvas.spark.MultiLoopThreadDumper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import net.minecraft.CrashReport;
import net.minecraft.ReportType;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThreadedServer implements ThreadedBukkitServer {
    public static final Logger LOGGER = LoggerFactory.getLogger("ThreadedServer");
    public static BooleanSupplier SHOULD_KEEP_TICKING;
    protected final Set<AbstractTickLoop> loops;
    private final List<ServerLevel> levels = new CopyOnWriteArrayList<>();
    private final DedicatedServer server;
    public MultiWatchdogThread.ThreadEntry watchdogEntry;
    private long tickSection;
    private boolean started = false;

    public ThreadedServer(DedicatedServer server) {
        this.server = server;
        this.loops = Collections.synchronizedSet(Sets.newLinkedHashSet());
    }

    @Override
    public @Unmodifiable List<World> getWorlds() {
        return this.levels.stream().map(Level::getWorld).collect(Collectors.toUnmodifiableList());
    }

    @Override
    public LevelAccess getLevelAccess(final World world) {
        return ((CraftWorld) world).getHandle();
    }

    @Override
    public MultithreadedTickScheduler getScheduler() {
        return TickLoopScheduler.getInstance();
    }

    @Override
    public void scheduleOnMain(final Runnable runnable) {
        this.server.scheduleOnMain(runnable);
    }

    public List<ServerLevel> getThreadedWorlds() {
        return levels;
    }

    public boolean hasStarted() {
        return started;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public void spin() {
        try {
            MultiLoopThreadDumper.REGISTRY.add(Thread.currentThread().getName());
            MultiLoopThreadDumper.REGISTRY.add("ls_wg "); // add linear-scaling world-gen workers
            MultiLoopThreadDumper.REGISTRY.add("tick scheduler");
            ThreadedBukkitServer.setInstance(this);

            if (!server.initServer()) {
                throw new IllegalStateException("Failed to initialize server");
            }

            TickLoopScheduler.start();
            this.started = true;
            this.server.nextTickTimeNanos = Util.getNanos();
            this.server.statusIcon = this.server.loadStatusIcon().orElse(null);
            this.server.status = this.server.buildServerStatus();

            LOGGER.info("Running delayed init tasks");
            this.server.server.getScheduler().mainThreadHeartbeat();

            final long actualDoneTimeMs = System.currentTimeMillis() - CanvasBootstrap.BOOT_TIME.toEpochMilli();
            LOGGER.info("Done ({})! For help, type \"help\"", String.format(java.util.Locale.ROOT, "%.3fs", actualDoneTimeMs / 1_000.00D));
            this.server.server.spark.enableBeforePlugins();
            this.watchdogEntry = MultiWatchdogThread.register(new MultiWatchdogThread.ThreadEntry(this.server.serverThread, "main thread", "Server", this.server::isTicking, () -> false));
            this.watchdogEntry.doTick();

            MultiWatchdogThread.hasStarted = true;
            //noinspection removal
            Arrays.fill(this.server.recentTps, 20);
            tickSection = Util.getNanos();
            if (io.papermc.paper.configuration.GlobalConfiguration.isFirstStart) {
                LOGGER.info("*************************************************************************************");
                LOGGER.info("This is the first time you're starting this server.");
                LOGGER.info("It's recommended you read our 'Getting Started' documentation for guidance.");
                LOGGER.info("View this and more helpful information here: https://docs.papermc.io/paper/next-steps");
                LOGGER.info("*************************************************************************************");
            }

            if (org.purpurmc.purpur.configuration.transformation.VoidDamageHeightMigration.HAS_BEEN_REGISTERED) {
                try {
                    org.purpurmc.purpur.PurpurConfig.config.save((File) this.server.options.valueOf("purpur-settings"));
                } catch (IOException ex) {
                    Bukkit.getLogger().log(java.util.logging.Level.SEVERE, "Could not save " + this.server.options.valueOf("purpur-settings"), ex);
                }
            }

            if (!Boolean.getBoolean("Purpur.IReallyDontWantStartupCommands") && !org.purpurmc.purpur.PurpurConfig.startupCommands.isEmpty()) {
                LOGGER.info("Purpur: Running startup commands specified in purpur.yml.");
                for (final String startupCommand : org.purpurmc.purpur.PurpurConfig.startupCommands) {
                    LOGGER.info("Purpur: Running the following command: \"{}\"", startupCommand);
                    this.server.handleConsoleInput(startupCommand, this.server.createCommandSourceStack());
                }
            }

            while (this.server.isRunning()) {
                tickSection = this.getServer().tick(tickSection);
            }
        } catch (Throwable throwable2) {
            //noinspection removal
            if (throwable2 instanceof ThreadDeath) {
                MinecraftServer.LOGGER.error("Main thread terminated by WatchDog due to hard crash", throwable2);
                return;
            }
            MinecraftServer.LOGGER.error("Encountered an unexpected exception", throwable2);
            CrashReport crashreport = MinecraftServer.constructOrExtractCrashReport(throwable2);

            this.server.fillSystemReport(crashreport.getSystemReport());
            Path path = this.server.getServerDirectory().resolve("crash-reports").resolve("crash-" + Util.getFilenameFormattedDateTime() + "-server.txt");

            if (crashreport.saveToFile(path, ReportType.CRASH)) {
                MinecraftServer.LOGGER.error("This crash report has been saved to: {}", path.toAbsolutePath());
            } else {
                MinecraftServer.LOGGER.error("We were unable to save this crash report to disk.");
            }

            this.server.onServerCrash(crashreport);
        } finally {
            try {
                this.server.stopped = true;
                this.server.stopServer();
            } catch (Throwable throwable3) {
                MinecraftServer.LOGGER.error("Exception stopping the server", throwable3);
            } finally {
                if (this.server.services.profileCache() != null) {
                    this.server.services.profileCache().clearExecutor();
                }
            }

        }
    }

    public void loadLevel(@NotNull ServerLevel level) {
        this.levels.add(level);
    }

    public String getName() {
        return Thread.currentThread().getName();
    }

    public void stopLevel(@NotNull ServerLevel level) {
        this.levels.remove(level);
        level.stopSpin();
    }

    public Collection<ServerLevel> getAllLevels() {
        return MinecraftServer.getServer().levels.values();
    }

    public Set<AbstractTickLoop> getTickLoops() {
        return this.loops;
    }

    public void markPrepareHalt() {
        // mark all threads to stop ticking.
        for (final AbstractTickLoop loop : this.loops) {
            loop.stopSpin();
        }
    }
}
