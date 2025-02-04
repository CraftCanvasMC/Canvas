package io.canvasmc.canvas.spark;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import me.lucko.spark.api.Spark;
import me.lucko.spark.paper.PaperClassSourceLookup;
import me.lucko.spark.paper.PaperCommandSender;
import me.lucko.spark.paper.PaperPlatformInfo;
import me.lucko.spark.paper.PaperPlayerPingProvider;
import me.lucko.spark.paper.PaperTickHook;
import me.lucko.spark.paper.PaperTickReporter;
import me.lucko.spark.paper.PaperWorldInfoProvider;
import me.lucko.spark.paper.api.Compatibility;
import me.lucko.spark.paper.api.PaperClassLookup;
import me.lucko.spark.paper.api.PaperScheduler;
import me.lucko.spark.paper.api.PaperSparkModule;
import me.lucko.spark.paper.common.SparkPlatform;
import me.lucko.spark.paper.common.SparkPlugin;
import me.lucko.spark.paper.common.monitor.ping.PlayerPingProvider;
import me.lucko.spark.paper.common.platform.PlatformInfo;
import me.lucko.spark.paper.common.platform.serverconfig.ServerConfigProvider;
import me.lucko.spark.paper.common.platform.world.WorldInfoProvider;
import me.lucko.spark.paper.common.sampler.ThreadDumper;
import me.lucko.spark.paper.common.sampler.source.ClassSourceLookup;
import me.lucko.spark.paper.common.sampler.source.SourceMetadata;
import me.lucko.spark.paper.common.tick.TickHook;
import me.lucko.spark.paper.common.tick.TickReporter;
import me.lucko.spark.paper.common.util.classfinder.ClassFinder;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class CanvasSparkPlugin implements PaperSparkModule, SparkPlugin {
    private final Server server;
    private final Logger logger;
    private final PaperScheduler scheduler;
    private final PaperClassLookup classLookup;
    private final PaperTickHook tickHook;
    private final PaperTickReporter tickReporter;
    private final ThreadDumper gameThreadDumper;
    private final SparkPlatform platform;

    public CanvasSparkPlugin(Server server, Logger logger, PaperScheduler scheduler, PaperClassLookup classLookup) {
        this.server = server;
        this.logger = logger;
        this.scheduler = scheduler;
        this.classLookup = classLookup;
        this.tickHook = new PaperTickHook();
        this.tickReporter = new PaperTickReporter();
        this.gameThreadDumper = new LevelThreadDumper(Thread.currentThread());
        this.platform = new SparkPlatform(this);
    }

    public static @NotNull PaperSparkModule create(Compatibility compatibility, Server server, Logger logger, PaperScheduler scheduler, PaperClassLookup classLookup) {
        return new CanvasSparkPlugin(server, logger, scheduler, classLookup);
    }

    public void enable() {
        this.platform.enable();
    }

    public void disable() {
        this.platform.disable();
    }

    public void executeCommand(CommandSender sender, String[] args) {
        this.platform.executeCommand(new PaperCommandSender(sender), args);
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        return this.platform.tabCompleteCommand(new PaperCommandSender(sender), args);
    }

    public boolean hasPermission(CommandSender sender) {
        return this.platform.hasPermissionForAnyCommand(new PaperCommandSender(sender));
    }

    public Collection<String> getPermissions() {
        return this.platform.getAllSparkPermissions();
    }

    public void onServerTickStart() {
        this.tickHook.onTick();
    }

    public void onServerTickEnd(double duration) {
        this.tickReporter.onTick(duration);
    }

    public String getVersion() {
        return "1.10.119";
    }

    public Path getPluginDirectory() {
        return this.server.getPluginsFolder().toPath().resolve("spark");
    }

    public String getCommandName() {
        return "spark";
    }

    public Stream<PaperCommandSender> getCommandSenders() {
        return Stream.concat(this.server.getOnlinePlayers().stream(), Stream.of(this.server.getConsoleSender()))
                     .map((x$0) -> new PaperCommandSender(x$0));
    }

    public void executeAsync(Runnable task) {
        this.scheduler.executeAsync(task);
    }

    public void executeSync(Runnable task) {
        this.scheduler.executeSync(task);
    }

    public void log(Level level, String msg) {
        this.logger.log(level, msg);
    }

    public void log(Level level, String msg, Throwable throwable) {
        this.logger.log(level, msg, throwable);
    }

    public ThreadDumper getDefaultThreadDumper() {
        return this.gameThreadDumper;
    }

    public TickHook createTickHook() {
        return this.tickHook;
    }

    public TickReporter createTickReporter() {
        return this.tickReporter;
    }

    public ClassSourceLookup createClassSourceLookup() {
        return new PaperClassSourceLookup();
    }

    public ClassFinder createClassFinder() {
        return (className) -> {
            try {
                return this.classLookup.lookup(className);
            } catch (Exception var3) {
                return null;
            }
        };
    }

    public Collection<SourceMetadata> getKnownSources() {
        return SourceMetadata.gather(Arrays.asList(this.server.getPluginManager().getPlugins()), Plugin::getName, (plugin) -> plugin.getPluginMeta()
                                                                                                                                    .getVersion(), (plugin) -> String.join(", ", plugin.getPluginMeta()
                                                                                                                                                                                       .getAuthors()), (plugin) -> plugin.getPluginMeta()
                                                                                                                                                                                                                         .getDescription());
    }

    public PlayerPingProvider createPlayerPingProvider() {
        return new PaperPlayerPingProvider(this.server);
    }

    public ServerConfigProvider createServerConfigProvider() {
        return new CanvasServerConfigProvider();
    }

    public WorldInfoProvider createWorldInfoProvider() {
        return new PaperWorldInfoProvider(this.server);
    }

    public PlatformInfo getPlatformInfo() {
        return PaperPlatformInfo.INSTANCE;
    }

    public void registerApi(Spark api) {
    }
}
