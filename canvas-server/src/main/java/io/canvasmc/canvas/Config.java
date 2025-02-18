package io.canvasmc.canvas;

import ca.spottedleaf.moonrise.patches.chunk_tick_iteration.ChunkTickConstants;
import io.canvasmc.canvas.config.AnnotationBasedYamlSerializer;
import io.canvasmc.canvas.config.ConfigSerializer;
import io.canvasmc.canvas.config.Configuration;
import io.canvasmc.canvas.config.ConfigurationUtils;
import io.canvasmc.canvas.config.ValidationException;
import io.canvasmc.canvas.config.annotation.AlwaysAtTop;
import io.canvasmc.canvas.config.annotation.Comment;
import io.canvasmc.canvas.config.annotation.EnumValue;
import io.canvasmc.canvas.config.annotation.Experimental;
import io.canvasmc.canvas.config.annotation.numeric.NegativeNumericValue;
import io.canvasmc.canvas.config.annotation.numeric.NonNegativeNumericValue;
import io.canvasmc.canvas.config.annotation.numeric.NonPositiveNumericValue;
import io.canvasmc.canvas.config.annotation.numeric.PositiveNumericValue;
import io.canvasmc.canvas.config.annotation.numeric.Range;
import io.canvasmc.canvas.config.internal.ConfigurationManager;
import io.canvasmc.canvas.entity.tracking.ThreadedTracker;
import io.canvasmc.canvas.server.chunk.ChunkSendingExecutor;
import io.canvasmc.canvas.server.network.PlayerJoinThread;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.Goal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

@Configuration("canvas_server")
public class Config {
    public static final Logger LOGGER = LogManager.getLogger("CanvasConfig");
    public static final Map<Class<? extends Goal>, GoalMask> COMPILED_GOAL_MASKS = new ConcurrentHashMap<>();
    public static final List<ResourceLocation> COMPILED_ENTITY_MASK_LOCATIONS = Collections.synchronizedList(new ArrayList<>());
    public static boolean CHECK_ENTITY_MASKS = false;
    public static Config INSTANCE = new Config();

    @Comment("Determines if the server should tick the playerlist assigned to each world on their own level threads, or if it should tick on the main thread(globally)")
    public boolean runPlayerListTickOnIndependentLevel = true;

    @Comment("Amount of ticks until the level will resync time with the player")
    public int timeResyncInterval = 2400;

    @Range(from = 1, to = 10, inclusive = true)
    @Comment(value = {
        "Sets the thread priority for tick loop threads",
        "",
        "The default uses the algorithm bellow to match the main thread calculations for thread priority:",
        "- priority = availableProcessors > 4 ? 8 : NORM_PRIORITY + 2",
        "",
        "References:",
        "- https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#setPriority(int)"
    })
    public int tickLoopThreadPriority = calculateDefaultThreadPriority();

    private int calculateDefaultThreadPriority() {
        return Runtime.getRuntime().availableProcessors() > 4 ? 8 : Thread.NORM_PRIORITY + 2;
    }

    @Comment(value = {
        "Sets the thread daemon for tick loop threads",
        "",
        "References:",
        "- https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#setDaemon(boolean)"
    })
    public boolean setDaemonForTickLoops = false;

    @Comment("In the ServerChunkCache, it schedules tasks to the main thread. Enabling this changes it to schedule to the level thread")
    public boolean useLevelThreadsAsChunkSourceMain = true;

    @Comment("Enables each world to have the \"empty server\" logic per world introduced in Minecraft 1.21.4")
    public boolean emptySleepPerWorlds = true;

    @Comment("Enables the \"threadedtick\" command, which is an implementation of the vanilla \"tick\" command for the Canvas threaded context")
    public boolean enableCanvasTickCommand = true;

    @Comment("Allows opening any type of door with your hand, including iron doors")
    public boolean canOpenAnyDoorWithHand = false;

    @Comment("Ensure correct doors. Schedules an extra update on the next tick to ensure the door doesnt get glitched when a Villager and Player both interact with it at the same time")
    public boolean ensureCorrectDoors = false;

    @Comment("Chunk related config options")
    public Chunks chunks = new Chunks();
    public static class Chunks {
        public long chunkDataCacheSoftLimit = 8192L;
        public long chunkDataCacheLimit = 32678L;
        @Comment(value = {
            "Enables AVX512 support for natives-math optimization",
            "",
            "References:",
            "- https://en.wikipedia.org/wiki/AVX-512",
            "- https://www.intel.com/content/www/us/en/products/docs/accelerator-engines/what-is-intel-avx-512.html"
        })
        public boolean allowAVX512 = false;

        @Range(from = -1, to = 9, inclusive = true)
        @Comment("Overrides the ISA target located by the native loader, which allows forcing AVX512(must be a value between 6-9 for AVX512 support). Value must be between 1-9(inclusive), -1 to disable override")
        public int isaTargetLevelOverride = -1;
        public boolean nativeAccelerationEnabled = true;

        @EnumValue(enumValue = ChunkSystemAlgorithm.class)
        @Comment("Modifies what algorithm the chunk system will use to define thread counts. values: MOONRISE, C2ME, C2ME_AGGRESSIVE")
        public String chunkWorkerAlgorithm = "C2ME";

        @Comment(value = {
            "Whether to use density function compiler to accelerate world generation",
            "",
            "Density function: https://minecraft.wiki/w/Density_function",
            "",
            "This functionality compiles density functions from world generation",
            "datapacks (including vanilla generation) to JVM bytecode to increase",
            "performance by allowing JVM JIT to better optimize the code",
            "",
            "Currently, all functions provided by vanilla are implemented.",
            "Chunk upgrades from pre-1.18 versions are not implemented and will",
            "fall back to the unoptimized version of density functions.",
            "",
            "Please test if this optimization actually benefits your server, as",
            "it can sometimes slow down chunk performance than speed it up."
        })
        public boolean enableDensityFunctionCompiler = false;

        @Comment(value = {
            "Sets the thread priority for worker threads",
            "",
            "References:",
            "- https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#setPriority(int)"
        })
        public int threadPoolPriority = Thread.NORM_PRIORITY + 1;
        @Comment("Changes the maximum view distance for the server, allowing clients to have render distances higher than 32.")
        public int maxViewDistance = 32;
        @Comment(value = {
            "Allows disabling distance manager updates for when the level thread",
            "tries to poll a task, which can cause some post process generation",
            "work to be run on the level thread instead of on the async chunk loader"
        })
        public boolean runDistanceManagerUpdatesOnTaskPoll = false;

        @Comment("Related configuration options to chunk sending optimizations")
        public ChunkSending chunkSending = new ChunkSending();
        public static class ChunkSending {
            @AlwaysAtTop
            @Comment("Runs chunk sending off-level/main")
            public boolean asyncChunkSending = true;

            @PositiveNumericValue
            @Comment("Amount of threads to use for async chunk sending. This does nothing when 'useVirtualThreadExecutorForChunkSenders' is enabled")
            public int asyncChunkSendingThreadCount = 1;

            @Comment("Similar to the 'virtual-thread' options, this makes it so that the executor for chunk senders uses a virtual thread pool")
            public boolean useVirtualThreadExecutorForChunkSenders = false;

            @Comment(value = {
                "With how chunks are loaded and generated with Canvas, chunk",
                "loading is much faster, causing the server to spam the",
                "client with a ton of packets when they first load in. This",
                "allows the server to rate-limit the amount of chunk sends per tick",
                "allowing a smoother ping when sending chunks on join",
                "",
                "Default is -1 to disable the rate limit",
                "Disabling this is highly not recommended, this spam-sent hundreds",
                "of chunks per tick to the client when developing chunk loading optimizations"
            })
            public int rateLimitChunkSends = 50;
        }

        @Comment(value = {
            "Similar to Folia, Canvas includes a few options for regionized chunk ticking",
            "Original work by @TheDeafCreeper"
        })
        public Regionized regionized = new Regionized();
        public static class Regionized {
            @Comment("Enables regionized chunk ticking logic")
            public boolean enableRegionizedChunkTicking = false;
            @Comment("The amount of threads to allocate to regionized chunk ticking")
            public int executorThreadCount = 4;
            @Range(from = 1, to = 10, inclusive = true)
            @Comment(value = {
                "Configures the thread priority of the executor. Default is 5(normal thread priority)",
                "",
                "References:",
                "- https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#setPriority(int)"
            })
            public int executorThreadPriority = Thread.NORM_PRIORITY;
        }

        @Comment("Smoothens the bedrock layer at the bottom(and top if in the nether) of the world during world generation.")
        public boolean smoothBedrockLayer = false;

        @Comment("Runs noise filling and biome populating in a virtual thread executor. If disabled, it will run sync")
        public boolean runNoiseFillAsync = false;
    }

    @Comment("Async-Pathfinding optimization options")
    public Pathfinding pathfinding = new Pathfinding();
    public static class Pathfinding {
        @AlwaysAtTop
        public boolean enableThreadedPathfinding = true;
        public boolean useThreadedWorldForScheduling = true;

        @PositiveNumericValue
        public int maxProcessors = 2;

        @PositiveNumericValue
        public int keepAlive = 60;
    }


    @Comment("Threaded EntityTracking options")
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

    @Comment("Delays the inventory change trigger to tick at an interval to avoid excessive usage of advancement updates and recipe updates")
    public int skipTicksAdvancements = 3;

    @Comment("Disables the ticking of a useless secondary poi sensor")
    public boolean skipUselessSecondaryPoiSensor = true;

    @Comment("Optimizes piston block entities")
    public boolean optimizePistonMovingBlockEntity = true;

    @Comment("More efficiently clumps XP orbs")
    public boolean clumpOrbs = true;

    @Comment("Use faster sin/cos math operations")
    public boolean useCompactSineLUT = true;

    @Comment("TNT-related optimizations")
    public TNT tnt = new TNT();
    public static class TNT {
        public boolean enableFasterTntOptimization = true;
        public boolean explosionNoBlockDamage = false;
        public double tntRandomRange = -1;

        @Comment("Enables 'merge tnt logic', which makes it so that nearby tnt are merged together, increasing the power of 1 tnt explosion and reducing the amount of explosions. Helpful for anarchy servers")
        public boolean mergeTntLogic = false;

        @Comment("Max TNT primed for merging logic to start. Requires 'mergeTntLogic' to be enabled")
        public int maxTntPrimedForMerge = 100;
    }


    @Comment("Amount of entities to summon per tick from the summon command")
    public int summonCommandBatchCount = 50;

    @Comment("Batches summon command tasks to spread across multiple ticks, preventing the server from freezing for multiple seconds when processing higher summon counts")
    public boolean batchSummonCommandTasks = true;

    @Comment("Ignore \"<player> moved too quickly\" if the server is lagging. Improves general gameplay experience of the player when the server is lagging, as they wont get lagged back")
    public boolean ignoreMovedTooQuicklyWhenLagging = true;

    @Comment("Masks for goals. Allows disabling and adding delays to the tick rate of the goal. The 'goalClass' must be the class name of the goal. Like \"net.minecraft.entity.goal.ExampleGoal\", and if its a subclass, then \"net.minecraft.entity.goal.RootClass$ExampleGoalInSubClass\"")
    public List<GoalMask> entityGoalMasks = new ArrayList<>();
    public static class GoalMask {
        public String goalClass;
        public boolean disableGoal = false;
        public int goalTickDelay = 0;
    }


    @Comment("Enable the server watchdog")
    public boolean enableWatchdog = true;

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


    @Comment("Allows configuration of how entities are ticked, and if they should be ticked based on the type")
    public List<EntityMask> entityMasks = new ArrayList<>();
    public static class EntityMask {
        public String type;
        public boolean shouldTick = true;
        public int tickRate = 1;
    }


    @Experimental
    @Comment("Moves entity ticking to their own async scheduler. Benefits SOME servers, not all. Best helps servers where players are primarily in 1 world spread out.")
    public boolean threadedEntityTicking = false;

    @Comment("Disables entity pushing, but the player can still be pushed. Immensely optimizes entity performance with lots of crammed entities")
    public boolean disableEntityPushing = false;

    @Comment("Ignores messages like 'moved too quickly' and 'moved wrongly'")
    public boolean alwaysAllowWeirdMovement = true;

    @Comment("Prevents players being disconnected by disconnect.spam")
    public boolean disableDisconnectSpam = false;

    @Comment("Defines a percentage of which the server will apply to the velocity applied to item entities dropped on death. 0 means it has no velocity, 1 is default.")
    public double itemEntitySpreadFactor = 1;

    @Comment("Disables saving snowball entities. This patches certain lag machines.")
    public boolean disableSnowballSaving = false;

    @Comment("Disables saving firework entities. This patches certain lag machines.")
    public boolean disableFireworkSaving = false;
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
    }

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


    @Comment("Determines if end crystals should explode in a chain reaction, similar to how tnt works when exploded")
    public boolean chainEndCrystalExplosions = false;

    @Comment("Fixes MC-258859, fixing what Minecraft classifies as a 'slope', fixing some visuals with biomes like Snowy Slopes, Frozen Peaks, Jagged Peaks, Terralith & more")
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

    @Comment("Disables fluid ticking on chunk generation")
    public boolean disableFluidTickingInPostProcessGenerationStep = false;

    @Comment("Disables leaf block decay")
    public boolean disableLeafDecay = false;

    @Comment("Replaces papers version of the spark-paper module with our own")
    public boolean replaceSparkModule = true;

    @Comment("Moves player joining to an isolated queue-thread, severely reducing lag when players are joining, due to blocking tasks now being handled off any tickloops")
    public boolean asyncPlayerJoining = true;

    @Comment("Allows configurability of the distance of which certain objects need to be from a player to tick, like chunks, block entities, etc. This can cause major behavior changes.")
    public TickDistanceMaps tickDistanceMaps = new TickDistanceMaps();
    public static class TickDistanceMaps {
        @NonNegativeNumericValue
        @Comment("Controls the radius for chunk ticking, allowing configurability of random tick distances, block tick distances, and chunk tick distances")
        public int chunkTickingRadius = 5;

        @Comment("Enables the override that applies the 'chunkTickingRadius'")
        public boolean enableChunkDistanceMapOverride = false;

        @Comment("Enables the chunk ticking of spawn chunks")
        public boolean includeSpawnChunks = true;

        @NonNegativeNumericValue
        @Comment("Controls the distance defined in the nearby player updates for 'TICK_VIEW_DISTANCE', affects per-player mob spawning")
        public int nearbyPlayersTickDistance = 4;

        @Comment("Enables the override that applies the `nearbyPlayersTickDistance`")
        public boolean enableNearbyPlayersTickViewDistanceOverride = false;

        @NonNegativeNumericValue
        @Comment("Controls the distance defined in the nearby player updates for `SPAWN_RANGE`, affects the local mob cap")
        public int playerSpawnTrackingRange = ChunkTickConstants.PLAYER_SPAWN_TRACK_RANGE; // 8

        @Comment("Enables the override that applies 'playerSpawnTrackingRange'")
        public boolean enableNearbyPlayersSpawnRangeOverride = false;
    }


    @Comment("Disables the world weather cycle")
    public boolean disableWeatherCycle = false;

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

    @Comment("Disables the inventory change criterion trigger. Some advancements will not work! 'skipTicksAdvancements' will not work either.")
    public boolean disableInventoryChangeCriterionTrigger = false;

    @Comment("Caches the command block parse results, significantly reducing performance impacts from command blocks(given parsing is often times half the command blocks tick time)")
    public boolean cacheCommandBlockParseResults = false;

    @Comment("Enables the development GUI tick graph rendering(another window of the Minecraft Server GUI), disabled by '--nogui' arg")
    public boolean enableDevelopmentTickGuiGraph = false;

    @Comment("Disables being disconnected from 'multiplayer.disconnect.invalid_player_movement', and just silently declines the packet handling.")
    public boolean gracefulTeleportHandling = true;

    @Comment("Uses euclidean distance squared algorithm for determining chunk task priorities(like generation, loading, etc).")
    public boolean useEuclideanDistanceSquaredChunkPriorities = true;

    @Comment("Configure the max amount of bonus damage the mace item can apply")
    public int maxMaceDamageBonus = -1;

    @Comment("Ignores the players 'takeXpDelay' field, allowing players to insta-absorb experience orbs")
    public boolean instantAbsorbXpOrbs = false;

    @Comment("Uses a 'dummy inventory' for passing the InventoryMoveEvent, which avoids unneeded resources spent on building a bukkit Inventory.")
    public boolean useDummyInventoryForHopperInventoryMoveEvent = true;

    private static <T extends Config> @NotNull ConfigSerializer<T> buildSerializer(Configuration config, Class<T> configClass) {
        AnnotationBasedYamlSerializer<T> serializer = new AnnotationBasedYamlSerializer<>(config, configClass);
        ConfigurationUtils.extractKeys(serializer.getConfigClass());
        serializer.registerAnnotationHandler(Comment.class, (yamlWriter, indent, _, _, comment) -> {
            if (comment.breakLineBefore()) {
                yamlWriter.append("\n");
            }
            Arrays.stream(comment.value()).forEach(val -> yamlWriter.append(indent).append("## ").append(val).append("\n"));
        });
        serializer.registerAnnotationValidator(Range.class, (key, _, range, value) -> {
            if (value instanceof Number number) {
                float floatValue = number.floatValue();
                int from = range.from();
                int to = range.to();
                if (!(range.inclusive() ? (floatValue >= from && floatValue <= to) : floatValue > from && floatValue < to)) {
                    throw new ValidationException("Number for key, '" + key + "' was out of bounds! Value must be between " + from + " and " + to);
                }
                return true;
            } else {
                throw new ValidationException("Range validation was applied to a non-numeric object.");
            }
        });
        serializer.registerAnnotationValidator(EnumValue.class, (_, _, enumValue, value) -> {
            @SuppressWarnings("rawtypes") Class<? extends Enum> enumClass = enumValue.enumValue();

            if (!enumClass.isEnum()) {
                throw new ValidationException("EnumValue annotation applied to a non-enum type.");
            }

            Object[] enumConstants = enumClass.getEnumConstants();

            if (value instanceof String str) {
                String normalized = str.trim().toUpperCase(Locale.ROOT);
                for (Object constant : enumConstants) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(normalized)) {
                        return true;
                    }
                }
            } else if (value instanceof Enum<?> enumInstance) {
                if (enumClass.isInstance(enumInstance)) {
                    return true;
                }
            }

            throw new ValidationException("Invalid enum value: " + value);
        });
        serializer.registerAnnotationValidator(NegativeNumericValue.class, (_, _, _, value) -> {
            if (value instanceof Number number) {
                if (!(number.floatValue() < 0)) {
                    throw new ValidationException("Value must be a negative value");
                }
                return true;
            }
            throw new ValidationException("NegativeNumericValue validation applied to a non-numeric object.");
        });
        serializer.registerAnnotationValidator(NonNegativeNumericValue.class, (_, _, _, value) -> {
            if (value instanceof Number number) {
                if (!(number.floatValue() >= 0)) {
                    throw new ValidationException("Value must be a non-negative value");
                }
                return true;
            }
            throw new ValidationException("NonNegativeNumericValue validation applied to a non-numeric object.");
        });
        serializer.registerAnnotationValidator(NonPositiveNumericValue.class, (_, _, _, value) -> {
            if (value instanceof Number number) {
                if (!(number.floatValue() <= 0)) {
                    throw new ValidationException("Value must be a non-positive value");
                }
                return true;
            }
            throw new ValidationException("NonPositiveNumericValue validation applied to a non-numeric object.");
        });
        serializer.registerAnnotationValidator(PositiveNumericValue.class, (_, _, _, value) -> {
            if (value instanceof Number number) {
                if (!(number.floatValue() > 0)) {
                    throw new ValidationException("Value must be a positive value");
                }
                return true;
            }
            throw new ValidationException("PositiveNumericValue validation applied to a non-numeric object.");
        });
        serializer.registerPostSerializeAction((context) -> {
            INSTANCE = context.configuration();

            System.setProperty("com.ishland.c2me.opts.natives_math.duringGameInit", "true");
            boolean configured = INSTANCE.chunks.nativeAccelerationEnabled;
            if (configured) {
                try {
                    Class.forName("io.canvasmc.canvas.util.NativeLoader").getField("lookup").get(null);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            ThreadedTracker.init();
            ChunkSendingExecutor.init();
            for (final GoalMask goalMask : INSTANCE.entityGoalMasks) {
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
            for (final EntityMask entityMask : INSTANCE.entityMasks) {
                if (!CHECK_ENTITY_MASKS) {
                    LOGGER.warn("An EntityMask was registered, please be very careful with this, as it can make the movement of entities a lot more choppy(if using delays)");
                    CHECK_ENTITY_MASKS = true;
                }
                LOGGER.info("Registered EntityMask for '{}'", entityMask.type);
                COMPILED_ENTITY_MASK_LOCATIONS.add(ResourceLocation.parse(entityMask.type));
            }

            if (context.configuration().asyncPlayerJoining) {
                //noinspection resource
                new PlayerJoinThread("AsyncPlayerJoinThread", "player join thread");
            }
        });

        return serializer;
    }

    public static Config init() {
        ConfigurationManager.register(Config.class, Config::buildSerializer);
        return INSTANCE;
    }

}
