package io.canvasmc.canvas;

import io.canvasmc.canvas.config.*;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.Goal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@me.shedaniel.autoconfig.annotation.Config(name = "canvas_server")
public class Config implements ConfigData {

	public static final Logger LOGGER = LogManager.getLogger("CanvasConfig");
    public static final List<Class<? extends Goal>> COMPILED_DISABLED_GOAL_CLASSES = Collections.synchronizedList(new ArrayList<>());
    public static final List<ResourceLocation> COMPILED_LOCATIONS = Collections.synchronizedList(new ArrayList<>());
    public static boolean shouldCheckMasks = false;
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

    @IgnoreModifications
    @Comment("Disables all types of goals provided here. Must be the class name of the goal. Like \"net.minecraft.entity.goal.ExampleGoal\", and if its a subclass, then \"net.minecraft.entity.goal.RootClass$ExampleGoalInSubClass\"")
    public List<String> goalsToDisable = new ArrayList<>();

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
        public ResourceLocation type;
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
    @Comment("Whether to use an alternative strategy to make structure layouts generate slightly even faster than\n" +
             "## the default optimization this mod has for template pool weights. This alternative strategy works by\n" +
             "## changing the list of pieces that structures collect from the template pool to not have duplicate entries.\n" +
             "## \n" +
             "## This will not break the structure generation, but it will make the structure layout different than\n" +
             "## if this config was off (breaking vanilla seed parity). The cost of speed may be worth it in large\n" +
             "## modpacks where many structure mods are using very high weight values in their template pools.\n" +
             "## \n" +
             "## Pros: Get a bit more performance from high weight Template Pool Structures.\n" +
             "## Cons: Loses parity with vanilla seeds on the layout of the structure. (Structure layout is not broken, just different)")
    public boolean deduplicateShuffledTemplatePoolElementList = false;
    @Comment("Enables a port of the mod StructureLayoutOptimizer, which optimizes general Jigsaw structure generation")
    public boolean enableStructureLayoutOptimizer = true;

	public static Config init() {
		AutoConfig.register(Config.class, YamlConfigSerializerWithComments::new);
		INSTANCE = AutoConfig.getConfigHolder(Config.class).getConfig();
		Config defaulted = new Config();

		if (defaulted == null || INSTANCE == null) {
			throw new NullPointerException("Defaulted config or registered config was null!");
		}

		detectModifications(defaulted, INSTANCE, Config.class);
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
        for (final String goalClassName : INSTANCE.goalsToDisable) {
            try {
                //noinspection unchecked
                Class<? extends Goal> clazz = (Class<? extends Goal>) Class.forName(goalClassName);
                COMPILED_DISABLED_GOAL_CLASSES.add(clazz);
                LOGGER.info("Disabling Goal \"{}\"...", clazz.getSimpleName());
            } catch (ClassNotFoundException e) {
                LOGGER.error("Unable to locate goal class \"{}\". Unable to disable, skipping.", goalClassName, e);
                continue;
            }
        }
        for (final EntityMask entityMask : INSTANCE.entityMasks) {
            if (!shouldCheckMasks) {
                shouldCheckMasks = true;
            }
            COMPILED_LOCATIONS.add(entityMask.type);
        }
		return INSTANCE;
	}

	private static void detectModifications(Object obj1, Object obj2, Class<?> parentClass) {
		if (obj1 == null || obj2 == null) {
			throw new NullPointerException("One of the objects to compare is null!");
		}

		Class<?> clazz = obj1.getClass();
		Field[] fields = clazz.getDeclaredFields();

		for (Field field : fields) {
			field.setAccessible(true);
            if (field.isAnnotationPresent(IgnoreModifications.class)) {
                continue;
            }
			try {
				Object value1 = field.get(obj1);
				Object value2 = field.get(obj2);

				if (!Objects.equals(value1, value2) && !isInnerClassOf(value1.getClass(), parentClass)) {
					if (field.isAnnotationPresent(Experimental.class)) {
                        LOGGER.warn("====== WARNING: EXPERIMENTAL FEATURE ======");
                        LOGGER.warn("Field '{}' is marked as experimental and its value was changed to: {}", field.getName(), value2);
                        LOGGER.warn("Proceed with caution as this feature may be unstable or subject to change.");
                        LOGGER.warn("===========================================");
                    } else {
                        LOGGER.info("Detected modified arg, '{}', was changed to: {}", field.getName(), value2);
                    }
				}

				if (value1 != null && value2 != null && isInnerClassOf(value1.getClass(), parentClass)) {
					detectModifications(value1, value2, value1.getClass());
				}
			} catch (IllegalAccessException e) {
				LOGGER.error("Cannot access field: {}", field.getName());
			}
		}
	}

	private static boolean isInnerClassOf(@NotNull Class<?> clazz, Class<?> parentClass) {
		return clazz.getEnclosingClass() != null && clazz.getEnclosingClass().equals(parentClass);
	}
}
