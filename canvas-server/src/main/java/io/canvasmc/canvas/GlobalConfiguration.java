package io.canvasmc.canvas;

import io.canvasmc.canvas.configuration.ConfigurationProvider;
import io.canvasmc.canvas.configuration.Part;
import io.canvasmc.canvas.configuration.Resolver;
import io.canvasmc.canvas.simd.SIMDDetection;
import io.canvasmc.canvas.tick.AffinitySchedulerThreadPool;
import io.canvasmc.canvas.util.version.ApiClient;
import io.canvasmc.canvas.util.version.CanvasVersionFetcher;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.TickRegions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.random.RandomGeneratorFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GlobalConfiguration extends Part {

    private static final Path CONFIG_PATH = Path.of("config/canvas-server.yml").toAbsolutePath().normalize();
    private static final String BROADCAST_PERMISSION = "canvas.broadcasting.reciever";

    protected static final int CHAR_LIM = 90;

    public static final Logger LOGGER = LoggerFactory.getLogger("CanvasMC");

    public static final int INFO = 0;
    public static final int WARN = 1;
    public static final int ERROR = 2;

    private static GlobalConfiguration INSTANCE;
    private static ApiClient.BuildStatus BUILD_STATUS;

    static {
        ConfigurationProvider.buildSolidConfiguration(
            CONFIG_PATH,
            GlobalConfiguration::new,
            CHAR_LIM,
            new Resolver<>() {
                @Override
                public void onDiffAdd(final String fullyQualifiedName) {
                    LOGGER.info("Added new server-wide configuration option, '{}'", fullyQualifiedName);
                }

                @Override
                public void onDiffRemove(final String fullyQualifiedName) {
                    LOGGER.warn("Server-wide configuration option '{}' no longer exists and is now removed.", fullyQualifiedName);
                }

                @Override
                public void onFinishLoad(final GlobalConfiguration instance) {
                    postLoad(instance);

                    CompletableFuture.supplyAsync(() -> {
                        ApiClient.BuildStatus buildStatus = ApiClient.BuildStatus.UNKNOWN;
                        ServerBuildInfo buildInfo = ServerBuildInfo.buildInfo();
                        int buildNum = buildInfo.buildNumber().orElse(-1);
                        if (buildNum == -1) {
                            buildStatus = ApiClient.BuildStatus.LOCAL;
                        }
                        else {
                            try {
                                buildStatus = CanvasVersionFetcher.CLIENT.getBuild(buildNum).buildStatus();
                            } catch (Throwable ignored) {
                            }
                        }
                        return buildStatus;
                    }).thenAccept(buildStatus -> RegionizedServer.getInstance().addTask(() -> {
                        BUILD_STATUS = buildStatus;
                        switch (buildStatus) {
                            case UNKNOWN -> broadcast("Running unknown build channel, proceed with caution", WARN);
                            case EXPERIMENTAL ->
                                broadcast("Running a beta build, there may be bugs, proceed with caution!", WARN);
                            case LOCAL ->
                                broadcast("You are running a development version of Canvas, which may not be production-ready, be very careful!", WARN);
                        }
                    }));
                }
            },
            String.join(" ",
                "This is the server-wide configuration file provided by CanvasMC. This config holds options that",
                "are set across the entire server, and cannot be overridden per-world. You are free to modify, add, or",
                "remove comments as you please. You may refresh this configuration using the '/canvas reload` command at runtime.",
                "All option defaults are for Vanilla compatibility, not performance. If you have questions about certain options",
                "please reach out in our discord, https://canvasmc.io/discord"
            )
        );
    }

    private static void postLoad(final GlobalConfiguration configuration) {
        INSTANCE = configuration;

        if (TickRegions.started) {

            // if this is a reload, we may have things that need to be taken into effect now
            // for example, 1.8 combat delay configs may be updated, so we conduct updates

            final PlayerList playerList = MinecraftServer.getServer().getPlayerList();

            for (final ServerPlayer player : playerList.players) {
                // update all info with player, covers 1.8 combat config
                playerList.sendAllPlayerInfo(player);
            }
        }
        else {

            // this is only for startup-specific things, and should not contain post actions
            // that should be run on reload too. anything for reload and startup should be below

            try {
                RandomGeneratorFactory.of("Xoroshiro128PlusPlus");
            } catch (Throwable throwable) {
                broadcast("Canvas' faster random impl is not supported by your VM, falling back to legacy random", WARN);
                Config.ENABLE_FASTER_RANDOM = false;
            }

            // SIMD actions
            try {
                SIMDDetection.isEnabled = SIMDDetection.canEnable(LOGGER);
            } catch (NoClassDefFoundError | Exception ignored) {
                ignored.printStackTrace();
            }

            if (SIMDDetection.isEnabled) {
                LOGGER.info("SIMD operations detected as functional. Will replace some operations with faster versions.");
            }
            else {
                LOGGER.warn("SIMD operations are available for your server, but are not configured!");
                LOGGER.warn("To enable additional optimizations, add \"--add-modules=jdk.incubator.vector\" to your startup flags, BEFORE the \"-jar\".");
                LOGGER.warn("If you have already added this flag, then SIMD operations are not supported on your JVM or CPU.");
                LOGGER.warn("Debug: Java: {}, test run: {}", System.getProperty("java.version"), SIMDDetection.testRun);
            }
        }

        broadcast("Using " + configuration.regionScheduler.defaultTickRate + " as default tick rate", INFO);
    }

    public static GlobalConfiguration getInstance() {
        return INSTANCE;
    }

    public static ApiClient.BuildStatus getBuildStatus() {
        return BUILD_STATUS;
    }

    public static void broadcast(String msg, int severity) {
        if (TickRegions.started) {
            final MutableComponent literal = Component.literal(msg);

            switch (severity) {
                case WARN -> literal.withStyle(ChatFormatting.YELLOW);
                case ERROR -> literal.withStyle(ChatFormatting.RED);
            }

            // players might be in the server, try and send msg to people with perms

            for (final ServerPlayer entityPlayer : MinecraftServer.getServer().getPlayerList().players) {
                if (entityPlayer.getBukkitEntity().hasPermission(BROADCAST_PERMISSION)) {
                    entityPlayer.sendSystemMessage(literal);
                }
            }
        }

        // send to console
        switch (severity) {
            case INFO -> LOGGER.info(msg);
            case WARN -> LOGGER.warn(msg);
            case ERROR -> LOGGER.error(msg);
        }
    }

    public Scheduler regionScheduler = new Scheduler();
    public static class Scheduler extends Part {

        public AffinityScheduler affinityScheduler = new AffinityScheduler();
        public static class AffinityScheduler extends Part {
            public long stealThresholdMillis = AffinitySchedulerThreadPool.DEFAULT_STEAL_THRESH_MILLIS;
            public double runTasksBufferMillis = AffinitySchedulerThreadPool.DEFAULT_RUN_TASKS_BUFFER_MILLIS;
            public boolean enableWorkStealing = true;
            public boolean enableMidTickTasks = true;
            public List<String> tickRegionAffinity = new ArrayList<>();
            public boolean enableAffinitySchedulerCpuAffinity = false;
            {
                defineStyle("stealThresholdMillis", "The maximum amount of time, in milliseconds, a thread will delay the execution of a scheduled task before allowing other threads to steal it for execution. Note: A smaller value reduces task deadline delays but increases potential task stealing between threads");
                defineStyle("runTasksBufferMillis", "Buffer time (in milliseconds) before tick deadline to stop executing intermediate tasks. Ensures runTick() can start on time, at the deadline. Higher = safer, lower = more work done. Default: 0.1ms");
                defineStyle("enableWorkStealing", "Enables work stealing/task-thread affinity. This will try and attempt to keep tasks on the same tick thread to improve performance. If this is enabled, and the task misses its deadline by 'stealThresholdMillis', it can be taken by another tick thread to be run.");
                defineStyle("enableMidTickTasks", "Enables the affinity scheduler to run intermediate tasks while waiting for the deadline of the currently owned tick");
                defineStyle("tickRegionAffinity", "Thread affinity for the AFFINITY scheduler provided by Canvas. By using this, you could pin the threads of region scheduler to cpu cores");
                defineStyle("enableAffinitySchedulerCpuAffinity", "Enables pinning threads of the AFFINITY region scheduler to cpu cores");
            }
        }

        public long overloadedLogMillis = 5_000L;
        public float defaultTickRate = 20.0F;

        {
            defineStyle("affinityScheduler", "Configurations for the AFFINITY scheduler provided by Canvas. For these options to take effect, change the 'threaded-regions.scheduler' option in 'paper-global.yml' to 'AFFINITY'");
            defineStyle("overloadedLogMillis", "Amount of time between the end and next start of a region tick where the server will log a warning that the scheduler is overloaded. Can help catch if you need to allocate more threads or help identify deadline missing issues");
            defineStyle("defaultTickRate", "The default tick rate for the scheduler. Vanilla is 20, the game will run faster or slower depending on how you adjust this value. Note this should really only be used for debugging purposes and for custom environments that require this change");
        }
    }
}
