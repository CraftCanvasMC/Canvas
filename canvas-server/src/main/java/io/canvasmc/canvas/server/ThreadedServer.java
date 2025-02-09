package io.canvasmc.canvas.server;

import ca.spottedleaf.moonrise.common.util.TickThread;
import io.canvasmc.canvas.Config;
import io.canvasmc.canvas.LevelAccess;
import io.canvasmc.canvas.ThreadedBukkitServer;
import io.canvasmc.canvas.entity.ThreadedEntityScheduler;
import io.canvasmc.canvas.server.level.LevelThread;
import io.canvasmc.canvas.server.network.PlayerJoinThread;
import io.netty.util.internal.ConcurrentSet;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
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
    public static final long MAX_NANOSECONDS_FOR_TICK_FRAME = 50_000_000;
    public static final ThreadGroup SERVER_THREAD_GROUP = new ThreadGroup("ServerThreadGroup");
    private static final Set<Long> LEVEL_THREAD_IDS = new ConcurrentSet<>();
    public static BooleanSupplier SHOULD_KEEP_TICKING;
    public static Function<ServerLevel, Thread> SPINNER = (level) -> {
        try {
            LevelThread dedicated = new LevelThread(SERVER_THREAD_GROUP, level::spin, "levelThread:" + level.getName(), level);
            LEVEL_THREAD_IDS.add(dedicated.threadId());
            dedicated.setPriority(Config.INSTANCE.levelThreadPriority);
            dedicated.setUncaughtExceptionHandler((thread, throwable) -> LOGGER.error("Uncaught exception in level thread, {}", ((LevelThread) thread).getLevel().getName(), throwable));
            dedicated.start();
            return dedicated;
        } catch (Throwable throwable) {
            throw new RuntimeException("Unable to spin world '" + level.getName() + "'!", throwable);
        }
    };
    private final List<ServerLevel> levels = new CopyOnWriteArrayList<>();
    private final MinecraftServer server;
    public ThreadedEntityScheduler entityScheduler;
    private long tickSection;
    private boolean started = false;

    public ThreadedServer(MinecraftServer server) {
        this.server = server;
    }

    public static Long @NotNull [] getLevelIds() {
        return LEVEL_THREAD_IDS.toArray(new Long[0]);
    }

    @Override
    public boolean isLevelThread(long id) {
        return LEVEL_THREAD_IDS.contains(id);
    }

    @Override
    public boolean isLevelThread(final Thread thread) {
        return thread instanceof LevelThread;
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
    public void scheduleOnMain(final Runnable runnable) {
        this.server.scheduleOnMain(runnable);
    }

    public List<ServerLevel> getThreadedWorlds() {
        return levels;
    }

    public boolean hasStarted() {
        return started;
    }

    public long getTickSection() {
        return tickSection;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public void spin() {
        try {
            ThreadedBukkitServer.setInstance(this);
            if (!server.initServer()) {
                throw new IllegalStateException("Failed to initialize server");
            }

            if (Config.INSTANCE.threadedEntityTicking) {
                //noinspection resource
                final ThreadedEntityScheduler entityScheduler = new ThreadedEntityScheduler("EntityScheduler");
                this.entityScheduler = entityScheduler;
                TickThread entitySchedulerThread = new TickThread(entityScheduler::spin, "EntityScheduler");
                LEVEL_THREAD_IDS.add(entitySchedulerThread.threadId());
                entitySchedulerThread.setPriority(Config.INSTANCE.levelThreadPriority); // Keep priority same as level threads to avoid inconsistency
                entitySchedulerThread.start();
            }
            if (Config.INSTANCE.asyncPlayerJoining) {
                PlayerJoinThread.getInstance().start();
            }
            this.started = true;
            this.server.nextTickTimeNanos = Util.getNanos();
            this.server.statusIcon = this.server.loadStatusIcon().orElse(null);
            this.server.status = this.server.buildServerStatus();

            LOGGER.info("Running delayed init tasks");
            this.server.server.getScheduler().mainThreadHeartbeat();

            final long actualDoneTimeMs = System.currentTimeMillis() - org.bukkit.craftbukkit.Main.BOOT_TIME.toEpochMilli();
            LOGGER.info("Done ({})! For help, type \"help\"", String.format(java.util.Locale.ROOT, "%.3fs", actualDoneTimeMs / 1000.00D));
            this.server.server.spark.enableBeforePlugins();
            org.spigotmc.WatchdogThread.tick();

            org.spigotmc.WatchdogThread.hasStarted = true;
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
                    ((DedicatedServer) this.server).handleConsoleInput(startupCommand, this.server.createCommandSourceStack());
                }
            }

            while (this.server.isRunning()) {
                tickSection = this.getServer().tick(tickSection);
            }
        } catch (Throwable throwable2) {
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
        LOGGER.info("Loaded level to threaded context: {}", level.dimension().location());
    }

    public String getName() {
        return Thread.currentThread().getName();
    }

    public void stopLevel(@NotNull ServerLevel level) {
        this.levels.remove(level);
        level.stopSpin();
        LOGGER.info("Removed level from threaded context: {}", level.dimension().location());
    }

    public Collection<ServerLevel> getAllLevels() {
        return MinecraftServer.getServer().levels.values();
    }
}
