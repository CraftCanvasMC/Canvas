package io.canvasmc.canvas;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.cloth.clothconfig.shadowed.blue.endless.jankson.Comment;
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

	private static final Logger LOGGER = LogManager.getLogger("CanvasConfig");
    public static final List<Class<? extends Goal>> COMPILED_DISABLED_GOAL_CLASSES = Collections.synchronizedList(new ArrayList<>());
	public static Config INSTANCE = new Config();

    // Threaded Dimensions
    @Comment("Determines if the server should tick the playerlist assigned to each world on their own level threads, or if it should tick on the main thread(globally)")
    public boolean runPlayerListTickOnIndependentLevel = true;
    @Comment("Amount of ticks until the level will resync time with the player")
    public int timeResyncInterval = 20;
    @Comment("Thread priority for level threads, must be a value between 1-10.")
    public int levelThreadPriority = 9;
    @Comment("In the ServerChunkCache, it schedules tasks to the main thread. Enabling this changes it to schedule to the level thread")
    public boolean useLevelThreadsAsChunkSourceMain = true;
    @Comment("Disables leaves from ticking")
    public boolean disableLeafTicking = true;
    @Comment("Enables each world to have the \"empty server\" logic per world introduced in Minecraft 1.21.4")
    public boolean emptySleepPerWorlds = true;
    @Comment("Enables the \"threadedtick\" command, which is an implementation of the vanilla \"tick\" command for the Canvas threaded context")
    public boolean enableCanvasTickCommand = true;

    // Chunk Generation
    @Comment("Chunk-Gen related config options")
    public ChunkGeneration chunkGeneration = new ChunkGeneration();
    public static class ChunkGeneration {
        public long chunkDataCacheSoftLimit = 8192L;
        public long chunkDataCacheLimit = 32678L;
        public boolean allowAVX512 = false;
        public boolean nativeAccelerationEnabled = true;
        @Comment("Experimental: Modifies chunk gen worker default algorithm to linearly scale better than Moonrises default.")
        public boolean useAlternativeAlgorithmForChunkWorkers = false;
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

	public static Config init() {
		AutoConfig.register(Config.class, JanksonConfigSerializer::new);
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
					LOGGER.info("Detected modified arg, '{}', was changed to: {}", field.getName(), value2);
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
