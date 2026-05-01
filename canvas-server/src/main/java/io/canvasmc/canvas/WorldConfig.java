package io.canvasmc.canvas;

import io.canvasmc.canvas.configuration.ConfigurationProvider;
import io.canvasmc.canvas.configuration.Part;
import io.canvasmc.canvas.configuration.Resolver;
import io.canvasmc.canvas.configuration.Style;
import io.canvasmc.canvas.configuration.Validator;
import java.nio.file.Path;
import io.papermc.paper.threadedregions.TickRegions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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

    private final ServerLevel world;

    public WorldConfig() {
        this(null);
    }

    public WorldConfig(final ServerLevel world) {
        this.world = world;
    }

    public static WorldConfig buildForWorld(final @NonNull ServerLevel world, final ResourceKey<Level> dimension) {

        // we build it as a patch here, and from here we can set the world properly
        final WorldConfig[] result = new WorldConfig[1];

        ConfigurationProvider.buildPatchableConfiguration(
            MinecraftServer.getServer().storageSource.getDimensionPath(dimension)
                .resolve("canvas-patch.yml"),
            BASE_FILE,
            () -> new WorldConfig(world),
            new Resolver<>() {
                @Override
                public void onFinishLoad(final WorldConfig instance) {
                    LOGGER.info("Loaded Canvas config patch for world {}", dimension.identifier());

                    result[0] = instance;

                    instance.onLoad();
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

    public boolean isTiedToWorld() {
        return world == null;
    }

    public ServerLevel getWorld() {
        if (!isTiedToWorld()) {
            throw new IllegalStateException("This configuration is not tied to any world");
        }
        return world;
    }

    private void onLoad() {

        // validate the object here too, because some users may do
        // something stupid in the patch variant

        Validator.validateObject(this);
    }

    {
        option("enableTpsBar")
            .docs(
                "Enables a regionized TPS-Bar implementation for Canvas.",
                "To enable the tps-bar, use the \"/tpsbar\" command"
            );
        option("tpsBarFormat")
            .docs(
                "MiniMessage-formatted line for the TPS bar. Placeholders are <tps>, <mspt>, <util>, and <players>.",
                "Legacy tokens(%tps%, %mspt%, %util%, %players%) are also accepted and auto-converted."
            ).greedyString();
    }

    public boolean enableTpsBar = true;
    public String tpsBarFormat = DEFAULT_TPSBAR_FORMAT;

    public Visuals visuals = new Visuals();
    public static class Visuals extends Part {
        public boolean hideFlamesOnEntitiesWithFireResistance = false;
        public boolean hideFlamesOnEntitiesWithInvisibility = false;
    }
}
