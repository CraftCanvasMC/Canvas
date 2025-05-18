package io.canvasmc.canvas;

import io.canvasmc.canvas.config.AnnotationBasedYamlSerializer;
import io.canvasmc.canvas.config.ConfigHandlers;
import io.canvasmc.canvas.config.ConfigSerializer;
import io.canvasmc.canvas.config.Configuration;
import io.canvasmc.canvas.config.ConfigurationUtils;
import io.canvasmc.canvas.config.RuntimeModifier;
import io.canvasmc.canvas.config.SerializationBuilder;
import io.canvasmc.canvas.config.annotation.AlwaysAtTop;
import io.canvasmc.canvas.config.annotation.Comment;
import io.canvasmc.canvas.config.annotation.Experimental;
import io.canvasmc.canvas.config.annotation.numeric.NonNegativeNumericValue;
import io.canvasmc.canvas.config.annotation.numeric.PositiveNumericValue;
import io.canvasmc.canvas.config.annotation.numeric.Range;
import io.canvasmc.canvas.config.impl.ConfigAccess;
import io.canvasmc.canvas.config.internal.ConfigurationManager;
import io.canvasmc.canvas.util.YamlTextFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.minecraft.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Configuration("canvas_server")
public class Config {
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
            "recommended to use folia-compatible plugins with this option enabled.",
            "",
            "This force-modifies the following options(for stability and performance purposes):",
            " - Enables threaded tracking"
        })
        public boolean enableThreadedRegionizing = false;

        @Comment("The region chunk shift. Only works with threaded regionizing enabled")
        public int regionGridExponent = 3;

        @Comment("The amount of time(in seconds) before watchdog starts printing error logs from slowdown")
        public long watchdogLoggingTime = 4L;
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
            "Modifies what algorithm the chunk system will use to define thread counts. Internally, the chunk system does not change.",
            "Valid options(lowercase or uppercase):",
            " - MOONRISE [Paper default thread count]",
            " - C2ME [Old algorithm from C2ME, less aggressive than the modern one]",
            " - C2ME_AGGRESSIVE [Modern algorithm from C2ME, more aggressive than the previous]"
        })
        public ChunkSystemAlgorithm defaultThreadAlgorithm = ChunkSystemAlgorithm.C2ME;

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

        public RateLimiters rateLimiters = new RateLimiters();
        public static class RateLimiters {
            @Comment("Disables the rate-limiter for the chunk sender. The limit only affects players")
            public boolean disableChunkSendRateLimiter = false;
            @Comment("Disables the rate-limiter for the chunk loader. The limit only affects players")
            public boolean disableChunkLoadRateLimiter = false;
            @Comment("Disables the rate-limiter for the chunk generator. The limit only affects players")
            public boolean disableChunkGenRateLimiter = false;
        }
    }

    public Debug debug = new Debug();
    public static class Debug {
        @Comment("Logs region teleports with entities")
        public boolean regionTeleports = false;
        @Comment("Logs task retiring with the tick scheduler")
        public boolean taskRetire = false;
        @Comment("Prints the configuration tree at startup. Not really recommended to disable, as this helps a ton with debugging issues")
        public boolean printConfigurationTree = true;
        @Comment("Logs teleport ticket debug when an entity is teleported")
        public boolean logTeleportTicketDebug = false;
    }

    public Fixes fixes = new Fixes();
    public static class Fixes {
        @Comment(value = {
            "Fixes MC-258859, fixing what Minecraft classifies as a 'slope', fixing",
            "some visuals with biomes like Snowy Slopes, Frozen Peaks, Jagged Peaks, Terralith & more"
        })
        public boolean mc258859 = false;
        @Comment(value = {
            "Fixes MC-136249(and 174584, along with its duplicates), fixing a bug where the",
            "Riptide enchantment does not function properly when combined with the Depth Strider enchantment"
        })
        public boolean mc136249 = false;
    }

    public Spawner spawner = new Spawner();
    public static class Spawner {
        public int minSpawnDelay = 200;
        public int maxSpawnDelay = 800;
        public int spawnCount = 4;
        public int maxNearbyEntities = 6;
        public int requiredPlayerRange = 16;
        public int spawnRange = 4;
    }

    public Blocks blocks = new Blocks();
    public static class Blocks {

        @Comment("Disables leaf block decay")
        public boolean disableLeafDecay = false;

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

        @Comment("Determines if end crystals should explode in a chain reaction, similar to how tnt works when exploded")
        public boolean chainEndCrystalExplosions = false;

        @Comment("Disables falling on farmland turning it back to dirt")
        public boolean disableFarmlandTrampling = false;

        @Comment("Makes farmland always moist, never drying out, even if it isn't near water")
        public boolean farmlandAlwaysMoist = false;

        @Comment(value = {
            "The amount of patterns that can be applied to a banner. Beyond 6, it will stop",
            " showing suggestions, as the client only goes to 6, but the result will still be",
            "displayed and usable. This allows for increasing or decreasing the amount of",
            "banner patterns maximum for a banner, or disabling the loom entirely"
        })
        public int loomMaxPatternCount = 6;

        @Comment("The rebound velocity of the living entity when bouncing on a slime block")
        public double livingEntityVelocityReboundFactor = 1.0D;

        @Comment("The rebound velocity of the non living entity when bouncing on a slime block")
        public double nonLivingEntityVelocityReboundFactor = 0.8D;
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

    public Commands commands = new Commands();
    public static class Commands {
        @Comment("Configurations for the 'tp' command")
        public Teleport teleport = new Teleport();
        public static class Teleport {
            @Comment("Teleports the entity or entities asynchronously")
            public boolean teleportAsync = false;
        }
    }

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

        @Comment("Allows configurability of the Skeleton aim accuracy. 0 is normal, higher the value, the less accurate it is.")
        public int skeletonAimAccuracy = 0;

        @Comment(value = {
            "Disables entity pushing, but the player can still push entities",
            "Immensely optimizes entity performance with lots of colliding entities"
        })
        public boolean onlyPlayersPushEntities = false;

        @Comment(value = {
            "Defines a percentage of which the server will apply to the velocity applied to",
            "item entities dropped on death. 0 means it has no velocity, 1 is default."
        })
        public double itemEntitySpreadFactor = 1.0D;

        @Comment("Disables saving snowball entities. This patches certain lag machines.")
        public boolean disableSnowballSaving = false;

        @Comment("Disables saving firework entities. This patches certain lag machines.")
        public boolean disableFireworkSaving = false;

        @Range(from = -1, to = 100, inclusive = true)
        @Comment("The chance a villager will light a firework in celebration of a raids completion. -1 to disable, must be a value between 0 and 100 inclusive.")
        public int villagerCelebrationFireworksChance = 5;

        public Cramming cramming = new Cramming();
        public static class Cramming {
            @Experimental // could have odd side effects
            @Comment("Reduces the amount of times an entity's movement goals(like random strolling) are ticked based on how crammed it is with other entities.")
            public boolean reduceEntityMoveWhenCrammed = false;

            @Comment("The threshold for an entity to be considered \"crammed\"")
            public int crammedThreshold = 2;
        }
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

    @Comment("Disables being disconnected from 'multiplayer.disconnect.invalid_player_movement', and just silently declines the packet handling.")
    public boolean gracefulTeleportHandling = true;

    public Networking networking = new Networking();
    public static class Networking {
        @Comment("Prevents players being disconnected by disconnect.spam")
        public boolean disableDisconnectSpam = false;

        @Comment("Prevents players being disconnected by connection throttling")
        public boolean disableConnectionThrottle = false;

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

    public VirtualThreads virtualThreads = new VirtualThreads();
    public static class VirtualThreads {
        @AlwaysAtTop
        @Comment("Enables use of Java 21+ virtual threads. Generally recommended to have these enabled")
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
                "join our discord at https://canvasmc.io/discord",
                "As a general rule of thumb, do NOT change a setting if",
                "you don't know what it does! If you don't know, ask!"
            })
            .handler(ConfigHandlers.ExperimentalProcessor::new)
            .handler(ConfigHandlers.CommentProcessor::new)
            .validator(ConfigHandlers.RangeProcessor::new)
            .validator(ConfigHandlers.NegativeProcessor::new)
            .validator(ConfigHandlers.PositiveProcessor::new)
            .validator(ConfigHandlers.NonNegativeProcessor::new)
            .validator(ConfigHandlers.NonPositiveProcessor::new)
            .validator(ConfigHandlers.PatternProcessor::new)
            .runtimeModifier("debug.*", new RuntimeModifier<>(boolean.class, (original) -> CanvasBootstrap.RUNNING_IN_IDE || original))
            .post(context -> {
                INSTANCE = context.configuration();
                if (INSTANCE.debug.printConfigurationTree) {
                    // build and print config tree.
                    YamlTextFormatter formatter = new YamlTextFormatter(4);
                    CanvasBootstrap.LOGGER.info(Component.text("Printing configuration tree:").appendNewline().append(formatter.apply(context.contents())));
                }
            })
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
        CanvasBootstrap.LOGGER.info("Finished Canvas config init in {}ms", TimeUnit.MILLISECONDS.convert(Util.getNanos() - startNanos, TimeUnit.NANOSECONDS));
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
