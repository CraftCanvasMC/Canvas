/*
 * This file is part of Ignite, licensed under the MIT License (MIT).
 *
 * Copyright (c) vectrix.space <https://vectrix.space/>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package space.vectrix.ignite.applaunch;

import cpw.mods.modlauncher.Launcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.fusesource.jansi.AnsiConsole;
import space.vectrix.ignite.api.Blackboard;
import space.vectrix.ignite.api.service.IBootstrapService;
import space.vectrix.ignite.applaunch.agent.Agent;
import space.vectrix.ignite.applaunch.mod.ModEngine;
import space.vectrix.ignite.applaunch.service.BootstrapServiceHandler;
import space.vectrix.ignite.applaunch.service.DummyBootstrapService;
import space.vectrix.ignite.applaunch.util.IgniteConstants;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class IgniteBootstrap {

    // Canvas start
    public IgniteBootstrap(){
        IgniteBootstrap.instance = this;
        this.modEngine = new ModEngine();
    }

    private static void findMainInstance(){
        try {
            mainfile = new File(IgniteBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
    // Canvas end

  /**
   * The debug mode.
   */
  public static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(Blackboard.DEBUG.getName(), "true"));

  /**
   * The launch service name.
   */
  public static final @NonNull String LAUNCH_SERVICE = System.getProperty(Blackboard.LAUNCH_SERVICE.getName(), "paper"); // Canvas

  /**
   * The launch jar path.
   */
  public static final @NonNull Path LAUNCH_JAR = Paths.get(System.getProperty(Blackboard.LAUNCH_JAR.getName(), "./versions/1.20.2/canvas-1.20.2.jar")); // Canvas

  /**
   * The launch target class path.
   */
  public static final @NonNull String LAUNCH_TARGET = System.getProperty(Blackboard.LAUNCH_TARGET.getName(), "org.bukkit.craftbukkit.Main");

  /**
   * The mods directory.
   */
  public static final @NonNull Path MOD_TARGET_PATH = Paths.get(System.getProperty(Blackboard.MOD_DIRECTORY_PATH.getName(), "./plugins")); // Canvas

  /**
   * The configs directory.
   */
  public static final @NonNull Path CONFIG_TARGET_PATH = Paths.get(System.getProperty(Blackboard.CONFIG_DIRECTORY_PATH.getName(), "./configs"));

  /**
   * The libraries directory.
   */
  public static final @NonNull Path LIBRARIES_TARGET_PATH = Paths.get(System.getProperty(Blackboard.LIBRARIES_DIRECTORY_PATH.getName(), "./libraries"));

  static {
    AnsiConsole.systemInstall();
  }

  private static IgniteBootstrap instance;

  public static IgniteBootstrap getInstance() {
    return IgniteBootstrap.instance;
  }
 // Canvas start

    private static File mainfile;
    public static File getJarLocation(){
      return mainfile;
    }

 // Canvas end

  public static void main(final @NonNull String[] args) {
      // Canvas start
      findMainInstance();
      Path cdir = Paths.get(mainfile.getParent());

      try (Stream<Path> files = Files.list(cdir)) {
          Path jarFile = files
              .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".jar"))
              .findFirst()
              .orElse(null);

          if (jarFile != null) {
              JarFile jar = new JarFile(jarFile.toFile());

              Enumeration<JarEntry> entries = jar.entries();
              while (entries.hasMoreElements()) {
                  JarEntry entry = entries.nextElement();
                  if (entry.getName().startsWith("canvas-server") && entry.getName().endsWith(".jar")) {
                      Path pluginsDirectory = Paths.get(mainfile.getParent());

                      if (!Files.exists(pluginsDirectory)) {
                          Files.createDirectories(pluginsDirectory);
                      }

                      Path destinationPath = pluginsDirectory.resolve("canvas-serverData.jar");
                      Files.copy(jar.getInputStream(entry), destinationPath, StandardCopyOption.REPLACE_EXISTING);

                      Logger logger = LogManager.getLogger("Ignite/Canvas Bootstrap");
                      logger.info("ServerData file loaded successfully");
                      break;
                  }
              }

              jar.close();
          } else {
              System.err.println("No JAR file found.");
          }
      } catch (IOException e) {
          e.printStackTrace();
      }
        (new IgniteBootstrap()).run(args);
      // Canvas end
  }

  private final ModEngine modEngine;

  /**
   * The main launch target to boostrap from.
   *
   * @param args The launch arguments
   */
  public void run(final @NonNull String[] args) {
    final List<String> arguments = Arrays.asList(args);
    final List<String> launchArguments = new ArrayList<>(arguments);

    // Blackboard
    Blackboard.computeProperty(Blackboard.LAUNCH_ARGUMENTS, Collections.unmodifiableList(arguments));
    Blackboard.computeProperty(Blackboard.DEBUG, IgniteBootstrap.DEBUG);
    Blackboard.computeProperty(Blackboard.LAUNCH_SERVICE, IgniteBootstrap.LAUNCH_SERVICE);
    Blackboard.computeProperty(Blackboard.LAUNCH_JAR, IgniteBootstrap.LAUNCH_JAR);
    Blackboard.computeProperty(Blackboard.LAUNCH_TARGET, IgniteBootstrap.LAUNCH_TARGET);
    Blackboard.computeProperty(Blackboard.MOD_DIRECTORY_PATH, IgniteBootstrap.MOD_TARGET_PATH);
    Blackboard.computeProperty(Blackboard.CONFIG_DIRECTORY_PATH, IgniteBootstrap.CONFIG_TARGET_PATH);
    Blackboard.computeProperty(Blackboard.LIBRARIES_DIRECTORY_PATH, IgniteBootstrap.LIBRARIES_TARGET_PATH);

    // Launch Target
    launchArguments.add("--launchTarget");
    launchArguments.add(IgniteConstants.IGNITE_LAUNCH_SERVICE);

    // Configurations
    IgniteConfigurations.configure();

    // Bootstrap Launch Service
    final BootstrapServiceHandler bootstrapServiceHandler = new BootstrapServiceHandler();
    final IBootstrapService bootstrapService = bootstrapServiceHandler.findService(Blackboard.getProperty(Blackboard.LAUNCH_SERVICE)).orElseGet(DummyBootstrapService::new);
    try {
      if (!bootstrapService.validate()) throw new IllegalStateException("Service failed to validate environment!");
      bootstrapService.execute();
    } catch (final Throwable throwable) {
      throw new RuntimeException("Encountered an exception running the bootstrap service!", throwable);
    }

    // Target Check
    if (!Files.exists(Blackboard.getProperty(Blackboard.LAUNCH_JAR))) {
      throw new IllegalStateException("Unable to locate launch jar at '" + Blackboard.getProperty(Blackboard.LAUNCH_JAR) + "'.");
    }

    // Load the server jar on the provided ClassLoader via the Agent.
    try {
      Agent.addJar(Blackboard.getProperty(Blackboard.LAUNCH_JAR));
    } catch (final IOException exception) {
      throw new IllegalStateException("Unable to add launch jar to classpath!", exception);
    }

    // Load the library jars on the provided ClassLoader via the Agent.
    if(Files.exists(Blackboard.getProperty(Blackboard.LIBRARIES_DIRECTORY_PATH))) {
      try(final Stream<Path> stream = Files.walk(Blackboard.getProperty(Blackboard.LIBRARIES_DIRECTORY_PATH))) {
        stream.forEach(path -> {
          if(!path.toString().endsWith(".jar")) return;

          try {
            Agent.addJar(path);
          } catch (final IOException exception) {
            throw new IllegalStateException("Unable to add library jar at '" + path + "' to classpath!", exception);
          }
        });
      } catch (final IOException exception) {
        throw new IllegalStateException("Unable to list library jars at '" + Blackboard.getProperty(Blackboard.LIBRARIES_DIRECTORY_PATH) + "'!", exception);
      }
    }

    // Update Security - Java 9+
    Agent.updateSecurity();

    // Logger Debug
    if(!Blackboard.getProperty(Blackboard.DEBUG)) {
      final LoggerContext context = (LoggerContext) LogManager.getContext(false);
      final Configuration config = context.getConfiguration();

      config.getRootLogger().removeAppender("DebugFile");
      context.updateLoggers();
    }

    // Logger
    final Logger logger = LogManager.getLogger("Ignite Bootstrap");
    logger.info("Ignite Launcher v" + IgniteBootstrap.class.getPackage().getImplementationVersion());

    // Modlauncher
    logger.info("Preparing ModLauncher with arguments " + launchArguments);
    Launcher.main(launchArguments.toArray(new String[0]));
  }

  public final @NonNull ModEngine getModEngine() {
    return this.modEngine;
  }
}
