package io.canvasmc.canvas;

import io.canvasmc.canvas.configuration.ConfigurationProvider;
import io.canvasmc.canvas.configuration.Part;
import io.canvasmc.canvas.configuration.Resolver;
import io.canvasmc.canvas.configuration.Style;
import io.canvasmc.canvas.configuration.Validator;
import io.canvasmc.canvas.util.CanonicalReference;
import io.papermc.paper.threadedregions.TickRegions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorldConfig extends Part {

    // all constants for configurations go here

    public static final String DEFAULT_TPSBAR_FORMAT =
        "<gradient:blue:aqua><b>TPS:</b></gradient> <tps>  <dark_gray>-</dark_gray>  " +
            "<gradient:blue:aqua><b>MSPT:</b></gradient> <mspt>  <dark_gray>-</dark_gray>  " +
            "<gradient:blue:aqua><b>Util:</b></gradient> <util>  <dark_gray>-</dark_gray>  " +
            "<gradient:blue:aqua><b>Players:</b></gradient> <players>";
    public static final String DEFAULT_RAMBAR_FORMAT =
        "<gradient:green:dark_green><b>RAM:</b></gradient> <used>/<xmx> <dark_gray>(</dark_gray><percent><dark_gray>%)</dark_gray>";

    // note that Canvas core utilities and loggers and such should go in the global configuration class, as this one
    // doesn't entirely seem that appropriate for that sort of stuff

    // we have a logger internally here for world-config related things, and should not be used globally. the global
    // config class should be the logger publicly used

    private static final Logger LOGGER = LoggerFactory.getLogger("CanvasWorlds");

    private static final Path BASE_FILE = Path.of("config/canvas-worlds.yml").toAbsolutePath().normalize();

    // for the default configuration, we do need a solid configuration for this or else the patchable
    // variant will fail to load, so we load this in the static block

    static {
        //noinspection ResultOfMethodCallIgnored
        GlobalConfiguration.getInstance(); // preload global

        reload();
    }

    public static void reload() {
        ConfigurationProvider.buildSolidConfiguration(
            BASE_FILE,
            WorldConfig::new,
            GlobalConfiguration.CHAR_LIM,
            new Resolver<>() {
                @Override
                public void onDiffAdd(final String fullyQualifiedName) {
                    LOGGER.info("Added new world configuration option, '{}'", fullyQualifiedName);
                }

                @Override
                public void onDiffRemove(final String fullyQualifiedName) {
                    LOGGER.warn("World configuration option '{}' no longer exists and is now removed.", fullyQualifiedName);
                }

                @Override
                public void onFinishLoad(final WorldConfig instance) {

                    // validate the configuration so users don't end up doing a stupid
                    Validator.validateObject(instance);

                    // note that we do not do anything else on post load for the default
                    // configuration file, only patchable instances need post load

                }
            },
            Style.create()
                .literal("Worlds default configuration file for CanvasMC").endLine()
                .blank()
                .wordWrap(
                    "This is the defaults for the per-world configuration file for CanvasMC.",
                    "Each option can be overridden by the patch variant in each world folder. You are",
                    "free to modify, add, or remove comments as you please."
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

        // on reload, if the server started, we need to swap out the configs
        if (TickRegions.started) {
            for (final ServerLevel world : MinecraftServer.getServer().getAllLevels()) {

                // this will swap the config with the new patchable variant
                // it mimics the startup process of the patchable configs

                world.reloadCanvasConfig();
            }
        }
    }

    public static WorldConfig buildForWorld(final @NonNull ServerLevel world, final ResourceKey<Level> dimension) {

        // we build it as a patch here, and from here we can set the world properly
        final WorldConfig[] result = new WorldConfig[1];

        ConfigurationProvider.buildPatchableConfiguration(
            MinecraftServer.getServer().storageSource.getDimensionPath(dimension)
                .resolve("canvas-patch.yml"),
            BASE_FILE,
            WorldConfig::new,
            new Resolver<>() {
                @Override
                public void onFinishLoad(final WorldConfig instance) {
                    LOGGER.info("Loaded Canvas config patch for world {}", dimension.identifier());

                    result[0] = instance;

                    instance.onLoad(world);
                }
            },
            Style.create()
                .literal("Patch configuration file for world " + dimension.identifier()).endLine()
                .blank()
                .wordWrap(
                    "This configuration file can be used to override the values in the default configuration",
                    "for worlds defined in \"/config/canvas-worlds.yml\""
                ).endLine()
                .blank()
                .wordWrap(
                    "To override values in this, just copy the same option path to override the value. Think of",
                    "the values you place in here for each option as a replacement for the default one for this world specifically"
                )
                .compile(60)
        );

        return result[0];
    }

    private void onLoad(final @NonNull ServerLevel world) {

        // validate the object here too, because some users may do
        // something stupid in the patch variant

        Validator.validateObject(this);

        // basically we just need to parse the loadChunks strings into identifiers
        // and then from there we can access all entity types and use that as the predicate
        final EntityType<?>[] entityTypes = entities.projectiles.loadChunks.stream()
            .map(Identifier::parse)
            .map(BuiltInRegistries.ENTITY_TYPE::getValue)
            .toList().toArray(new EntityType<?>[0]);

        if (entityTypes.length > 0) {
            LOGGER.info("Set {} projectile types to load chunks in {}", entityTypes.length, world.dimension().identifier().toDebugFileName());
        }

        // set the predicate now
        entities.projectiles.compiledPredicate.setValue((projectile) -> {
            // this is an immutable array, so realistically this is fine?
            for (final EntityType<?> entityType : entityTypes) {
                if (projectile.is(entityType)) return true;
            }
            // if nothing matched, it doesn't have an override
            return false;
        });
    }

    {
        option("regionBars").docs("Region resource bars config. You can toggle these on players with the \"/regionbar\" command");
    }

    public RegionBars regionBars = new RegionBars();
    public static class RegionBars extends Part {

        {
            option("enableTpsBar").docs("Enables a regionized TPS-Bar implementation for Canvas.");
            option("tpsBarFormat")
                .docs(
                    "MiniMessage-formatted line for the TPS bar. Placeholders are <tps>, <mspt>, <util>, and <players>.",
                    "Legacy tokens(%tps%, %mspt%, %util%, %players%) are also accepted and auto-converted."
                ).greedyString();
            option("enableRamBar").docs("Enables a regionized RAM-Bar implementation for Canvas.");
            option("ramBarFormat")
                .docs(
                    "MiniMessage-formatted line for the RAM bar. Placeholders are <used>, <xmx>, <percent>.",
                    "Legacy tokens(%used%, %xmx%, %percent%) are also accepted and auto-converted."
                ).greedyString();
        }

        public boolean enableTpsBar = true;
        public String tpsBarFormat = DEFAULT_TPSBAR_FORMAT;

        public boolean enableRamBar = true;
        public String ramBarFormat = DEFAULT_RAMBAR_FORMAT;
    }

    public Visuals visuals = new Visuals();
    public static class Visuals extends Part {

        {
            option("particles")
                .docs(
                    "All options unless explicitly specified otherwise are unnecessary packets",
                    "sent to the client and can be safely disabled without Vanilla deviation"
                );
        }

        public boolean hideFlamesOnEntitiesWithFireResistance = false;
        public boolean hideFlamesOnEntitiesWithInvisibility = false;

        public Particles particles = new Particles();
        public static class Particles extends Part {

            {
                option("disableFallParticles").docs("Note that when enabled this option breaks Vanilla visual compatibility");
                option("disableNewCombatParticles").docs("Note that when enabled this option breaks Vanilla visual compatibility");
            }

            public boolean disableSprintParticles = false;
            public boolean disableFallParticles = false;
            public boolean disableDeathParticles = false;
            public boolean disableEffectParticles = false;
            public boolean disableWaterSplashParticles = false;
            public boolean disableBubbleColumnParticles = false;
            public boolean disableNewCombatParticles = false;
        }

        {
            option("dontTrackPlayersInEntityTracking").docs("This makes players not able to see other players in this world when enabled");
        }

        // useful for AFK worlds
        public boolean dontTrackPlayersInEntityTracking = false;
    }

    {
        option("chainEndCrystalExplosions").docs("When enabled, this chains end crystal explosions instead of executing them all in 1 tick");
        option("disableSnowLightChecks").docs("Disables snow light checks, so snow layers never melt");
        option("disableGrassLightChecks").docs("Disables grass light checks, so grass always spreads despite being in darkness");
    }

    public boolean chainEndCrystalExplosions = false;
    public boolean disableSnowLightChecks = false;
    public boolean disableGrassLightChecks = false;

    public Farming farming = new Farming();
    public static class Farming extends Part {

        {
            option("disableFarmlandTrampling").docs("Makes falling on farmland not turn it back to dirt");
            option("cropsIgnoreLightCheck").docs("Makes crops ignore sunlight requirements when planting");
        }

        public boolean farmlandAlwaysMoist = false;
        public boolean disableLeafDecay = false;
        public boolean cropsIgnoreLightCheck = false;
        public boolean disableFarmlandTrampling = false;
    }

    public Entities entities = new Entities();
    public static class Entities extends Part {

        {
            option("fastOrbs")
                .docs(
                    "Removes the XP pickup delay and uses a faster merging system. Can be very useful for",
                    "heavy XP farms. This changes how orbs are merged, allowing 1 orb to contain an infinite",
                    "amount of merged experience and then be collected instantly, much faster than the Vanilla",
                    "equivalent. This also fixes \"ghost orbs\", since instead of increasing the count, this",
                    "increases the value of the orb"
                );

            option("entityCollisionMode")
                .docs(
                    Style.wrap("The entity collision mode for the server")
                        .defineEnum(EntityCollisionMode.class, (mode) -> {
                            return switch (mode) {
                                case VANILLA -> "Default, all entities have collisions";
                                case ONLY_PUSHABLE_PLAYERS_SMALL ->
                                    "Only players are pushable by entities, searching in a small radius";
                                case ONLY_PUSHABLE_PLAYERS_LARGE ->
                                    "Only players are pushable by entities, searching in the normal radius";
                                case NO_COLLISIONS -> "Disables entity collisions entirely";
                            };
                        })
                );
        }

        public boolean fastOrbs = false;

        public ItemEntities itemEntities = new ItemEntities();
        public static class ItemEntities extends Part {

            {
                option("itemEntityVelocityOnDeathFactor")
                    .docs(
                        "A multiplied value for the velocity of item entities dropped on death. The smaller the value,",
                        "the less it spreads out. The larger the value, the more it spreads out"
                    ).greaterThanOrEqualTo(0.0F);
                option("itemEntitiesWaitTwoSecondsForMergeCheckAlways")
                    .docs(
                        "Item entity merge checks during the tick are always intervaled by 2 seconds unless the item is moving,",
                        "of which then this interval is 2 ticks. This forces the interval to always be 2 ticks, reducing the amount",
                        "of times item entities check to merge"
                    );
            }

            public boolean itemEntitiesImmuneToExplosions = false;
            public boolean itemEntitiesImmuneToLightning = false;
            public double itemEntityVelocityOnDeathFactor = 1.0D;
            public boolean itemEntitiesWaitTwoSecondsForMergeCheckAlways = false;
        }

        public EntityCollisionMode entityCollisionMode = EntityCollisionMode.VANILLA;
        public enum EntityCollisionMode {
            VANILLA,
            ONLY_PUSHABLE_PLAYERS_LARGE,
            ONLY_PUSHABLE_PLAYERS_SMALL,
            NO_COLLISIONS;

            private static final EntityCollisionMode[] VALUES = values();
            private final int id;

            EntityCollisionMode() {
                this.id = ordinal();
            }

            public static EntityCollisionMode fromOrdinal(int ordinal) {
                if (ordinal < 0 || ordinal >= VALUES.length) {
                    return VANILLA;
                }
                return VALUES[ordinal];
            }

            public int getId() {
                return this.id;
            }

            public boolean onlyPlayersPushable() {
                return id == ONLY_PUSHABLE_PLAYERS_LARGE.id || id == ONLY_PUSHABLE_PLAYERS_SMALL.id;
            }

            public boolean allEntitiesCanBePushed() {
                return id == VANILLA.id;
            }

            public boolean noCollisions() {
                return id == NO_COLLISIONS.id;
            }

            public boolean isLargePushRange() {
                return id == ONLY_PUSHABLE_PLAYERS_LARGE.id;
            }
        }

        public Projectiles projectiles = new Projectiles();
        public static class Projectiles extends Part {

            {
                option("loadChunks").docs("Specify which projectiles should load chunks when moving. Only works when thrown by players");
                option("crossRegionRedirectableProjectileDeflection")
                    .docs(
                        Style.wrap(
                            "Restores Vanilla redirect behavior for arrow hits on redirectable projectiles",
                            "like wind charges and fireballs across region threads."
                        )
                        .blank()
                        .wordWrap(
                            "It is recommended to set \"max-arrow-despawn-invulnerability: disabled\"",
                            "in paper-world-defaults.yml to prevent arrows from despawning"
                        )
                    );
            }

            public int maxProjectileChunkLoadsPerTick = 10;
            public int maxProjectileChunkLoadsPerProjectileBeforeRemoval = 10;
            public List<String> loadChunks = new ArrayList<>();
            public boolean crossRegionRedirectableProjectileDeflection = false;

            private final CanonicalReference<Predicate<Projectile>> compiledPredicate = new CanonicalReference<>();

            public Predicate<Projectile> getDoesProjectileLoadChunksOverridePredicate() {
                return compiledPredicate.value();
            }
        }

        {
            option("skeletonAimAccuracy").docs("Defines the inaccuracy of skeleton bow shots. 14 is Vanilla, higher is more inaccurate and lower is more accurate");
            option("villagers")
                .docs(
                    "Options regarding villagers. The options for reducing POI search ranges shrink the search radius(in blocks)",
                    "from 48 to 16, which can help improve tick times with little Vanilla deviation, however this will prevent Villagers",
                    "from acquiring POIs between 17-48 blocks away"
                );
        }

        public double skeletonAimAccuracy = 14.0D;

        public Villagers villagers = new Villagers();
        public static class Villagers extends Part {

            {
                option("villagerAcquirePoiTasksLoadChunks")
                    .docs("Whether the server should allow Villagers to load unloaded chunks for Villagers to locate POIs");
            }

            public boolean villagerAcquirePoiTasksLoadChunks = true;
            public boolean reduceJobSitePoiSearchRange = false;
            public boolean reduceHomePoiSearchRange = false;
            public boolean reduceMeetingPointPoiSearchRange = false;
        }
    }

    public Combat combat = new Combat();
    public static class Combat extends Part {

        {
            option("restoreOldAttackDelayMechanics").docs("Restores 1.8 attack delay mechanics");
            option("imitateSwordBlocking").docs("Restores 1.8 sword blocking mechanics. Might not work for <1.21.4 clients");
        }

        public boolean restoreOldAttackDelayMechanics = false;
        public boolean imitateSwordBlocking = false;

        public Mace mace = new Mace();
        public static class Mace extends Part {

            {
                option("ignoreFallDistance").docs("Removes the fall distance amplifier from maces");
                option("fallDistanceLimit").docs("The limit before fall distance scaling stops working for mace damage bonuses");
            }

            public boolean ignoreFallDistance = false;
            public double fallDistanceLimit = -1.0D;
        }

        {
            // TODO - can we restore this? the issue with this is that plugins can change this, and entities
            //        can change worlds, which complicates this logic
            // option("invulnerabilityTicks")
            //     .docs(
            //         "When an entity is damaged, it has 10 ticks of \"invulnerability time\" until it can be",
            //         "damaged next. This configuration lets you control the amount of invulnerability time",
            //         "that is applied to the entity. 0 meaning invulnerability isn't applied"
            //     ).greaterThan(0.0F);
            option("criticalHitMultiplier").docs("Configures the damage modifier per critical hit");
            option("removeRedDeathAnimation").docs("Removes the red death animation seen on entities when killed");
            option("useLegacyBlastProtection").docs("Restores the blast protection logic from before 1.21");
        }

        public boolean disableSweepingEdge = false;
        public boolean disableNetheriteKnockbackResistance = false;
        public boolean disableCritsWhileSprinting = false;
        // public int invulnerabilityTicks = 10;
        public boolean allowFishingRodsToPullEntities = true;
        public float criticalHitMultiplier = 1.5F;
        public boolean removeRedDeathAnimation = false;
        public boolean useLegacyBlastProtection = false;
        public boolean snowballCanKnockbackPlayers = false;
        public boolean eggCanKnockbackPlayers = false;
    }

    // TODO - move this to "blocks" config
    public Spawner spawner = new Spawner();
    public static class Spawner extends Part {

        {
            // option("minSpawnDelay").docs("The minimum delay between spawner spawns");
            // option("maxSpawnDelay").docs("The maximum delay between spawner spawns");
            // option("spawnCount").docs("The amount of entities a spawner spawns per cycle");
            // option("maxNearbyEntities").docs("The maximum amount of nearby entities before the spawner stops ticking");
            // option("requiredPlayerRange").docs("The required player range for spawners to activate");
            // option("spawnRange").docs("The maximum position range for spawned entities");
            option("disableMaxNearbyEntitiesCheck").docs("Disables the spawner max nearby entities check");
            option("spawnedEntitiesHaveNoCollision").docs("Disables collisions for entities spawned by spawners");
        }

        // TODO - can we bring these back or find suitable replacements?
        // public int minSpawnDelay = 200;
        // public int maxSpawnDelay = 800;
        // public int spawnCount = 4;
        // public int maxNearbyEntities = 6;
        // public int requiredPlayerRange = 16;
        // public int spawnRange = 4;
        public boolean disableMaxNearbyEntitiesCheck = false;
        public boolean spawnedEntitiesHaveNoCollision = false;
    }

    {
        option("waypointUpdateScale")
            .docs(
                "Controls how quickly Canvas' waypoints system falls off with distance between players.",
                "You can read more about how this new system works and play around with this configuration",
                "here: https://docs.canvasmc.io/canvas/info/waypoints/"
            );
        option("disableCriterionTrigger").docs("Disables all criterion triggers. Advancements will not work!");
        option("cactusCheckSurvivalBeforeGrowth").docs("Check if a cactus can survive before growing. Heavily optimizes cacti farms");
        option("enableSuffocationOptimization")
            .docs(
                "Optimizes the suffocation check by selectively skipping the check in a way that still appears Vanilla"
            );
    }

    public double waypointUpdateScale = 4000.0D;
    public boolean disableCriterionTrigger = false;
    public boolean cactusCheckSurvivalBeforeGrowth = false;
    public boolean enableSuffocationOptimization = false;

}
