package io.canvasmc.canvas;

import ca.spottedleaf.moonrise.common.util.SimpleThreadUnsafeRandom;
import io.canvasmc.canvas.configuration.ConfigurationProvider;
import io.canvasmc.canvas.configuration.Part;
import io.canvasmc.canvas.configuration.Resolver;
import io.canvasmc.canvas.configuration.Style;
import io.canvasmc.canvas.configuration.Validator;
import io.canvasmc.canvas.simd.SIMDDetection;
import io.canvasmc.canvas.tick.AffinitySchedulerThreadPool;
import io.canvasmc.canvas.util.FasterRandomSource;
import io.canvasmc.canvas.util.version.ApiClient;
import io.canvasmc.canvas.util.version.CanvasVersionFetcher;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.threadedregions.RegionizedServer;
import io.papermc.paper.threadedregions.TickRegions;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.random.RandomGeneratorFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.RandomSupport;
import org.jspecify.annotations.NonNull;
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
    private static boolean ENABLE_FASTER_RANDOM = true;

    static {
        reload();
    }

    public static void reload() {
        LOGGER.info("Loading Canvas server configuration");
        ConfigurationProvider.buildSolidConfiguration(
            CONFIG_PATH,
            GlobalConfiguration::new,
            CHAR_LIM,
            new Resolver<>() {
                @Override
                public void onDiffAdd(final String fullyQualifiedName) {
                    LOGGER.info("Added new server-wide configuration option: \"{}\"", fullyQualifiedName);
                }

                @Override
                public void onDiffRemove(final String fullyQualifiedName) {
                    LOGGER.warn("Server-wide configuration option \"{}\" no longer exists and is now removed.", fullyQualifiedName);
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
            Style.create()
                .literal("Global Configuration for CanvasMC").endLine()
                .blank()
                .wordWrap(
                    "This is the server-wide configuration file provided by CanvasMC. This config holds options",
                    "that are set across the entire server, and cannot be overridden per-world. You are free to modify,",
                    "add, or remove comments as you please."
                ).endLine()
                .blank()
                .wordWrap(
                    "You may refresh this configuration at runtime using the \"/canvas reload\" command, however",
                    "it is not recommended to do this during production, as this can cause issues like unexpected crashes",
                    "or unintended behavior."
                ).endLine()
                .blank()
                .wordWrap(
                    "All defaults for the options provided in this configuration are configured for upstream",
                    "compatibility over performance. You must do some manual configuration to get some of the performance",
                    "benefits Canvas provides."
                ).endLine()
                .blank()
                .wordWrap(
                    "If you have questions about certain configuration options please reach out in our discord"
                ).endLine()
                .literal("https://canvasmc.io/discord")
                .compile(60)
        );
    }

    private static void postLoad(final GlobalConfiguration configuration) {
        INSTANCE = configuration;

        // validate the configuration so users don't end up doing a stupid
        Validator.validateObject(configuration);

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
                ENABLE_FASTER_RANDOM = false;
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

    public static @NonNull RandomSource createFastRandom() {
        return ENABLE_FASTER_RANDOM ? new FasterRandomSource(RandomSupport.generateUniqueSeed()) : new SimpleThreadUnsafeRandom(RandomSupport.generateUniqueSeed());
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

    public RegionScheduler regionScheduler = new RegionScheduler();
    public static class RegionScheduler extends Part {

        {
            option("affinityScheduler")
                .docs(
                    "Configurations for the AFFINITY scheduler provided by Canvas. For these options to take effect,",
                    "change the \"threaded-regions.scheduler\" option in \"paper-global.yml\" to \"AFFINITY\""
                );
        }

        public AffinityScheduler affinityScheduler = new AffinityScheduler();
        public static class AffinityScheduler extends Part {

            {
                option("stealThresholdMillis")
                    .docs(
                        Style.wrap(
                            "The maximum amount of time, in milliseconds, a thread will delay the execution of a scheduled task",
                            "before allowing other threads to steal it for execution."
                        )
                        .blank()
                        .literal("Note: A smaller value reduces task deadline delays but increases potential task stealing between threads")
                    ).greaterThanOrEqualTo(0.0F);

                option("runTasksBufferMillis")
                    .docs(
                        Style.wrap(
                            "Buffer time (in milliseconds) before tick deadline to stop executing intermediate tasks.",
                            "Ensures runTick() can start on time, at the deadline."
                        )
                        .blank()
                        .literal("Default: 0.1ms, Higher is safer, lower means more work is done")
                    ).greaterThanOrEqualTo(0.0F);

                option("enableWorkStealing")
                    .docs(
                        "Enables work stealing/task-thread affinity. This will try and attempt to keep tasks on the same tick thread",
                        "to improve performance. If this is enabled, and the task misses its deadline by \"stealThresholdMillis\", it can",
                        "be taken by another tick thread to be run."
                    );

                option("enableMidTickTasks").docs("Enables the affinity scheduler to run intermediate tasks while waiting for the deadline of the currently owned tick");
                option("tickRegionAffinity")
                    .docs("Thread affinity for the AFFINITY scheduler provided by Canvas. By using this, you could pin the threads of region scheduler to cpu cores")
                    .greaterThanOrEqualTo(0.0F);

                option("enableAffinitySchedulerCpuAffinity").docs("Enables pinning threads of the AFFINITY region scheduler to cpu cores");
            }

            public long stealThresholdMillis = AffinitySchedulerThreadPool.DEFAULT_STEAL_THRESH_MILLIS;
            public double runTasksBufferMillis = AffinitySchedulerThreadPool.DEFAULT_RUN_TASKS_BUFFER_MILLIS;
            public boolean enableWorkStealing = true;
            public boolean enableMidTickTasks = true;
            public int[] tickRegionAffinity = new int[0];
            public boolean enableAffinitySchedulerCpuAffinity = false;
        }

        {
            option("overloadedLogMillis")
                .docs(
                    "Amount of time between the end and next start of a region tick where the server will log a",
                    "warning that the scheduler is overloaded. Can help catch if you need to allocate more threads",
                    "or help identify deadline missing issues"
                ).greaterThan(0.0F);

            option("defaultTickRate")
                .docs(
                    "The default tick rate for the scheduler. Vanilla is 20, the game will run faster or slower depending on how you adjust this value.",
                    "Note this should really only be used for debugging purposes and for custom environments that require this change"
                ).greaterThan(0.0F);

            option("guardSeverity")
                .docs(
                    Style.wrap(
                        "Canvas introduces extra tick thread checks to help catch plugin issues. This determines how aggressive the new guards are"
                    ).defineEnum(GuardSeverity.class, (severity) -> {
                        return switch (severity) {
                            case LOG -> "Just logs a warning in console, but continues the operation";
                            case THROW -> "Throws an exception, can crash the server. Good for ensuring correctness";
                            case SILENT -> "Doesn't say anything or do anything";
                        };
                    })
                );
        }

        public long overloadedLogMillis = 5_000L;
        public float defaultTickRate = 20.0F;
        public GuardSeverity guardSeverity = GuardSeverity.THROW;

        public enum GuardSeverity {
            SILENT,
            LOG,
            THROW
        }
    }

    public ChunkSystem chunkSystem = new ChunkSystem();
    public static class ChunkSystem extends Part {

        {
            option("threadPriority").between(Thread.MIN_PRIORITY, Thread.MAX_PRIORITY);
            option("fluidPostProcessingAlgorithm")
                .docs(
                    Style.wrap(
                        "The worldgen processes creates a lot of unnecessary fluid post-processing tasks,",
                        "which can overload the server and cause stuttering when generating new chunks.",
                        "Depending on the algorithm chosen, this can help reduce stutter and improve performance",
                        "when generating chunks"
                    ).defineEnum(FluidPostProcessingMode.class, (mode) -> {
                        return switch (mode) {
                            case VANILLA -> "Normal post processing algorithm, everything is processed";
                            case DISABLED -> "Disables fluid post processing entirely";
                            case FILTERED -> "C2MEs algorithm to filter unnecessary post processing tasks";
                        };
                    })
                );

            option("makeFluidPostProcessScheduledTick")
                .docs(
                    "Enabling this turns fluid post processing into a scheduled tick, which hopefully",
                    "helps to mitigate MSPT spiking issues during chunk generation"
                );
            option("endBiomeCacheSize").greaterThan(0.0F);
            option("structureOptimizations").docs(
                "These options are ported from the mod StructureLayoutOptimizer, https://modrinth.com/mod/structure-layout-optimizer",
                "which optimizes the generation of Jigsaw Structures and NBT pieces"
            );
        }

        public int threadPriority = Thread.NORM_PRIORITY;
        public FluidPostProcessingMode fluidPostProcessingAlgorithm = FluidPostProcessingMode.VANILLA;

        public enum FluidPostProcessingMode {
            VANILLA,
            DISABLED,
            FILTERED
        }

        public boolean makeFluidPostProcessScheduledTick = false;
        public boolean optimizeAquifer = false;
        public boolean useEndBiomeCache = false;
        public int endBiomeCacheSize = 1024;
        public boolean optimizeBeardifier = false;
        public boolean optimizeNoiseGeneration = false;

        public StructureGen structureOptimizations = new StructureGen();
        public static class StructureGen extends Part {

            {
                option("deduplicateShuffledTemplatePoolElementList").docs(
                    Style.wrap(
                        "Whether to use an alternative strategy to make structure layouts generate slightly faster than",
                        "the default optimization has for template pool weights. This alternative strategy works by",
                        "changing the list of pieces that structures collect from the template pool to not have duplicate entries."
                    )
                    .blank()
                    .wordWrap(
                        "By enabling this option you can get a bit more performance from high weight Template Pool Structures,",
                        "but you lose parity with Vanilla seeds on the layout of the structure"
                    )
                );
            }

            public boolean deduplicateShuffledTemplatePoolElementList = false;
            public boolean enable = false;
        }
    }

    // TODO - check these on minecraft updates
    public UpstreamFixes vanillaFixes = new UpstreamFixes();
    public static class UpstreamFixes extends Part {

        {
            // should we do these specific or do we try and do better with this?
            // stream((fieldName) -> {
            //     if (fieldName.startsWith("mc")) {
            //         // this is a specific minecraft fix
            //         return new OptionDefinition()
            //             .docs(
            //                 Style.create().literal("https://bugs.mojang.com/browse/MC/issues/MC-" + fieldName.substring(2))
            //             );
            //     }
            //     return null;
            // });
            option("pearlDuplication")
                .docs(
                    "There is a Vanilla bug where in-flight pearls are duplicated at shutdown. This fixes that when",
                    "the option \"restoreVanillaEnderPearlBehavior\" is enabled alongside this."
                );
        }

        public boolean mc298464 = false;
        public boolean mc223153 = false;
        public boolean mc200418 = false;
        public boolean mc94054 = false;
        public boolean mc245394 = false;
        public boolean mc227337 = false;
        public boolean mc221257 = false;
        public boolean mc206922 = false;
        public boolean mc155509 = false;
        public boolean mc132878 = false;
        public boolean mc121706 = false;
        public boolean mc119754 = false;
        public boolean mc100991 = false;
        public boolean mc30391 = false;
        public boolean mc183990 = false;
        public boolean mc136249 = false;
        public boolean pearlDuplication = false;
    }

    public Networking networking = new Networking();
    public static class Networking extends Part {

        {
            option("filterVelocityPacket")
                .docs(
                    "The ClientboundSetEntityMotionPacket, also known as the entity velocity packet, can often",
                    "consume major amounts of network usage, often being up to 60% on large production servers",
                    "This option filters the unnecessary packets sent, while still maintaining Vanilla visual effects"
                );
            option("filterMovePackets").docs("Filters useless move packets that dont need to be sent");

            option("alternativePlayerListTick").docs("Splits players into buckets to be spread evenly across the playerlist tick");
            option("playerInfoSendInterval")
                .docs(
                    "If alternative playerlist tick is enabled, this is the interval in ticks for how often",
                    "each bucket will be ticked"
                ).greaterThan(0.0F);
            option("asyncProtocolSwitch")
                .docs(
                    "This makes protocol switching asynchronous during login, which reduces global region blocking",
                    "and can improve login and configuration phase performance during player join"
                );

            option("maximumPacketBytes")
                .docs(
                    "The maximum bytes that can be sent by the server in a single packet to a player before kicking them"
                ).greaterThan(0.0F);
            option("disablePaperPacketOverflowContainerFix")
                .docs(
                    "This disables Papers overflow fallback for large container packets being sent to the client. This means",
                    "that if the container data is too large, it will kick the player if they attempt to open a container",
                    "with contents larger than the max packet byte size"
                );
            option("packetTooLargeDisconnectReason")
                .docs(
                    "The disconnect reason sent to the client when the server attempted to send a packet that",
                    "exceeded the max packet size"
                );
        }

        public boolean filterVelocityPacket = false;
        public boolean filterMovePackets = false;
        public boolean alternativePlayerListTick = false;
        public int playerInfoSendInterval = 600;
        public boolean asyncProtocolSwitch = false;
        public int maximumPacketBytes = 8388608;
        public boolean disablePaperPacketOverflowContainerFix = false;
        public String packetTooLargeDisconnectReason = "Clientbound packet exceeded max packet bytes";
        public boolean purpurAlternativeKeepalive = false;
    }

    {
        option("serverModName").docs("The server mod name displayed in server listings and client info").word();
        option("restoreVanillaEnderPearlBehavior").docs("Restores and fixes Vanilla Ender Pearl behavior, broken by Folia");

        option("displayWorldLoadScreenForPortaling")
            .docs(
                "Folia's portaling rewrite makes the world loading screen not display on the client properly, and",
                "instead shows an empty void. With this enabled, Canvas will display the proper world loading screen"
            );
        option("cacheMinecraft2BukkitEntityTypeConversion").docs("Whether to cache expensive CraftEntityType#minecraftToBukkit call");
        option("tileEntitySnapshotCreation").docs("Enables creation of tile entity snapshots on retrieving blockstates");

        option("defaultRespawnDimensionKey")
            .docs(
                "The default respawn dimension for the server. This can assist servers needing to change this to a",
                "different world due to setup reasoning, like needing to send players to the \"spawn\" world or something.",
                "This also applies to the end portal and nether portal, in replacement of the overworld, meaning the",
                "target dimension for entities going from the nether for example will be sent here"
            ).identifier(); // TODO - object mapping?
    }

    public String serverModName = ServerBuildInfo.buildInfo().brandName();
    public boolean restoreVanillaEnderPearlBehavior = false;
    public boolean displayWorldLoadScreenForPortaling = true;
    public boolean cacheMinecraft2BukkitEntityTypeConversion = false;
    public boolean tileEntitySnapshotCreation = false;
    public String defaultRespawnDimensionKey = Level.OVERWORLD.identifier().toString();

    public static @NonNull ResourceKey<@NonNull Level> fetchRespawnDimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(GlobalConfiguration.getInstance().defaultRespawnDimensionKey));
    }

    public PurpurContainers purpurContainers = new PurpurContainers();
    public static class PurpurContainers extends Part {

        {
            option("barrelRows").docs("The amount of rows for the barrel block").between(1, 6);
            option("enderChestSixRows").docs("Whether to use 6 rows for the player ender chest, rather than the normal 3");
            option("enderChestPermissionRows")
                .docs(
                    Style.wrap("Whether to use a permission based system for defining the size of ender chests per player")
                        .literal("Valid permissions").endLine()
                        .literal(" - purpur.enderchest.rows.six").endLine()
                        .literal(" - purpur.enderchest.rows.five").endLine()
                        .literal(" - purpur.enderchest.rows.four").endLine()
                        .literal(" - purpur.enderchest.rows.three").endLine()
                        .literal(" - purpur.enderchest.rows.two").endLine()
                        .literal(" - purpur.enderchest.rows.one").endLine()
                );
            option("enderChestPersistHiddenRows").docs("Whether items should remain stored in slots, even if those slots become inaccessible through permissions");
        }

        public int barrelRows = 3;
        public boolean enderChestSixRows = false;
        public boolean enderChestPermissionRows = false;
        public boolean enderChestPersistHiddenRows = true;
    }

    public boolean blacklistNonPlayerEntitiesFromEnteringNetherPortals = false;
    public boolean blacklistNonPlayerEntitiesFromEnteringEndPortals = false;
    public boolean blacklistNonPlayerEntitiesFromEnteringGatewayPortals = false;

    public Chat chat = new Chat();
    public static class Chat extends Part {

        {
            option("disableChatReporting").docs("Disables Minecraft chat signing to prevent player chat reporting");
            option("disableChatVerificationOrder").docs("Disables Minecraft chat verification ordering");
        }

        public boolean disableChatReporting = false;
        public boolean disableChatVerificationOrder = false;
    }

}
