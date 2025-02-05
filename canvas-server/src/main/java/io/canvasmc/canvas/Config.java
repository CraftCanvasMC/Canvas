package io.canvasmc.canvas;

import io.canvasmc.canvas.config.AnnotationBasedYamlSerializer;
import io.canvasmc.canvas.config.ConfigurationUtils;
import io.canvasmc.canvas.config.annotation.Comment;
import io.canvasmc.canvas.config.annotation.Experimental;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.serializer.ConfigSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.Goal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

@me.shedaniel.autoconfig.annotation.Config(name = "canvas_server")
public class Config implements ConfigData {

    public static final Logger LOGGER = LogManager.getLogger("CanvasConfig");
    public static final Map<Class<? extends Goal>, GoalMask> COMPILED_GOAL_MASKS = new ConcurrentHashMap<>();
    public static final List<ResourceLocation> COMPILED_ENTITY_MASK_LOCATIONS = Collections.synchronizedList(new ArrayList<>());
    public static boolean CHECK_ENTITY_MASKS = false;
    public static Config INSTANCE = new Config();

    // Threaded Dimensions
    @Comment("Determines if the server should tick the playerlist assigned to each world on their own level threads, or if it should tick on the main thread(globally)")
    public boolean runPlayerListTickOnIndependentLevel = true;
    @Comment("Amount of ticks until the level will resync time with the player")
    public int timeResyncInterval = 400;
    @Comment("Thread priority for level threads, must be a value between 1-10.")
    public int levelThreadPriority = 9;
    @Comment("In the ServerChunkCache, it schedules tasks to the main thread. Enabling this changes it to schedule to the level thread")
    public boolean useLevelThreadsAsChunkSourceMain = true;
    @Comment("Enables each world to have the \"empty server\" logic per world introduced in Minecraft 1.21.4")
    public boolean emptySleepPerWorlds = true;
    @Comment("Enables the \"threadedtick\" command, which is an implementation of the vanilla \"tick\" command for the Canvas threaded context")
    public boolean enableCanvasTickCommand = true;
    @Comment("Wraps the broadcast of section block updates in a synchronized lock")
    public boolean wrapBroadcastSynchronized = true;
    @Comment("Allows opening any type of door with your hand, including iron doors")
    public boolean canOpenAnyDoorWithHand = true;
    @Comment("Ensure correct doors. Schedules an extra update on the next tick to ensure the door doesnt get glitched when a Villager and Player both interact with it at the same time")
    public boolean ensureCorrectDoors = false;

    // Chunk Generation
    @Comment("Chunk-Gen related config options")
    public ChunkGeneration chunkGeneration = new ChunkGeneration();

    public static class ChunkGeneration {
        public long chunkDataCacheSoftLimit = 8192L;
        public long chunkDataCacheLimit = 32678L;
        public boolean allowAVX512 = false;
        public boolean nativeAccelerationEnabled = true;
        @Comment("Modifies what algorithm the chunk system will use to define thread counts. values: MOONRISE, C2ME, ANY, ALL")
        public String chunkWorkerAlgorithm = "MOONRISE";
    }

    // Async Pathfinding
    @Comment("Async-Pathfinding optimization options")
    public Pathfinding pathfinding = new Pathfinding();
    public static class Pathfinding {
        public boolean enableThreadedPathfinding = true;
        public boolean useThreadedWorldForScheduling = true;
        public int maxProcessors = 2;
        public int keepAlive = 60;
    }

	// Entity Tracking
	@Comment("Threaded EntityTracking options")
	public EntityTracking entityTracking = new EntityTracking();
	public static class EntityTracking {
		public boolean enableThreadedTracking = true;
		public int maxProcessors = 1;
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
    }

    public NoChatReports noChatReports = new NoChatReports();
    public static class NoChatReports {
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
    @Comment(value =
        {"Whether to use an alternative strategy to make structure layouts generate slightly even faster than",
            "the default optimization this mod has for template pool weights. This alternative strategy works by",
            "changing the list of pieces that structures collect from the template pool to not have duplicate entries.",
            "{line_break}",
            "This will not break the structure generation, but it will make the structure layout different than",
            "if this config was off (breaking vanilla seed parity). The cost of speed may be worth it in large",
            "modpacks where many structure mods are using very high weight values in their template pools.",
            "{line_break}",
            "Pros: Get a bit more performance from high weight Template Pool Structures.",
            "Cons: Loses parity with vanilla seeds on the layout of the structure. (Structure layout is not broken, just different)"},
        breakLineBefore = true)
    public boolean deduplicateShuffledTemplatePoolElementList = false;
    @Comment("Enables a port of the mod StructureLayoutOptimizer, which optimizes general Jigsaw structure generation")
    public boolean enableStructureLayoutOptimizer = true;
    @Comment("The value of which Minecraft will cap out the neighbor updates for that tick. Set to '-1' to disable the limit")
    public int maxChainedNeighborUpdates = 1_000_000;
    @Comment("Disables fluid ticking on chunk generation")
    public boolean disableFluidTickingInPostProcessGenerationStep = false;
    @Comment("Disables leaf block decay")
    public boolean disableLeafDecay = false;
    @Comment("Replaces papers version of the spark-paper module with our own")
    public boolean replaceSparkModule = true;

    @Comment("Related configuration options to chunk sending optimizations")
    public ChunkSending chunkSending = new ChunkSending();
    public static class ChunkSending {
        @Comment("Runs chunk sending off-level/main")
        public boolean asyncChunkSending = false;
        @Comment("Amount of threads to use for async chunk sending. This does nothing when 'useVirtualThreadExecutorForChunkSenders' is enabled")
        public int asyncChunkSendingThreadCount = 1;
        @Comment("Similar to the 'virtual-thread' options, this makes it so that the executor for chunk senders uses a virtual thread pool")
        public boolean useVirtualThreadExecutorForChunkSenders = false;
    }

    private static <T extends Config> @NotNull ConfigSerializer<T> buildSerializer(me.shedaniel.autoconfig.annotation.Config config, Class<T> configClass) {
        AnnotationBasedYamlSerializer<T> serializer = new AnnotationBasedYamlSerializer<>(config, configClass);
        ConfigurationUtils.extractKeys(serializer.getConfigClass());
        serializer.registerAnnotationHandler(Comment.class, (yamlWriter, indent, _, _, comment) -> {
            if (comment.breakLineBefore()) {
                yamlWriter.append("\n");
            }
            Arrays.stream(comment.value()).forEach(val -> yamlWriter.append(indent).append("## ").append(val).append("\n"));
        });
        serializer.registerPostSerializeAction((context) -> {
            INSTANCE = context.configuration();

            System.setProperty("com.ishland.c2me.opts.natives_math.duringGameInit", "true");
            boolean configured = INSTANCE.chunkGeneration.nativeAccelerationEnabled;
            if (configured) {
                try {
                    Class.forName("io.canvasmc.canvas.util.NativeLoader").getField("lookup").get(null);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            io.canvasmc.canvas.entity.tracking.ThreadedTracker.init();
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
        });

        return serializer;
    }

    public static Config init() {
        AutoConfig.register(Config.class, Config::buildSerializer);
        return INSTANCE;
    }

}
