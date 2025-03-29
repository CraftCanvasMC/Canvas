package io.canvasmc.canvas;

import io.canvasmc.canvas.config.AnnotationBasedYamlSerializer;
import io.canvasmc.canvas.config.ConfigHandlers;
import io.canvasmc.canvas.config.ConfigSerializer;
import io.canvasmc.canvas.config.Configuration;
import io.canvasmc.canvas.config.ConfigurationUtils;
import io.canvasmc.canvas.config.SerializationBuilder;
import io.canvasmc.canvas.config.annotation.AlwaysAtTop;
import io.canvasmc.canvas.config.annotation.Comment;
import io.canvasmc.canvas.config.annotation.numeric.NonNegativeNumericValue;
import io.canvasmc.canvas.config.annotation.numeric.PositiveNumericValue;
import io.canvasmc.canvas.config.annotation.numeric.Range;
import io.canvasmc.canvas.config.impl.ConfigAccess;
import io.canvasmc.canvas.config.internal.ConfigurationManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.Goal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Configuration("canvas_server")
public class Config {
    public static final Logger LOGGER = LogManager.getLogger("CanvasConfig");
    public static final Map<Class<? extends Goal>, Entities.GoalMask> COMPILED_GOAL_MASKS = new ConcurrentHashMap<>();
    public static final List<ResourceLocation> COMPILED_ENTITY_MASK_LOCATIONS = Collections.synchronizedList(new ArrayList<>());
    public static boolean CHECK_ENTITY_MASKS = false;
    public static Config INSTANCE = new Config();

    public Ticking ticking = new Ticking();
    public static class Ticking {

        @Range(from = 1, to = 10, inclusive = true)
        @Comment(value = {
            "Sets the thread priority for tick loop threads",
            "",
            "The default uses the algorithm bellow to match the main thread calculations for thread priority:",
            "- priority = availableProcessors > 4 ? 10 : NORM_PRIORITY + 2",
            "",
            "References:",
            "- https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#setPriority(int)"
        })
        public int tickLoopThreadPriority = Runtime.getRuntime().availableProcessors() > 4 ? 10 : Thread.NORM_PRIORITY + 2;

        @Comment("Defines the amount of threads allocated to the tick scheduler")
        public int allocatedSchedulerThreadCount;

        {
            int tickThreads = Runtime.getRuntime().availableProcessors() / 2;
            if (tickThreads <= 4) {
                tickThreads = 1;
            } else {
                tickThreads = tickThreads / 4;
            }
            allocatedSchedulerThreadCount = tickThreads;
        }

        @Comment("Enables each world to have the \"empty server\" logic per world introduced in Minecraft 1.21.4")
        public boolean emptySleepPerWorlds = true;

        @Comment(value = {
            "Enables threaded regions. Works exactly like Folia in region grouping, but",
            "works slightly differently in behavior. To prevent issues with plugins, it's",
            "recommended to use folia-compatible plugins with this option enabled."
        })
        public boolean enableThreadedRegionizing = false;

        @Comment("The region chunk shift. Only works with threaded regionizing enabled")
        public int gridExponent = 4;
    }

    public Chunks chunks = new Chunks();
    public static class Chunks {

        @Comment("Soft limit for io worker nbt cache")
        public long chunkDataCacheSoftLimit = 8192L;

        @Comment("Hard limit for io worker nbt cache")
        public long chunkDataCacheLimit = 32678L;

        public NativeAcceleration nativeAcceleration = new NativeAcceleration();
        public static class NativeAcceleration {
            @Comment(value = {
                "Enables AVX512 support for natives-math optimization",
                "",
                "References:",
                "- https://en.wikipedia.org/wiki/AVX-512",
                "- https://www.intel.com/content/www/us/en/products/docs/accelerator-engines/what-is-intel-avx-512.html"
            })
            public boolean allowAVX512 = false;

            @Range(from = -1, to = 9, inclusive = true)
            @Comment(value = {
                "Overrides the ISA target located by the native loader, which allows forcing AVX512.",
                "Value must be between 1-9(inclusive), -1 to disable override",
                "",
                "Must be a value between 6-9 for AVX512 support"
            })
            public int isaTargetLevelOverride = -1;

            @Comment("Enable the use of bundled native libraries to accelerate world generation")
            public boolean nativeAccelerationEnabled = true;
        }

        @Comment(value = {
            "Modifies what algorithm the chunk system will use to define thread counts.",
            "Valid options(lowercase or uppercase):",
            " - MOONRISE [Paper default thread count]",
            " - C2ME [Old algorithm from C2ME, less aggressive than the modern one]",
            " - C2ME_AGGRESSIVE [Modern algorithm from C2ME, more aggressive than the previous]"
        })
        public ChunkSystemAlgorithm chunkWorkerAlgorithm = ChunkSystemAlgorithm.C2ME;

        @Comment(value = {
            "Sets the thread priority for worker threads. Default is NORMAL+1",
            "",
            "References:",
            "- https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#setPriority(int)"
        })
        public int threadPoolPriority = Thread.NORM_PRIORITY + 1;

        public ChunkSending chunkSending = new ChunkSending();
        public static class ChunkSending {
            @AlwaysAtTop
            @Comment("Runs chunk sending off-level/main in virtual threads")
            public boolean asyncChunkSending = true;

            @Comment(value = {
                "Changes the maximum view distance for the server, allowing clients to have",
                "render distances higher than 32"
            })
            public int maxViewDistance = 32;
        }

        @Comment("Smoothens the bedrock layer at the bottom(and top if in the nether) of the world during world generation.")
        public boolean smoothBedrockLayer = false;

        public Generation generation = new Generation();
        public static class Generation {
            @Comment(value = {
                "Fixes MC-258859, fixing what Minecraft classifies as a 'slope', fixing",
                "some visuals with biomes like Snowy Slopes, Frozen Peaks, Jagged Peaks, Terralith & more"
            })
            public boolean mc258859 = false;

            @Comment(value = {
                "Whether to use an alternative strategy to make structure layouts generate slightly even faster than",
                "the default optimization this mod has for template pool weights. This alternative strategy works by",
                "changing the list of pieces that structures collect from the template pool to not have duplicate entries.",
                "",
                "This will not break the structure generation, but it will make the structure layout different than",
                "if this config was off (breaking vanilla seed parity). The cost of speed may be worth it in large",
                "modpacks where many structure mods are using very high weight values in their template pools.",
                "",
                "Pros: Get a bit more performance from high weight Template Pool Structures.",
                "Cons: Loses parity with vanilla seeds on the layout of the structure. (Structure layout is not broken, just different)"
            })
            public boolean deduplicateShuffledTemplatePoolElementList = false;

            @Comment("Enables a port of the mod StructureLayoutOptimizer, which optimizes general Jigsaw structure generation")
            public boolean enableStructureLayoutOptimizer = true;

            @Comment(value = {
                "Uses euclidean distance squared algorithm for determining chunk task priorities(like generation, loading, etc).",
                "If true: chunk priorities will be ordered in a circle pattern",
                "If false: chunk priorities will be ordered in a diamond pattern"
            })
            public boolean useEuclideanDistanceSquaredChunkPriorities = true;

            @Comment("Disables fluid ticking on chunk generation")
            public boolean disableFluidTickingInPostProcessGenerationStep = false;
        }
    }

    public Blocks blocks = new Blocks();
    public static class Blocks {

        @Comment("Optimizes piston block entities")
        public boolean optimizePistonMovingBlockEntity = true;

        @Comment("Allows opening any type of door with your hand, including iron doors")
        public boolean canOpenAnyDoorWithHand = false;

        public TNT tnt = new TNT();
        public static class TNT {
            public boolean explosionNoBlockDamage = false;

            @Comment(value = {
                "Enables 'merge tnt logic', which makes it so that nearby tnt are merged together,",
                "increasing the power of 1 tnt explosion and reducing the amount of explosions.",
                "Helpful for anarchy servers"
            })
            public boolean mergeTntLogic = false;

            @Comment("Max TNT primed for merging logic to start. Requires 'mergeTntLogic' to be enabled")
            public int maxTntPrimedForMerge = 100;
        }

        @Comment(value = {
            "Caches the command block parse results, significantly reducing performance impact",
            "from command blocks(given parsing is often times half the command blocks tick time)"
        })
        public boolean cacheCommandBlockParseResults = true;
    }

    @Comment(value = {
        "Enables plugin compatibility mode.",
        "With Canvas' multi-threaded context, plugins most likely are not going to be as compatible with",
        "Canvas, given it can fire events basically anywhere at anytime. Because of this, some plugins break.",
        "This option sync-locks any 'single-threaded' events. What this means is \"single-threaded\" events",
        "are locked so only 1 thread can call a \"single-threaded event\" at a time. \"asynchronous-marked\"",
        "events remain unlocked, given plugins that use them should already be prepared for them to fire basically",
        "whenever and wherever. This may cause performance issues, so proceed with caution when using this, and",
        "only use this when absolutely necessary(its better to fix the problem in the plugin than have the entire",
        "server suffer performance loss)"
    })
    public boolean pluginCompatibilityMode = false;

    public Entities entities = new Entities();
    public static class Entities {

        public Pathfinding pathfinding = new Pathfinding();
        public static class Pathfinding {
            @AlwaysAtTop
            public boolean enableThreadedPathfinding = true;

            @PositiveNumericValue
            public int maxProcessors = 2;

            @PositiveNumericValue
            public int keepAlive = 60;
        }

        public EntityTracking entityTracking = new EntityTracking();
        public static class EntityTracking {
            @AlwaysAtTop
            public boolean enableThreadedTracking = true;

            @PositiveNumericValue
            public int maxProcessors = 1;

            @PositiveNumericValue
            public int keepAlive = 60;
        }

        @Comment("Enables a modified version of Pufferfish's async mob spawning patch")
        public boolean enableAsyncSpawning = true;

        @Comment("Disables the ticking of a useless secondary poi sensor")
        public boolean skipUselessSecondaryPoiSensor = true;

        @Comment("More efficiently clumps XP orbs")
        public boolean clumpOrbs = true;

        @Comment(value = {
            "Masks for goals. Allows disabling and adding delays to the tick rate of the goal. The 'goalClass'",
            "must be the class name of the goal. Like \"net.minecraft.entity.goal.ExampleGoal\", and if its a",
            "subclass, then \"net.minecraft.entity.goal.RootClass$ExampleGoalInSubClass\""
        })
        public List<GoalMask> entityGoalMasks = new ArrayList<>();
        public static class GoalMask {
            public String goalClass;
            public boolean disableGoal = false;
            public int goalTickDelay = 0;
        }

        @Comment("Allows configuration of how entities are ticked, and if they should be ticked based on the type")
        public List<EntityMask> entityMasks = new ArrayList<>();
        public static class EntityMask {
            public String type;
            public boolean shouldTick = true;
            public int tickRate = 1;
        }

        @Comment(value = {
            "Disables entity pushing, but the player can still be pushed.",
            "Immensely optimizes entity performance with lots of crammed entities"
        })
        public boolean disableEntityPushing = false;

        @Comment(value = {
            "Defines a percentage of which the server will apply to the velocity applied to",
            "item entities dropped on death. 0 means it has no velocity, 1 is default."
        })
        public double itemEntitySpreadFactor = 1;

        @Comment("Disables saving snowball entities. This patches certain lag machines.")
        public boolean disableSnowballSaving = false;

        @Comment("Disables saving firework entities. This patches certain lag machines.")
        public boolean disableFireworkSaving = false;
    }

    @Comment("Use faster sin/cos math operations")
    public boolean useCompactSineLUT = true;

    @Comment("Lag compensation related configurations. Improves the player experience when TPS is low")
    public LagCompensation lagCompensation = new LagCompensation();
    public static class LagCompensation {
        @AlwaysAtTop
        public boolean enabled = true;
        public boolean blockEntityAcceleration = false;
        public boolean blockBreakingAcceleration = true;
        public boolean eatingAcceleration = true;
        public boolean potionEffectAcceleration = true;
        public boolean fluidAcceleration = true;
        public boolean pickupAcceleration = true;
        public boolean portalAcceleration = true;
        public boolean sleepingAcceleration = true;
        public boolean timeAcceleration = true;
        public boolean randomTickSpeedAcceleration = true;
    }

    @Comment("Ignores messages like 'moved too quickly' and 'moved wrongly'")
    public boolean alwaysAllowWeirdMovement = true;

    @Comment("Disables being disconnected from 'multiplayer.disconnect.invalid_player_movement', and just silently declines the packet handling.")
    public boolean gracefulTeleportHandling = true;

    @Comment(value = {
        "Ignore \"<player> moved too quickly\" if the server is lagging. Improves general",
        "gameplay experience of the player when the server is lagging, as they wont get lagged back"
    })
    public boolean ignoreMovedTooQuicklyWhenLagging = true;

    public Networking networking = new Networking();
    public static class Networking {
        @Comment("Prevents players being disconnected by disconnect.spam")
        public boolean disableDisconnectSpam = false;

        public NoChatReports noChatReports = new NoChatReports();
        public static class NoChatReports {
            @AlwaysAtTop
            @Comment("Enables no chat reports, like the fabric mod.")
            public boolean enable = false;

            @Comment("True if server should include extra query data to help clients know that your server is secure.")
            public boolean addQueryData = true;

            @Comment("True if server should convert all player messages to system messages.")
            public boolean convertToGameMessage = true;

            @Comment("Enables debug logging for this feature.")
            public boolean debugLog = false;

            @Comment("Requires the No Chat Reports mod for the client to join")
            public boolean demandOnClient = false;

            @Comment("The message that will disconnect the client if they dont have the mod. 'demandOnClient' must be true to take effect")
            public String disconnectDemandOnClientMessage = "You do not have No Chat Reports, and this server is configured to require it on client!";
        }
    }

    @Comment("Determines if end crystals should explode in a chain reaction, similar to how tnt works when exploded")
    public boolean chainEndCrystalExplosions = false;

    @Comment("Disables leaf block decay")
    public boolean disableLeafDecay = false;

    @Comment(value = {
        "Allows configurability of the distance of which certain objects need to be from a player",
        "to tick, like chunks, block entities, etc. This can cause major behavior changes."
    })
    public TickDistanceMaps tickDistanceMaps = new TickDistanceMaps();
    public static class TickDistanceMaps {
        @NonNegativeNumericValue
        @Comment("Controls the distance defined in the nearby player updates for 'TICK_VIEW_DISTANCE', affects per-player mob spawning")
        public int nearbyPlayersTickDistance = 4;

        @Comment("Enables the override that applies the `nearbyPlayersTickDistance`")
        public boolean enableNearbyPlayersTickViewDistanceOverride = false;

        @NonNegativeNumericValue
        @Comment(value = {
            "In certain checks, like if a player is near a chunk(primarily used for spawning), it checks if the player is within a certain",
            "circular range of the chunk. This configuration allows configurability of the distance(in blocks) the player must be to pass the check.",
            "",
            "This value is used in the calculation 'range/16' to get the distance in chunks any player must be to allow the check to pass",
            "By default, this range is computed to 8, meaning a player must be within an 8 chunk radius of a chunk position to pass",
            "Keep in mind the result is rounded to the nearest whole number."
        })
        public int playerNearChunkDetectionRange = 128;
    }

    @Comment("Configure the amount of ticks between updating chunk precipitation")
    public int ticksBetweenPrecipitationUpdates = -1;
    @Comment("Configure the amount of ticks between ticking random tick updates")
    public int ticksBetweenRandomTickUpdates = -1;
    @Comment("Configure the amount of ticks between fluid ticks")
    public int ticksBetweenFluidTicking = -1;
    @Comment("Configure the amount of ticks between block ticks")
    public int ticksBetweenBlockTicking = -1;
    @Comment("Configure the amount of ticks between block events")
    public int ticksBetweenBlockEvents = -1;
    @Comment("Configure the amount of ticks between ticking raids")
    public int ticksBetweenRaidTicking = -1;
    @Comment("Configure the amount of ticks between purging stale tickets")
    public int ticksBetweenPurgeStaleTickets = -1;
    @Comment("Configure the amount of ticks between ticking custom spawners(like phantoms, cats, wandering traders, etc)")
    public int ticksBetweenCustomSpawnersTick = -1;

    @Comment("Configure the max amount of bonus damage the mace item can apply")
    public int maxMaceDamageBonus = -1;

    @Comment("Uses a 'dummy inventory' for passing the InventoryMoveEvent, which avoids unneeded resources spent on building a bukkit Inventory.")
    public boolean useDummyInventoryForHopperInventoryMoveEvent = true;

    @Comment(value = {
        "Blacklists criterion triggers based off its name in the key in the ResourceLocation associated with the trigger.",
        "Criterion Triggers are essentially the triggers that drive advancements. Some, can be really performance-heavy",
        "with high playercounts, like the 'inventory_changed' criterion. For each string placed in the blacklist, any key",
        "matching that during registration will be unregistered, disabling the criterion trigger.",
        "",
        "To disable all criterion triggers, input '*'"
    })
    public List<String> blacklistedCriterionTriggers = new ArrayList<>();

    @Comment(value = {
        "Broadcasts the \"server is lagging, running X ticks behind\" message from console",
        "to all operators on the server"
    })
    public boolean broadcastServerTicksBehindToOps = false;

    public VirtualThreads virtualThreads = new VirtualThreads();
    public static class VirtualThreads {
        @AlwaysAtTop
        @Comment("Enables use of Java 21+ virtual threads")
        public boolean enabled = false;

        @Comment("Uses virtual threads for the Bukkit scheduler.")
        public boolean bukkitScheduler = false;

        @Comment("Uses virtual threads for the Chat scheduler.")
        public boolean chatScheduler = false;

        @Comment("Uses virtual threads for the Authenticator scheduler.")
        public boolean authenticatorScheduler = false;

        @Comment("Uses virtual threads for the Tab Complete scheduler.")
        public boolean tabCompleteScheduler = false;

        @Comment("Uses virtual threads for the MCUtil async executor.")
        public boolean asyncExecutor = false;

        @Comment("Uses virtual threads for the Async Command Builder Thread Pool")
        public boolean commandBuilderScheduler = false;

        @Comment("Use virtual threads for the Profile Lookup pool, which fetches player profile info")
        public boolean profileLookupPool = false;

        @Comment("Use virtual threads for the server text filter pool")
        public boolean serverTextFilterPool = false;

        public boolean shouldReplaceAuthenticator() {
            return enabled && authenticatorScheduler;
        }

        public boolean shouldReplaceChatExecutor() {
            return enabled && chatScheduler;
        }

        public boolean shouldReplaceTabCompleteExecutor() {
            return enabled && tabCompleteScheduler;
        }

        public boolean shouldReplaceBukkitScheduler() {
            return enabled && bukkitScheduler;
        }

        public boolean shouldReplaceAsyncExecutor() {
            return enabled && asyncExecutor;
        }

        public boolean shouldReplaceCommandBuilderExecutor() {
            return enabled && commandBuilderScheduler;
        }

        public boolean shouldReplaceProfileLookupPool() {
            return enabled && profileLookupPool;
        }

        public boolean shouldReplaceTextFilterPool() {
            return enabled && serverTextFilterPool;
        }
    }

    private static <T extends Config> @NotNull ConfigSerializer<T> buildSerializer(Configuration config, Class<T> configClass) {
        ConfigurationUtils.extractKeys(configClass);
        return new AnnotationBasedYamlSerializer<>(SerializationBuilder.<T>newBuilder()
            .header(new String[]{
                "This is the main Canvas configuration file",
                "All configuration options here are made for vanilla-compatibility",
                "and not for performance. Settings must be configured specific",
                "to your hardware and server type. If you have questions",
                "join our discord at https://discord.gg/canvasmc/"
            })
            .handler(ConfigHandlers.CommentProcessor::new)
            .validator(ConfigHandlers.RangeProcessor::new)
            .validator(ConfigHandlers.NegativeProcessor::new)
            .validator(ConfigHandlers.PositiveProcessor::new)
            .validator(ConfigHandlers.NonNegativeProcessor::new)
            .validator(ConfigHandlers.NonPositiveProcessor::new)
            .validator(ConfigHandlers.PatternProcessor::new)
            .post(context -> INSTANCE = context.configuration())
            .build(config, configClass)
        );
    }

    public static Config init() {
        long startNanos = Util.getNanos();
        ConfigurationManager.register(Config.class, Config::buildSerializer);
        System.setProperty("com.ishland.c2me.opts.natives_math.duringGameInit", "true");
        if (INSTANCE.chunks.nativeAcceleration.nativeAccelerationEnabled) {
            try {
                Class.forName("io.canvasmc.canvas.util.NativeLoader").getField("lookup").get(null);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        for (final Entities.GoalMask goalMask : INSTANCE.entities.entityGoalMasks) {
            try {
                //noinspection unchecked
                Class<? extends Goal> clazz = (Class<? extends Goal>) Class.forName(goalMask.goalClass);
                COMPILED_GOAL_MASKS.put(clazz, goalMask);
                LOGGER.info("Enabling Goal Mask for \"{}\"...", clazz.getSimpleName());
            } catch (ClassNotFoundException e) {
                LOGGER.error("Unable to locate goal class \"{}\", skipping.", goalMask, e);
                continue;
            }
        }
        for (final Entities.EntityMask entityMask : INSTANCE.entities.entityMasks) {
            if (!CHECK_ENTITY_MASKS) {
                LOGGER.warn("An EntityMask was registered, please be very careful with this, as it can make the movement of entities a lot more choppy(if using delays)");
                CHECK_ENTITY_MASKS = true;
            }
            LOGGER.info("Registered EntityMask for '{}'", entityMask.type);
            COMPILED_ENTITY_MASK_LOCATIONS.add(ResourceLocation.parse(entityMask.type));
        }
        if (INSTANCE.ticking.enableThreadedRegionizing) {
            INSTANCE.entities.enableAsyncSpawning = false; // incompatible with threaded regions
            INSTANCE.entities.entityTracking.enableThreadedTracking = false; // incompatible with threaded regions
        }
        LOGGER.info("Finished Canvas config init in {}ms", TimeUnit.MILLISECONDS.convert(Util.getNanos() - startNanos, TimeUnit.NANOSECONDS));
        return INSTANCE;
    }

    public static class Access implements ConfigAccess {

        @Override
        public boolean containsField(final @NotNull String field) {
            return getPossibleObject(field) != null;
        }

        @Override
        public <T> T getField(final @NotNull String field) {
            //noinspection unchecked
            return (T) getPossibleObject(field);
        }

        private @Nullable Object getPossibleObject(@NotNull String field) {
            String[] sharded = field.split("\\.");
            Object r = null;
            for (String f : sharded) {
                try {
                    r = (r == null ? Config.class : r.getClass()).getField(f).get(r == null ? Config.INSTANCE : r);
                } catch (IllegalAccessException | NoSuchFieldException e) {
                    throw new RuntimeException(e);
                }
            }
            return r;
        }

    }
}
