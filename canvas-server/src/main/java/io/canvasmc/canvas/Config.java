package io.canvasmc.canvas;

import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import ca.spottedleaf.moonrise.patches.chunk_system.util.ParallelSearchRadiusIteration;
import io.canvasmc.canvas.configuration.ConfigSerializer;
import io.canvasmc.canvas.configuration.Configuration;
import io.canvasmc.canvas.configuration.internal.ConfigurationManager;
import io.canvasmc.canvas.configuration.validator.NamespacedKeyValidator;
import io.canvasmc.canvas.configuration.validator.numeric.NonNegativeNumericValueValidator;
import io.canvasmc.canvas.configuration.validator.numeric.PositiveNumericValueValidator;
import io.canvasmc.canvas.configuration.validator.numeric.RangeValidator;
import io.canvasmc.canvas.configuration.writer.Comment;
import io.canvasmc.canvas.simd.SIMDDetection;
import io.canvasmc.canvas.tick.AffinitySchedulerThreadPool;
import io.canvasmc.canvas.util.Json5SerializerImpl;
import io.canvasmc.canvas.util.version.ApiClient;
import io.canvasmc.canvas.util.version.CanvasVersionFetcher;
import io.canvasmc.canvas.world.RegionizedTpsBar;
import io.canvasmc.canvas.world.entity.EntityCollisionMode;
import io.canvasmc.canvas.world.levelgen.SecureSeed;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.threadedregions.RegionizedServer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.random.RandomGeneratorFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

@Configuration("canvas-server")
public class Config {
    public static boolean ENABLE_FASTER_RANDOM = true;
    public static final ComponentLogger LOGGER = ComponentLogger.logger("Canvas");
    // Note: this field should never be used during POST, use 'context.configuration()' instead
    public static Config INSTANCE;
    public static ApiClient.BuildStatus ACTIVE_BUILD_CHANNEL = ApiClient.BuildStatus.UNKNOWN;
    public static final Consumer<String> GLOBAL_BROADCAST = (msg) -> {
        Component component = RegionizedTpsBar.gradient("[CanvasMC] ",
            s -> s.decorate(TextDecoration.BOLD),
            TextColor.color(0x357CEF), TextColor.color(0xF21AF4));

        Component text = Component.text(msg)
            .decoration(TextDecoration.BOLD, false);

        Component merged = component.append(text);
        LOGGER.info(text);
        if (isServerAccessible()) {
            for (final ServerPlayer player : MinecraftServer.getServer().getPlayerList().players) {
                if (player.getBukkitEntity().isOp()) {
                    player.sendSystemMessage(PaperAdventure.asVanilla(merged));
                }
            }
        }
    };

    private static boolean isServerAccessible() {
        return Bukkit.getServer() != null;
    }

    private static void installSecureSeed(Config config) {
        SecureSeedProtection settings = config.secureSeedProtection;
        if (settings.mode == SecureSeedProtection.Mode.V1) {
            // Reload from V2 -> V1 is allowed; downstream warns on the swap.
            SecureSeed.install(null);
            return;
        }

        // Salt persistence: never regenerate after first write. If the user
        // clears the field by mistake we re-seed once and log loudly so the
        // operator can detect world drift.
        if (SecureSeed.saltFromHex(settings.salt) == null) {
            if (SecureSeed.active() != null && SecureSeed.active().mode() == SecureSeed.Mode.V2) {
                LOGGER.warn("Secure-seed salt is missing from config but a V2 seed is already active; "
                    + "keeping the active seed and rewriting the salt to disk to avoid world drift.");
                settings.salt = SecureSeed.saltToHex(SecureSeed.active().saltCopy());
            }
            else {
                settings.salt = SecureSeed.saltToHex(SecureSeed.generateSalt());
                LOGGER.info("Generated secure-seed salt for V2 mode, persisting to config "
                    + "(this happens once; do not edit the salt afterwards)");
            }
        }

        // Activate immediately if the server is far enough along to expose its
        // seed; otherwise the world bootstrap will call activateSecureSeed once
        // the level data is loaded.
        if (MinecraftServer.getServer() != null) {
            try {
                long worldSeed = MinecraftServer.getServer().getWorldData().worldGenOptions().seed();
                activateSecureSeed(worldSeed);
            } catch (Throwable ignored) {
                // server not yet at a state where world data is available;
                // activateSecureSeed will be invoked later from the world bootstrap.
            }
        }
    }

    /**
     * Activates V2 secure seed protection using the supplied world seed and
     * the salt currently persisted in the config. Idempotent: identical
     * inputs replace nothing, mismatched inputs swap the active seed and
     * log a warning.
     */
    public static void activateSecureSeed(long worldSeed) {
        if (INSTANCE == null) {
            return;
        }
        SecureSeedProtection settings = INSTANCE.secureSeedProtection;
        if (settings.mode == SecureSeedProtection.Mode.V1) {
            SecureSeed.install(null);
            return;
        }
        byte[] salt = SecureSeed.saltFromHex(settings.salt);
        if (salt == null) {
            LOGGER.warn("Secure seed protection is in V2 mode but no salt is configured; falling back to V1");
            SecureSeed.install(null);
            return;
        }
        SecureSeed existing = SecureSeed.active();
        if (existing != null
            && existing.mode() == SecureSeed.Mode.V2
            && existing.worldSeed() == worldSeed
            && java.util.Arrays.equals(existing.saltCopy(), salt)) {
            return; // already installed with these exact inputs
        }
        SecureSeed.install(SecureSeed.of(SecureSeed.Mode.V2, worldSeed, salt));
        LOGGER.info("Secure seed protection (V2) is active for world seed {}", worldSeed);
    }

    static {
        reload();
        // preload parallel search radius iteration early
        //noinspection ResultOfMethodCallIgnored
        ParallelSearchRadiusIteration.getSearchIteration(MoonriseConstants.MAX_VIEW_DISTANCE);
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
            ACTIVE_BUILD_CHANNEL = buildStatus;
            switch (buildStatus) {
                case UNKNOWN -> GLOBAL_BROADCAST.accept("Running unknown build channel, proceed with caution");
                case EXPERIMENTAL -> GLOBAL_BROADCAST.accept("Running a beta build, there may be bugs, proceed with caution!");
                case LOCAL ->
                    GLOBAL_BROADCAST.accept("You are running a development version of Canvas, which may not be production-ready, be very careful!");
            }
        }));
    }

    public static void reload() {
        GLOBAL_BROADCAST.accept("Instantiating Canvas configuration");
        long startNanos = System.nanoTime();
        INSTANCE = ConfigurationManager.register(Config.class, Config::buildGlobal).getConfig();
        GLOBAL_BROADCAST.accept("Finished Canvas config init in " + TimeUnit.MILLISECONDS.convert(Util.getNanos() - startNanos, TimeUnit.NANOSECONDS) + "ms");
    }

    private static @NonNull @Unmodifiable ConfigSerializer<Config> buildGlobal(Configuration config, Class<Config> configClass) {
        return new Json5SerializerImpl.Json5Builder<Config>()
            .header("""
                This is the global Canvas configuration file.
                All configuration options here are made for vanilla-compatibility by default
                If you have questions join our discord at https://canvasmc.io/discord
                As a general rule of thumb, do NOT change a setting if
                you don't know what it does! If you don't know, ask!
                """)
            .classOf(configClass)
            .constructor(Config::new)
            .post(context -> {
                GLOBAL_BROADCAST.accept("Running post validation consumer");

                installSecureSeed(context.configuration());

                if (isServerAccessible()) {
                    for (final ServerPlayer player : MinecraftServer.getServer().getPlayerList().players) {
                        // update all info with player, covers 1.8 combat config
                        MinecraftServer.getServer().getPlayerList().sendAllPlayerInfo(player);
                    }
                }
                else {
                    // SIMD
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
                        LOGGER.warn("Debug: Java: " + System.getProperty("java.version") + ", test run: " + SIMDDetection.testRun);
                    }

                    try {
                        RandomGeneratorFactory.of("Xoroshiro128PlusPlus");
                    } catch (Throwable throwable) {
                        LOGGER.error("Canvas' faster random impl is not supported by your VM, falling back to legacy random");
                        Config.ENABLE_FASTER_RANDOM = false;
                    }
                }
            }).build();
    }

    /* START CONFIGURATION */

    public Scheduler scheduler = new Scheduler();

    public static class Scheduler {
        @Comment({
            "The maximum amount of time, in milliseconds, a thread will delay the execution of a scheduled task",
            "before allowing other threads to steal it for execution.",
            "Note: A smaller value reduces task deadline delays but increases potential task stealing between threads"
        })
        public long stealThresholdMillis = AffinitySchedulerThreadPool.DEFAULT_STEAL_THRESH_MILLIS;

        @Comment({
            "Buffer time (in milliseconds) before tick deadline to stop executing intermediate tasks.",
            "Ensures runTick() can start on time, at the deadline. Higher = safer, lower = more work done.",
            "Default: 0.1ms"
        })
        public double runTasksBufferMillis = AffinitySchedulerThreadPool.DEFAULT_RUN_TASKS_BUFFER_MILLIS;

        @Comment({
            "Amount of time between the end and next start of a region tick where the server will log a",
            "warning that the scheduler is overloaded. Can help catch if you need to allocate more threads",
            "or help identify deadline missing issues"
        })
        public long overloadedLogMillis = 5_000L;

        @Comment("Thread affinity for the AFFINITY scheduler provided by Canvas. By using this, you could pin the threads of region scheduler to cpu cores")
        public List<String> tickRegionAffinity = new ArrayList<>();

        @Comment("Enables pinning threads of the AFFINITY region scheduler to cpu cores")
        public boolean enableAffinitySchedulerCpuAffinity = false;

        @Comment({
            "Enables work stealing/task-thread affinity. This will try and attempt to keep tasks on the same tick thread",
            "to improve performance. If this is enabled, and the task misses its deadline by 'stealThresholdMillis', it can",
            "be taken by another tick thread to be run."
        })
        public boolean enableWorkStealing = false;

        @Comment({
            "Enables the affinity scheduler to run intermediate tasks while waiting for the deadline of the currently owned tick"
        })
        public boolean enableMidTickTasks = false;

        @Comment({
            "The default tick rate for the scheduler. Vanilla is 20, the game will run faster or slower depending on how you adjust this value",
            "Note this should really only be used for debugging purposes and for custom environments that require this change"
        })
        public float defaultTickRate = 20.0F;
    }

    public Chunks chunks = new Chunks();

    public static class Chunks {
        @Comment("Use euclidean distance squared for chunk task ordering. Makes the world load in what appears a circle rather than a diamond")
        public boolean useEuclideanDistanceSquared = true;

        @RangeValidator.Range(from = 1, to = 10, inclusive = true)
        @Comment("The thread priority for Canvas' rewritten chunk system executor")
        public int threadPoolPriority = Thread.NORM_PRIORITY;

        @Comment({
            "Determines the fluid post processing mode.",
            "The worldgen processes creates a lot of unnecessary fluid post-processing tasks,",
            "which can overload the server thread and cause stutters.",
            "There are 3 accepted values",
            " - VANILLA - just normal vanilla, no changes",
            " - DISABLED - disables fluid post processing completely",
            " - FILTERED - applies a rough filter to filter out fluids that are definitely not going to flow"
        })
        public FluidPostProcessingMode fluidPostProcessingMode = FluidPostProcessingMode.VANILLA;

        public enum FluidPostProcessingMode {
            VANILLA, DISABLED, FILTERED
        }

        @Comment({
            "Whether to turn fluid postprocessing into scheduled tick",
            "Fluid post-processing is very expensive when loading in new chunks, and this can affect",
            "MSPT significantly. This option delays fluid post-processing to scheduled tick to hopefully mitigate this issue."
        })
        public boolean fluidPostProcessingToScheduledTick = false;

        @Comment("Whether to enable aquifer optimizations to accelerate overworld worldgen")
        public boolean optimizeAquifer = false;

        @Comment("Whether to enable End Biome Cache to accelerate The End worldgen")
        public boolean useEndBiomeCache = false;

        @PositiveNumericValueValidator.PositiveNumericValue
        @Comment("The cache capacity for the end biome cache. Only works with 'useEndBiomeCache' enabled")
        public int endBiomeCacheCapacity = 1024;

        @Comment("Whether to enable Beardifier optimizations to accelerate world generation")
        public boolean optimizeBeardifier = false;

        @Comment("Whether to enable optimizations to the noise based chunk generator")
        public boolean optimizeNoiseGeneration = false;

        public Structures structures = new Structures();

        public static class Structures {
            @Comment({
                "Whether to use an alternative strategy to make structure layouts generate slightly faster than",
                "the default optimization the 'optimizeStructureGen' option has for template pool weights. This alternative strategy works by",
                "changing the list of pieces that structures collect from the template pool to not have duplicate entries.",
                "",
                "This will not break the structure generation, but it will make the structure layout different than",
                "if this config was off (breaking vanilla seed parity). The cost of speed may be worth it in large",
                "servers where many structure or custom gen plugins are using very high weight values in their template pools.",
                "",
                "Pros: Get a bit more performance from high weight Template Pool Structures.",
                "Cons: Loses parity with vanilla seeds on the layout of the structure. (Structure layout is not broken, just different)"
            })
            public boolean deduplicateShuffledTemplatePoolElementList = false;

            @Comment("Enables a port of the mod StructureLayoutOptimizer, which optimizes generation of Jigsaw Structures and NBT pieces")
            public boolean optimizeStructureGen = false;
        }
    }

    public Networking networking = new Networking();

    public static class Networking {
        @Comment({
            "The ClientboundSetEntityMotionPacket can often cause high network (Netty) usage and consumes (on larger production servers)",
            "up to 60% of your network usage. Filtering should have no side effects visually on the client. If you find any, report to Canvas"
        })
        public boolean filterClientboundSetEntityMotionPacket = false;

        @Comment("Filters entity movement packets to reduce the amount of useless move packets sent")
        public boolean reduceUselessMovePackets = false;

        @Comment("When enabled, hides flames on entities with fire resistance")
        public boolean hideFlamesOnEntitiesWithFireResistance = false;

        @Comment("When enabled, hides flames on entities with invisibility")
        public boolean hideFlamesOnEntitiesWithInvisibility = false;

        @Comment({
            "Optimizes player information packet sending by splitting players",
            "into buckets to be sent to spread out the list tick"
        })
        public boolean optimizePlayerListTicking = false;

        @PositiveNumericValueValidator.PositiveNumericValue
        @Comment("The interval in ticks for how often the server will tick the playerlist buckets")
        public int playerInfoSendInterval = 600;

        @Comment("This option makes protocol switching asynchronous, reducing global region blocking and improving login and configuration performance.")
        public boolean asyncProtocolSwitch = false;

        @Comment("The maximum bytes that can be sent by the server in a single packet to a player before kicking them")
        public int maximumPacketBytes = 8388608;

        @Comment({
        	"Paper implements an overflow fallback for container contents packets to the client, splitting the load into individual packets",
        	"for each individual slot to prevent kicking the player for large containers. By enabling this, the server wont split the packet, and will",
        	"kick the player if they attempt to open a container with the set contents packet larger than the max packet byte size"
        })
        public boolean disablePaperPacketOverflowContainerFix = false;

        @Comment("The disconnet reason sent to the client when the server attempted to send a packet that was too large")
        public String packetTooLargeDisconnectReason = "Clientbound packet exceeded max packet bytes";
    }

    @Comment("Check if a cactus can survive before growing. Heavily optimizes cacti farms")
    public boolean cactusCheckSurvivalBeforeGrowth = false;

    @Comment("Whether to cache expensive CraftEntityType#minecraftToBukkit call")
    public boolean enableCachedMTBEntityTypeConvert = false;

    @Comment("Enables creation of tile entity snapshots on retrieving blockstates")
    public boolean tileEntitySnapshotCreation = false;

    @Comment("Determines if end crystals should explode in a chain reaction, similar to how tnt works when exploded")
    public boolean chainEndCrystalExplosions = false;

    @Comment("Disables falling on farmland turning it back to dirt")
    public boolean disableFarmlandTrampling = false;

    @Comment("Makes farmland always moist, never drying out, even if it isn't near water")
    public boolean farmlandAlwaysMoist = false;

    @Comment("Disables Minecraft Chat Signing to prevent player reporting")
    public boolean enableNoChatReports = false;

    @Comment("Disables Minecraft chat verification ordering")
    public boolean disableChatVerificationOrder = false;

    @Comment("Enables snowballs being able to knockback players")
    public boolean snowballCanKnockback = false;

    @Comment("Enables eggs being able to knockback players")
    public boolean eggCanKnockback = false;

    @Comment({
        "The entity collision mode for the server",
        "Acceptable values:",
        " - VANILLA - default, all entities have collisions",
        " - ONLY_PUSHABLE_PLAYERS_LARGE - only players are pushable by entities, we search in a large radius(8 chunks)",
        "        for colliding players. This is primarily used for if servers have very large entities via the scale attribute",
        "        or custom entities plugin",
        " - ONLY_PUSHABLE_PLAYERS_SMALL - only players are pushable by entities, we search in a small radius(2 chunks)",
        "        for colliding players. This is used for if the server will have no large entities exceeding 2 chunks of width",
        " - NO_COLLISIONS - all entities have no collisions"
    })
    public EntityCollisionMode entityCollisionMode = EntityCollisionMode.VANILLA;

    // TODO - check these on minecraft updates
    public Fixes fixes = new Fixes();

    public static class Fixes {
        @Comment({
            "Fixes MC-298464 - https://bugs.mojang.com/browse/MC/issues/MC-298464",
            "Memory leak in hoglin farm due to CHANGED_DIMENSION entity removal"
        })
        public boolean mc298464 = false; // TODO - fixed by Mojang, remove.

        @Comment({
            "Fixes MC-223153 - https://bugs.mojang.com/browse/MC/issues/MC-223153",
            "Block of Raw Copper uses stone sounds instead of copper sounds"
        })
        public boolean mc223153 = false;

        @Comment({
            "Fixes MC-200418 - https://bugs.mojang.com/browse/MC/issues/MC-200418",
            "Cured baby zombie villagers stay as jockey variant"
        })
        // track this, was reopened in 1.21.11-pre2
        public boolean mc200418 = false;

        @Comment({
            "Fixes MC-200418 - https://bugs.mojang.com/browse/MC/issues/MC-94054",
            "Cave spiders spin around when walking"
        })
        public boolean mc94054 = false;

        @Comment({
            "Fixes MC-245394 - https://bugs.mojang.com/browse/MC/issues/MC-245394",
            "The sounds of raid horns blaring aren't controlled by the correct sound slider"
        })
        public boolean mc245394 = false;

        @Comment({
            "Fixes MC-231743 - https://bugs.mojang.com/browse/MC/issues/MC-231743",
            "minecraft.used:minecraft.POTTABLE_PLANT doesn't increase when placing plants into flower pots"
        })
        public boolean mc231743 = false;

        @Comment({
            "Fixes MC-227337 - https://bugs.mojang.com/browse/MC/issues/MC-227337",
            "When a shulker bullet hits an entity, the explodes sound is not played and particles are not produced"
        })
        public boolean mc227337 = false;

        @Comment({
            "Fixes MC-221257 - https://bugs.mojang.com/browse/MC/issues/MC-221257",
            "Shulker bullets don't produce bubble particles when moving through water"
        })
        public boolean mc221257 = false;

        @Comment({
            "Fixes MC-206922 - https://bugs.mojang.com/browse/MC/issues/MC-206922",
            "Items dropped by entities that are killed by lightning instantly disappear"
        })
        public boolean mc206922 = false;

        @Comment({
            "Fixes MC-155509 - https://bugs.mojang.com/browse/MC/issues/MC-155509",
            "Puffed pufferfish can hurt the player while dying"
        })
        public boolean mc155509 = false;

        @Comment({
            "Fixes MC-132878 - https://bugs.mojang.com/browse/MC/issues/MC-132878",
            "Armor stands destroyed by explosions/lava/fire don't produce particles"
        })
        public boolean mc132878 = false;

        @Comment({
            "Fixes MC-121706 - https://bugs.mojang.com/browse/MC/issues/MC-121706",
            "Skeletons and illusioners aren't looking up / down at their target while strafing"
        })
        public boolean mc121706 = false;

        @Comment({
            "Fixes MC-119754 - https://bugs.mojang.com/browse/MC/issues/MC-119754",
            "Firework boosting on elytra continues in spectator mode"
        })
        public boolean mc119754 = false;

        @Comment({
            "Fixes MC-100991 - https://bugs.mojang.com/browse/MC/issues/MC-100991",
            "Killing entities with a fishing rod doesn't count as a kill"
        })
        public boolean mc100991 = false;

        @Comment({
            "Fixes MC-30391 - https://bugs.mojang.com/browse/MC/issues/MC-30391",
            "Chickens, blazes and the wither emit particles when landing from a height, despite falling slowly"
        })
        public boolean mc30391 = false;

        @Comment({
            "Fixes MC-183990 - https://bugs.mojang.com/browse/MC/issues/MC-183990",
            "Group AI of some mobs breaks when their target dies"
        })
        public boolean mc183990 = false;

        @Comment({
            "Fixes MC-136249 - https://bugs.mojang.com/browse/MC/issues/MC-136249",
            "Wearing boots enchanted with depth strider decreases the strength of the riptide enchantment"
        })
        public boolean mc136249 = false;

        @Comment({
            "Fixes MC-258859 - https://bugs.mojang.com/browse/MC/issues/MC-258859",
            "Steep surface rule condition only works on the north and east faces of slopes"
        })
        public boolean mc258859 = false;

        @Comment({
            "In Vanilla, pearls can be duplicated during shutdown because pearls are saved to its owning player data",
            "and in the chunk data. Meaning, when the chunk is loaded, it loads that pearl, and when the player is loaded",
            "it loads the pearl in the player data.",
            "",
            "This fixes that, so that when 'restoreVanillaEnderPearlBehavior' is enabled, it unloads the pearl during shutdown",
            "so that the duplication doesn't occur. With that configuration disabled, it just loads the pearl from the",
            "chunk like normal."
        })
        public boolean pearlDuplication = false;
    }

    @Comment({
        "Enables better XP orb merging and removes the XP pickup delay",
        "Can be very useful for heavy XP farms",
        "This completely changes how orbs are merged, allowing for 1 single orb",
        "to contain an infinite amount of experience and is fully collected instantly",
        "rather than 1 xp per tick like with Vanilla. This is because we change the",
        "criteria for orbs to be merged, and instead of increasing the count, we",
        "increase the value of the orb. This way orbs are collected instantly, there",
        "will be no \"ghost orbs\", and all xp merging is as efficient as possible"
    })
    public boolean fastOrbs = false;

    @Comment({
        "Enables a regionized TPS-Bar implementation for Canvas",
        "This function is per-player, with this as a global setting to disable it",
        "To enable the tps-bar per-player, use the '/tpsbar' command"
    })
    public boolean enableTpsBar = true;

    @Comment({
        "MiniMessage-formatted line for the TPS bar. Placeholders: <tps>, <mspt>, <util>, <players>.",
        "Legacy tokens %tps%, %mspt%, %util%, %players% are also accepted and auto-converted."
    })
    public String tpsBarFormat = RegionizedTpsBar.DEFAULT_FORMAT;

    @Comment(value = {
        "The default respawn dimension for the server.",
        "This can assist for servers that need this changed to a different world",
        "due to setup reasoning, like needing to send the players to the spawn world",
        "or the wilderness world, etc.",
        "This needs a NamespacedKey string pattern, like 'namespace:key' that points",
        "to the dimension you want to use. The default is 'minecraft:overworld'",
        "",
        "This also applies to the end portal and nether portal, in replacement of the overworld",
        "For example, if you set this to 'minecraft:the_nether', all entities entering the",
        "end portal from the end will respawn in the nether rather than the overworld"
    })
    @NamespacedKeyValidator.NamespacedKey
    public String defaultRespawnDimensionKey = "minecraft:overworld";

    public ResourceKey<@NonNull Level> fetchRespawnDimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, Identifier.parse(this.defaultRespawnDimensionKey));
    }

    public Containers containers = new Containers();

    public static class Containers {
        @Comment("The amount of rows for the barrel block")
        @RangeValidator.Range(from = 1, to = 6, inclusive = true)
        public int barrelRows = 3;
        @Comment("Whether to use 6 rows for the player ender chest, rather than the normal 3")
        public boolean enderChestSixRows = false;
        @Comment({
            "Whether to use a permission based system for defining the size of ender chests per player",
            "Valid permissions:",
            " - purpur.enderchest.rows.six",
            " - purpur.enderchest.rows.five",
            " - purpur.enderchest.rows.four",
            " - purpur.enderchest.rows.three",
            " - purpur.enderchest.rows.two",
            " - purpur.enderchest.rows.one"
        })
        public boolean enderChestPermissionRows = false;
    }

    @Comment("Disables leaf decaying")
    public boolean disableLeafDecay = false;

    @Comment("Makes item entities immune to explosion damage sources")
    public boolean itemEntitiesImmuneToExplosions = false;

    @Comment("Makes item entities immune to lightning damage sources")
    public boolean itemEntitiesImmuneToLightning = false;

    @NonNegativeNumericValueValidator.NonNegativeNumericValue
    @Comment({
        "Defines a percentage of which the server will apply to the velocity applied to",
        "item entities dropped on death. 0 means it has no velocity, 1 is default."
    })
    public double itemEntitySpreadFactor = 1.0D;

    public Projectiles projectiles = new Projectiles();

    public static class Projectiles {
        @Comment("Controls how many chunks are allowed to be sync loaded by projectiles in a tick.")
        public int maxProjectileLoadsPerTick = 10;

        @Comment("Controls how many chunks a projectile can load in its lifetime before it gets automatically removed.")
        public int maxProjectileLoadsPerProjectile = 10;

        @Comment({
            "Specify which projectiles should load chunks when moving.",
            "Only works with projectiles thrown by players."
        })
        public List<String> loadChunks = new ArrayList<>();

        @Comment({
            "Restores vanilla redirect behavior for arrow hits on redirectable projectiles",
            "(like wind charges and fireballs) across region threads.",
            "For machines using this mechanic, it is recommended to set",
            "\"max-arrow-despawn-invulnerability: disabled\" in paper-world-defaults.yml",
            "To prevent the arrows from despawning."
        })
        public boolean crossRegionRedirectableProjectileDeflection = false;
    }

    @Comment({
        "Optimizes the suffocation check by selectively skipping the check in a way",
        "that still appears vanilla. This should be left enabled on most servers, but",
        "is provided as a configuration option if the vanilla deviation is undesirable"
    })
    public boolean enableSuffocationOptimization = false;

    @NonNegativeNumericValueValidator.NonNegativeNumericValue
    @Comment({
        "Defines the inaccuracy of skeleton bow shots. 14 being vanilla,",
        "100+ being absurdly stupidly and somewhat hilariously inaccurate",
        "",
        "The server difficulty is already taken into account upon calculation at runtime"
    })
    public double skeletonAimInaccuracy = 14.0D;

    public Combat combat = new Combat();

    public static class Combat {
        @Comment("Restores 1.8 pvp mechanics for attack delays")
        public boolean disableAttackHitDelay = false;

        @Comment({
            "Restores 1.8 pvp mechanics for sword blocking",
            "WARNING: may not work for clients older than 1.21.4"
        })
        public boolean imitateSwordBlocking = false;

        public Mace mace = new Mace();

        public static class Mace {
            @Comment("Removes the fall distance amplifier with maces")
            public boolean ignoreFallDistance = false;
            @Comment("The limit before fall distance scaling stops working for mace damage bonuses")
            public double fallDistanceLimit = -1.0D;
        }

        @Comment("Enables the old crafting recipe for god apples, using 8 gold blocks and an apple")
        public boolean enableOldEnchantedGoldenAppleCrafting = false;

        @Comment("Disables sweeping effects with swords")
        public boolean disableSweepingEdge = false;

        @Comment("Disables netherite armor knockback resistance")
        public boolean ignoreNetheriteKnockbackResistance = false;

        @Comment("Disables critical hits while sprinting")
        public boolean disableCritsWhileSprinting = false;

        @NonNegativeNumericValueValidator.NonNegativeNumericValue
        @Comment({
            "When an entity is damaged, it has a certain amount of invulnerability",
            "ticks applied to the entity until it can be damaged next. In Vanilla, this",
            "is 10. This configuration allows you to change the amount of ticks of",
            "invulnerability that is applied to the entity. 0 means invulnerability is not applied"
        })
        public int invulnerabilityTicks = 10;

        @Comment("Allows toggling if a fishing rod can pull entities")
        public boolean fishingRodPulls = true;

        @Comment("Configures the damage modifier per critical hit")
        public float criticalHitMultiplier = 1.5F;

        @Comment("Removes the red death animation seen on entities when killed.")
        public boolean removeRedDeathAnimation = false;

        @Comment("Restores the blast protection logic from before 1.21")
        public boolean useOldBlastProtection = false;
    }

    public Spawner spawner = new Spawner();

    public static class Spawner {
        @Comment("The spawner minimum spawn delay")
        public int minSpawnDelay = 200;

        @Comment("The spawner maximum spawn delay")
        public int maxSpawnDelay = 800;

        @Comment("The amount of spawner spawn count")
        public int spawnCount = 4;

        @Comment("The maximum amount of nearby entities before cancelling spawners ticking")
        public int maxNearbyEntities = 6;

        @Comment("The required player range for spawners to work")
        public int requiredPlayerRange = 16;

        @Comment("The maximum position range for spawned entities")
        public int spawnRange = 4;

        @Comment("Disables the spawner max nearby entities check")
        public boolean disableMaxNearbyEntitiesCheck = false;

        @Comment("Disables collisions for entities spawned by spawners")
        public boolean noCollisions = false;
    }

    // Implementation done in 0005-Disable-Criterion-Trigger-Config base patch
    @Comment("Disables all criterion triggers. Advancements will not work!")
    public boolean disableCriterionTrigger = false;

    @Comment("Makes crops ignore sunlight requirements when planting")
    public boolean cropsIgnoreLightCheck = false;

    @Comment("Disables snow light checks, so snow layers never melt")
    public boolean disableSnowLightChecks = false;

    @Comment("Disables grass light checks, so grass always spreads despite being in darkness")
    public boolean disableGrassLightChecks = false;

    @Comment("It is recommended to enable these options, as the client displays most of these particles already, so the server-side particle logic is not needed")
    public Particles particles = new Particles();

    public static class Particles {
        @Comment("Disables entity sprinting particles")
        public boolean disableSprintParticles = false;

        @Comment({
            "Disables entity fall particles",
            "This is handled on the server-side, not the client, so this will cause visual deviations from Vanilla"
        })
        public boolean disableFallParticles = false;

        @Comment("Disables entity death particles")
        public boolean disableDeathParticles = false;

        @Comment("Disables effect particles")
        public boolean disableEffectParticles = false;

        @Comment("Disables entity water splash particles")
        public boolean disableWaterSplashParticles = false;

        @Comment("Disables bubble columns particles")
        public boolean disableBubbleColumnParticles = false;

        @Comment({
            "Disables new combat particles",
            "This is handled on the server-side, not the client, so this will cause visual deviations from Vanilla"
        })
        public boolean disableNewCombatParticles = false;
    }

    @Comment("Disables non-player-entities from entering nether portals")
    public boolean blacklistNonPlayerEntitiesFromEnteringNetherPortals = false;

    @Comment("Disables non-player-entities from entering end portals")
    public boolean blacklistNonPlayerEntitiesFromEnteringEndPortals = false;

    @Comment("Disables non-player-entities from entering gateway portals")
    public boolean blacklistNonPlayerEntitiesFromEnteringGatewayPortals = false;

    @Comment({
        "Controls how quickly waypoint updates fall off with distance between players.",
        "Higher values mean updates stay frequent even across large distances, while lower values make them rarer.",
        "4000.0 is a nice balance, far enough to avoid spamming updates, but not so far that things feel desynced.",
        "",
        "You can test this via https://www.desmos.com/calculator/k83i3ensfm where Y is the probability, S is the scale,",
        "and D is the distance.",
        "Note: teleportation forces waypoint updates, and ignores this formula."
    })
    public double waypointUpdateScale = 4000.0D;

    @Comment("The server mod name displayed in server listings and client info")
    public String serverModName = io.papermc.paper.ServerBuildInfo.buildInfo().brandName();

    @Comment("Restores Vanilla ender pearl behavior to match Paper, which is disabled in Folia.")
    public boolean restoreVanillaEnderPearlBehavior = false;

    @Comment({
        "Folia's portaling rewrite makes the world loading screen not display on the client, and shows more of an",
        "empty void. With this option enabled, Canvas will make the client display this screen which can be more visually appealing"
    })
    public boolean displayWorldLoadScreenForPortaling = true;

    public SecureSeedProtection secureSeedProtection = new SecureSeedProtection();

    public static class SecureSeedProtection {
        @Comment({
            "Secure world generation (Foldenor patch port).",
            "",
            "Replaces the predictable noise-based seed pipeline with a cryptographic",
            "PRF/KDF (BLAKE3). When enabled, the original 64-bit world seed is mixed",
            "with a per-server salt and expanded into a 1024-bit master key, making",
            "the seed unrecoverable from observed terrain.",
            "",
            "Modes:",
            " - V1 - vanilla / legacy behavior. Reversible. Default for parity.",
            " - V2 - secure mode. Recommended for servers that rely on hidden seeds.",
            "",
            "Switching modes after a world has generated will produce a different",
            "world for newly generated chunks. Existing chunks are unaffected."
        })
        public Mode mode = Mode.V1;

        @Comment({
            "32-byte salt encoded as hex. Mixed into the master key under V2 so",
            "the same world seed produces a different master across servers.",
            "",
            "Leave blank to auto-generate on first launch. Once written, do not",
            "change this value or your world will regenerate inconsistently."
        })
        public String salt = "";

        @Comment({
            "When enabled, V2 dimensions derive independent subkeys, so observing",
            "the overworld leaks nothing about randomness used in the nether or",
            "the end. Disable only if you need cross-dimension seed parity for",
            "tooling reasons."
        })
        public boolean perDimensionSubkeys = true;

        public enum Mode {
            V1, V2
        }
    }
}
