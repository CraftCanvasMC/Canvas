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
import io.canvasmc.canvas.util.Json5SerializerImpl;
import io.canvasmc.canvas.util.Util;
import io.canvasmc.canvas.util.version.ApiClient;
import io.canvasmc.canvas.util.version.CanvasVersionFetcher;
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
import net.minecraft.world.level.Level;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

@Configuration("canvas-server")
public class Config {

    public static final ComponentLogger LOGGER = ComponentLogger.logger("Canvas");

    // Note: this field should never be used during POST, use 'context.configuration()' instead
    public static Config INSTANCE;

    public static final Consumer<String> GLOBAL_BROADCAST = (msg) -> {
        Component component = Util.gradient("[CanvasMC] ",
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

    static {
        reload();
        // preload parallel search radius iteration early
        //noinspection ResultOfMethodCallIgnored
        ParallelSearchRadiusIteration.getSearchIteration(MoonriseConstants.MAX_VIEW_DISTANCE);
    }

    public static void reload() {
        GLOBAL_BROADCAST.accept("Instantiating Canvas configuration");
        long startNanos = System.nanoTime();
        INSTANCE = ConfigurationManager.register(Config.class, Config::buildGlobal).getConfig();
        GLOBAL_BROADCAST.accept("Finished Canvas config init in " + TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS) + "ms");
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
                        // GlobalConfiguration.ENABLE_FASTER_RANDOM = false;
                    }
                }
            }).build();
    }
}
