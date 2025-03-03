package io.canvasmc.canvas;

import com.mojang.logging.LogUtils;
import io.papermc.paper.PaperBootstrap;
import io.papermc.paper.ServerBuildInfo;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.util.PathConverter;
import net.minecraft.SharedConstants;
import net.minecraft.server.Eula;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.Main;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CanvasBootstrap {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    public static OptionSet bootstrap(String[] args) {
        System.setProperty("java.util.logging.manager", "io.papermc.paper.log.CustomLogManager");
        final String warnWhenLegacyFormattingDetected = String.join(".", "net", "kyori", "adventure", "text", "warnWhenLegacyFormattingDetected");
        if (false && System.getProperty(warnWhenLegacyFormattingDetected) == null) {
            System.setProperty(warnWhenLegacyFormattingDetected, String.valueOf(true));
        }
        if (System.getProperty("jdk.nio.maxCachedBufferSize") == null) System.setProperty("jdk.nio.maxCachedBufferSize", "262144"); // Paper - cap per-thread NIO cache size; https://www.evanjones.ca/java-bytebuffer-leak.html
        OptionParser parser = new OptionParser() {
            {
                this.acceptsAll(asList("?", "help"), "Show the help");
                this.acceptsAll(asList("c", "config"), "Properties file to use")
                    .withRequiredArg()
                    .ofType(File.class)
                    .defaultsTo(new File("server.properties"))
                    .describedAs("Properties file");
                this.acceptsAll(asList("P", "plugins"), "Plugin directory to use")
                    .withRequiredArg()
                    .ofType(File.class)
                    .defaultsTo(new File("plugins"))
                    .describedAs("Plugin directory");
                this.acceptsAll(asList("h", "host", "server-ip"), "Host to listen on")
                    .withRequiredArg()
                    .ofType(String.class)
                    .describedAs("Hostname or IP");
                this.acceptsAll(asList("W", "world-dir", "universe", "world-container"), "World container")
                    .withRequiredArg()
                    .ofType(File.class)
                    .defaultsTo(new File("."))
                    .describedAs("Directory containing worlds");
                this.acceptsAll(asList("w", "world", "level-name"), "World name")
                    .withRequiredArg()
                    .ofType(String.class)
                    .describedAs("World name");
                this.acceptsAll(asList("p", "port", "server-port"), "Port to listen on")
                    .withRequiredArg()
                    .ofType(Integer.class)
                    .describedAs("Port");
                this.accepts("serverId", "Server ID")
                    .withRequiredArg();
                this.accepts("jfrProfile", "Enable JFR profiling");
                this.accepts("pidFile", "pid File")
                    .withRequiredArg()
                    .withValuesConvertedBy(new PathConverter());
                this.acceptsAll(asList("o", "online-mode"), "Whether to use online authentication")
                    .withRequiredArg()
                    .ofType(Boolean.class)
                    .describedAs("Authentication");
                this.acceptsAll(asList("s", "size", "max-players"), "Maximum amount of players")
                    .withRequiredArg()
                    .ofType(Integer.class)
                    .describedAs("Server size");
                this.acceptsAll(asList("d", "date-format"), "Format of the date to display in the console (for log entries)")
                    .withRequiredArg()
                    .ofType(SimpleDateFormat.class)
                    .describedAs("Log date format");
                this.acceptsAll(asList("log-pattern"), "Specfies the log filename pattern")
                    .withRequiredArg()
                    .ofType(String.class)
                    .defaultsTo("server.log")
                    .describedAs("Log filename");
                this.acceptsAll(asList("log-limit"), "Limits the maximum size of the log file (0 = unlimited)")
                    .withRequiredArg()
                    .ofType(Integer.class)
                    .defaultsTo(0)
                    .describedAs("Max log size");
                this.acceptsAll(asList("log-count"), "Specified how many log files to cycle through")
                    .withRequiredArg()
                    .ofType(Integer.class)
                    .defaultsTo(1)
                    .describedAs("Log count");
                this.acceptsAll(asList("log-append"), "Whether to append to the log file")
                    .withRequiredArg()
                    .ofType(Boolean.class)
                    .defaultsTo(true)
                    .describedAs("Log append");
                this.acceptsAll(asList("log-strip-color"), "Strips color codes from log file");
                this.acceptsAll(asList("b", "bukkit-settings"), "File for bukkit settings")
                    .withRequiredArg()
                    .ofType(File.class)
                    .defaultsTo(new File("bukkit.yml"))
                    .describedAs("Yml file");
                this.acceptsAll(asList("C", "commands-settings"), "File for command settings")
                    .withRequiredArg()
                    .ofType(File.class)
                    .defaultsTo(new File("commands.yml"))
                    .describedAs("Yml file");
                this.acceptsAll(asList("forceUpgrade"), "Whether to force a world upgrade");
                this.acceptsAll(asList("eraseCache"), "Whether to force cache erase during world upgrade");
                this.acceptsAll(asList("recreateRegionFiles"), "Whether to recreate region files during world upgrade");
                this.accepts("safeMode", "Loads level with vanilla datapack only"); // Paper
                this.acceptsAll(asList("nogui"), "Disables the graphical console");
                this.acceptsAll(asList("nojline"), "Disables jline and emulates the vanilla console");
                this.acceptsAll(asList("noconsole"), "Disables the console");
                this.acceptsAll(asList("v", "version"), "Show the CraftBukkit Version");
                this.acceptsAll(asList("demo"), "Demo mode");
                this.acceptsAll(asList("initSettings"), "Only create configuration files and then exit"); // SPIGOT-5761: Add initSettings option
                this.acceptsAll(asList("S", "spigot-settings"), "File for spigot settings")
                    .withRequiredArg()
                    .ofType(File.class)
                    .defaultsTo(new File("spigot.yml"))
                    .describedAs("Yml file");
                acceptsAll(asList("paper-dir", "paper-settings-directory"), "Directory for Paper settings")
                    .withRequiredArg()
                    .ofType(File.class)
                    .defaultsTo(new File(io.papermc.paper.configuration.PaperConfigurations.CONFIG_DIR))
                    .describedAs("Config directory");
                acceptsAll(asList("paper", "paper-settings"), "File for Paper settings")
                    .withRequiredArg()
                    .ofType(File.class)
                    .defaultsTo(new File("paper.yml"))
                    .describedAs("Yml file");
                acceptsAll(asList("add-plugin", "add-extra-plugin-jar"), "Specify paths to extra plugin jars to be loaded in addition to those in the plugins folder. This argument can be specified multiple times, once for each extra plugin jar path.")
                    .withRequiredArg()
                    .ofType(File.class)
                    .defaultsTo(new File[] {})
                    .describedAs("Jar file");
                acceptsAll(asList("purpur", "purpur-settings"), "File for purpur settings")
                    .withRequiredArg()
                    .ofType(File.class)
                    .defaultsTo(new File("purpur.yml"))
                    .describedAs("Yml file");
                acceptsAll(asList("server-name"), "Name of the server")
                    .withRequiredArg()
                    .ofType(String.class)
                    .defaultsTo("Unknown Server")
                    .describedAs("Name");
            }
        };

        OptionSet options = null;

        try {
            options = parser.parse(args);
        } catch (joptsimple.OptionException ex) {
            Logger.getLogger(CanvasBootstrap.class.getName()).log(Level.SEVERE, ex.getLocalizedMessage());
        }

        if ((options == null) || (options.has("?"))) {
            try {
                parser.printHelpOn(System.out);
            } catch (IOException ex) {
                Logger.getLogger(CanvasBootstrap.class.getName()).log(Level.SEVERE, null, ex);
            }
        } else if (options.has("v")) {
            System.out.println(CraftServer.class.getPackage().getImplementationVersion());
        } else {
            String path = new File(".").getAbsolutePath();
            if (path.contains("!") || path.contains("+")) {
                System.err.println("Cannot run server in a directory with ! or + in the pathname. Please rename the affected folders and try again.");
                System.exit(70);
            }

            boolean skip = Boolean.getBoolean("Paper.IgnoreJavaVersion");
            String javaVersionName = System.getProperty("java.version");
            boolean isPreRelease = javaVersionName.contains("-");
            if (isPreRelease) {
                if (!skip) {
                    System.err.println("Unsupported Java detected (" + javaVersionName + "). You are running an unsupported, non official, version. Only general availability versions of Java are supported. Please update your Java version. See https://docs.papermc.io/paper/faq#unsupported-java-detected-what-do-i-do for more information.");
                    System.exit(70);
                }

                System.err.println("Unsupported Java detected ("+ javaVersionName + "), but the check was skipped. Proceed with caution! ");
            }

            try {
                if (options.has("nojline")) {
                    System.setProperty(net.minecrell.terminalconsole.TerminalConsoleAppender.JLINE_OVERRIDE_PROPERTY, "false");
                    Main.useJline = false;
                }

                if (options.has("noconsole")) {
                    Main.useConsole = false;
                    Main.useJline = false;
                    System.setProperty(net.minecrell.terminalconsole.TerminalConsoleAppender.JLINE_OVERRIDE_PROPERTY, "false"); // Paper
                }


                System.setProperty("library.jansi.version", "Paper");
                System.setProperty("jdk.console", "java.base");

                SharedConstants.tryDetectVersion();
                Path path2 = Paths.get("eula.txt");
                Eula eula = new Eula(path2);
                boolean eulaAgreed = Boolean.getBoolean("com.mojang.eula.agree");
                if (eulaAgreed) {
                    LOGGER.error("You have used the Spigot command line EULA agreement flag.");
                    LOGGER.error("By using this setting you are indicating your agreement to Mojang's EULA (https://aka.ms/MinecraftEULA).");
                    LOGGER.error("If you do not agree to the above EULA please stop your server and remove this flag immediately.");
                }
                if (!eula.hasAgreedToEULA() && !eulaAgreed) {
                    LOGGER.info("You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
                    System.exit(0);
                }

                getStartupVersionMessages().forEach(LOGGER::info);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        return options;
    }

    private static List<String> asList(String... params) {
        return Arrays.asList(params);
    }

    private static List<String> getStartupVersionMessages() {
        final String javaSpecVersion = System.getProperty("java.specification.version");
        final String javaVmName = System.getProperty("java.vm.name");
        final String javaVmVersion = System.getProperty("java.vm.version");
        final String javaVendor = System.getProperty("java.vendor");
        final String javaVendorVersion = System.getProperty("java.vendor.version");
        final String osName = System.getProperty("os.name");
        final String osVersion = System.getProperty("os.version");
        final String osArch = System.getProperty("os.arch");

        final ServerBuildInfo bi = ServerBuildInfo.buildInfo();
        return List.of(
            String.format(
                "Running Java %s (%s %s; %s %s) on %s %s (%s)",
                javaSpecVersion,
                javaVmName,
                javaVmVersion,
                javaVendor,
                javaVendorVersion,
                osName,
                osVersion,
                osArch
            ),
            String.format(
                "Loading %s %s for Minecraft %s",
                bi.brandName(),
                bi.asString(ServerBuildInfo.StringRepresentation.VERSION_FULL),
                bi.minecraftVersionId()
            ),
            String.format(
                "Running JVM args %s",
                ManagementFactory.getRuntimeMXBean().getInputArguments().toString()
            )
        );
    }
}
