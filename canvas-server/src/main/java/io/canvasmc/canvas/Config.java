package io.canvasmc.canvas;

import ca.spottedleaf.moonrise.common.util.MoonriseConstants;
import ca.spottedleaf.moonrise.patches.chunk_system.util.ParallelSearchRadiusIteration;
import io.canvasmc.canvas.chunk.FluidPostProcessingMode;
import io.canvasmc.canvas.configuration.ConfigSerializer;
import io.canvasmc.canvas.configuration.Configuration;
import io.canvasmc.canvas.configuration.Json5Builder;
import io.canvasmc.canvas.configuration.internal.ConfigurationManager;
import io.canvasmc.canvas.configuration.jankson.Jankson;
import io.canvasmc.canvas.configuration.jankson.JsonObject;
import io.canvasmc.canvas.configuration.validator.NamespacedKeyValidator;
import io.canvasmc.canvas.configuration.validator.numeric.NonNegativeNumericValueValidator;
import io.canvasmc.canvas.configuration.validator.numeric.PositiveNumericValueValidator;
import io.canvasmc.canvas.configuration.validator.numeric.RangeValidator;
import io.canvasmc.canvas.configuration.writer.Comment;
import io.canvasmc.canvas.entity.EntityCollisionMode;
import io.canvasmc.canvas.simd.SIMDDetection;
import io.canvasmc.canvas.util.GsonTextFormatter;
import io.canvasmc.canvas.util.virtual.VirtualThreadUtils;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGeneratorFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.Yaml;

@Configuration("canvas-server")
public class Config {
    public static boolean ENABLE_FASTER_RANDOM = true;
    public static final ComponentLogger LOGGER = ComponentLogger.logger("Canvas");
    public static Config INSTANCE;

    public Scheduler scheduler = new Scheduler();
    public static class Scheduler {
        @Comment({
            "The maximum amount of time, in milliseconds, a thread will delay the execution of a scheduled task",
            "before allowing other threads to steal it for execution.",
            "Note: A smaller value reduces task start delays but increases potential task stealing between threads"
        })
        public long stealThresholdMillis = 3L;

        @Comment({
            "The maximum amount of time, in milliseconds, a thread is allowed to process intermediate tasks before",
            "yielding control.",
            "Note: Ensures fairness by preventing any single task from keeping a scheduler thread for too long"
        })
        public long taskTimeSliceMillis = 2L;
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

        @Comment({
            "Once one task is completed then the next task starts immediately, to prevent blocking threads while waiting to complete all tasks",
            "WARNING: May cause the sequence of future compose disorder"
        })
        public boolean useFasterStructureGenFutureSequencing = false;

        @Comment("Whether to use a rewritten random tick system to optimize the server")
        public boolean optimizeRandomTick = false;

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
    }

    @Comment("Configurations for enabling virtual threads for different thread pool executors")
    public VirtualThreads virtualThreads = new VirtualThreads();
    public static class VirtualThreads {
        @Comment("Enables virtual thread usage for the async scheduler executor")
        public boolean asyncScheduler = false;

        @Comment("Enables virtual thread usage for the chat executor")
        public boolean chatExecutor = false;

        @Comment("Enables virtual thread usage for the authenticator pool")
        public boolean authenticatorPool = false;

        @Comment("Enables virtual thread usage for the text filter executor")
        public boolean serverTextFilter = false;

        @Comment("Enables virtual thread usage for the text filter executor")
        public boolean tabCompleteExecutor = false;

        @Comment("Enables virtual thread usage for the profile lookup executor")
        public boolean profileLookupExecutor = false;
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

    @Comment("Restores vanilla loading and unloading behavior broken by Folia")
    public boolean restoreVanillaEnderPearlBehavior = false;

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
            "Fixes MC-119417 - https://bugs.mojang.com/browse/MC/issues/MC-119417",
            "A spectator can occupy a bed if they enter it and then are switched to spectator mode"
        })
        public boolean mc119417 = false; // TODO - fixed by Mojang, remove.

        @Comment({
            "Fixes MC-200418 - https://bugs.mojang.com/browse/MC/issues/MC-200418",
            "Cured baby zombie villagers stay as jockey variant"
        })
        public boolean mc200418 = false; // TODO - fixed by Mojang, remove.

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
            "Fixes MC-129909 - https://bugs.mojang.com/browse/MC/issues/MC-129909",
            "Players in spectator mode continue to consume foods and liquids shortly after switching game modes",
            "This also fixes MC-81773 - https://bugs.mojang.com/browse/MC/issues/MC-81773",
            "Bows and tridents drawn in survival/creative/adventure mode can be released in spectator mode"
        })
        public boolean mc129909 = false;

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
            "Fixes MC-69216 - https://bugs.mojang.com/browse/MC/issues/MC-69216",
            "Switching to spectator mode while fishing keeps rod cast"
        })
        public boolean mc69216 = false; // TODO - fixed by Mojang, remove.

        @Comment({
            "Fixes MC-30391 - https://bugs.mojang.com/browse/MC/issues/MC-30391",
            "Chickens, blazes and the wither emit particles when landing from a height, despite falling slowly"
        })
        public boolean mc30391 = false;

        @Comment({
            "Fixes MC-2025 - https://bugs.mojang.com/browse/MC/issues/MC-2025",
            "Mobs going out of fenced areas/suffocate in blocks when loading chunks"
        })
        public boolean mc2025 = false; // TODO - fixed by Mojang, remove.

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

    public ResourceKey<Level> fetchRespawnDimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(this.defaultRespawnDimensionKey));
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

    @Comment("Disables Canvas' fix to waypoints. Recommended if you do not need this fix or it's causing a plugin incompatibility")
    public boolean disableWaypointsFix = false;

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
        public boolean fishingRodPulls = false;

        @Comment("Configures the damage modifier per critical hit")
        public float criticalHitMultiplier = 1.5F;

        @Comment("Enables legacy blast protection")
        public boolean legacyBlastProtection = false;

        @Comment("Removes the red death animation seen on entities when killed.")
        public boolean removeRedDeathAnimation = false;
    }

    @Comment({
        "Use direct random implementation instead of delegating to Java's RandomGenerator.",
        "This may improve performance but potentially changes RNG behavior."
    })
    public boolean useDirectRandomImpl = false;

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

    @NamespacedKeyValidator.NamespacedKey
    @Comment("Defines non-tickable entities. This is defined by a leniently-parsed resource location associated with the entity type")
    public List<String> nonTickableEntities = new ArrayList<>();
    public record EntityNonTickableConf(String raw, ResourceLocation parsed) {}

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
        "Enables plugin compatibility mode.",
        "Canvas includes multiple fixes for the Folia server, including fixing multiple",
        "plugin APIs that are broken or not working in Folia. As a result, some plugins",
        "could potentially run into issues because of the API being restored, and the plugin",
        "not expecting said API to be restored or even working.",
        "This option disables those fixes, so plugin compatibility will be maintained."
    })
    public boolean pluginCompatibilityMode = true;

    private static <T extends Config> @NotNull ConfigSerializer<T> buildSerializer(Configuration config, Class<T> configClass) {
        return new Json5Builder<T>()
            .header("""
                /*
                  This is the main Canvas configuration file
                  All configuration options here are made for vanilla-compatibility
                  and not for performance. Settings must be configured specific
                  to your hardware and server type. If you have questions
                  join our discord at https://canvasmc.io/discord
                  As a general rule of thumb, do NOT change a setting if
                  you don't know what it does! If you don't know, ask!
                
                  This configuration file is based off of Json5, a Json
                  syntax with Java-like comment capabilities. You are
                  able to add your own custom comments to the configuration
                  however there must always be 1 comment per option, however you
                  may add as many comments as you want in the "header", or above
                  the root json block or else your comment may be deleted. Proper
                  indentation is forced, restarting the server will reformat your
                  comment to include proper indentation and remove trailing
                  whitespaces.
                
                  You may add comments to the header, here, remove comments anywhere,
                  or replace them wholesale. If you have any questions, ask in our
                  discord server.
                */
                """)
            .classOf(configClass)
            .post(context -> {
                INSTANCE = context.configuration();
                // build and print config tree.
                GsonTextFormatter formatter = new GsonTextFormatter(4);
                VirtualThreadUtils.init();
                LOGGER.info(Component.text("Printing configuration tree:").appendNewline().append(formatter.apply(context.contents())));

                // SIMD
                try {
                    SIMDDetection.isEnabled = SIMDDetection.canEnable(LOGGER);
                } catch (NoClassDefFoundError | Exception ignored) {
                    ignored.printStackTrace();
                }

                if (SIMDDetection.isEnabled) {
                    LOGGER.info("SIMD operations detected as functional. Will replace some operations with faster versions.");
                } else {
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
            }).build();
    }

    /**
     * Instantiates the CanvasMC configuration
     * @return the loaded configuration
     */
    public static Config init() {
        long startNanos = System.nanoTime();
        ConfigurationManager.register(Config.class, Config::buildSerializer);
        LOGGER.info("Finished Canvas config init in {}ms", TimeUnit.MILLISECONDS.convert(Util.getNanos() - startNanos, TimeUnit.NANOSECONDS));
        // init parallel search radius iteration early
        //noinspection ResultOfMethodCallIgnored
        ParallelSearchRadiusIteration.getSearchIteration(MoonriseConstants.MAX_VIEW_DISTANCE);
        return INSTANCE;
    }

    public static @NotNull Config getDefault() {
        // TODO - remove this on next Minecraft update. -- we are doing this for 1.22
        final Path path = Paths.get("./canvas-server.yml");
        if (Files.exists(path)) {
            LOGGER.info("Old configuration detected, migrating.");
            try {
                Yaml yaml = new Yaml();
                String yamlContent = Files.readString(path);
                String[] lines = yamlContent.split("\n", 2);
                String body = lines.length > 1 ? lines[1] : "";

                JsonObject object = new JsonObject();
                Map<String, Object> yamnlMap = yaml.load(new StringReader(body));
                io.canvasmc.canvas.configuration.writer.Util.migrate(
                    yamnlMap, object
                );
                Files.delete(path);
                LOGGER.info("Migration complete, reparsing");
                return Jankson.builder().build().fromJson(object, Config.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return new Config();
    }
}
