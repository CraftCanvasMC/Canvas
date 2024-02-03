package space.vectrix.ignite;

import java.io.IOException;
import java.util.*; // Canvas
import java.util.stream.*; // Canvas
import java.util.jar.*; // Canvas
import java.nio.file.*; // Canvas
import java.io.*;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.util.JavaVersion;
import org.spongepowered.asm.util.asm.ASM;
import org.tinylog.Logger;
import space.vectrix.ignite.agent.IgniteAgent;
import space.vectrix.ignite.game.GameLocatorService;
import space.vectrix.ignite.game.GameProvider;
import space.vectrix.ignite.launch.ember.Ember;
import space.vectrix.ignite.mod.ModsImpl;
import space.vectrix.ignite.util.IgniteCollections;
import space.vectrix.ignite.util.IgniteConstants;

/**
 * Represents the main class which starts Ignite.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class IgniteBootstrap {
  private static IgniteBootstrap INSTANCE;

  /**
   * Returns the bootstrap instance.
   *
   * @return this instance
   * @since 1.0.0
   */
  public static @NotNull IgniteBootstrap instance() {
    return IgniteBootstrap.INSTANCE;
  }
  // Canvas start
  private static File mainfile = null;
    private static void findMainInstance(){
        try {
            mainfile = new File(IgniteBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
  // Canvas end

  /**
   * The main entrypoint to start Ignite.
   *
   * @param arguments the launch arguments
   * @since 1.0.0
   */
  public static void main(final String@NotNull [] arguments) {
    new IgniteBootstrap().run(arguments);
  }

  private final ModsImpl engine;

  public IgniteBootstrap() {
    // Canvas start
      String javaVersion = System.getProperty("java.version");
        Logger.info("Loading Java Version: " + javaVersion);

        int majorVersion = Integer.parseInt(javaVersion.split("\\.")[0]);

        if (!(majorVersion >= 17)) {
          Logger.info("Java version is below 17, please upgrade your Java version.");
          System.exit(1);
        }
      findMainInstance();
      Path cdir = Paths.get(mainfile.getParent());

      try (Stream<Path> files = Files.list(cdir)) {
          if (mainfile != null) {
              JarFile jar = new JarFile(mainfile);

              Enumeration<JarEntry> entries = jar.entries();
              while (entries.hasMoreElements()) {
                  JarEntry entry = entries.nextElement();
                  if (entry.getName().endsWith("canvas-1.20.4-R0.1-SNAPSHOT.zip")) {
                      Path pluginsDirectory = Paths.get(mainfile.getParent() + "/.launcher/");

                      if (!Files.exists(pluginsDirectory)) {
                          Files.createDirectories(pluginsDirectory);
                      }

                      Path destinationPath = pluginsDirectory.resolve("canvas-1.20.4-R0.1-SNAPSHOT.jar");
                      Files.copy(jar.getInputStream(entry), destinationPath, StandardCopyOption.REPLACE_EXISTING);
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
    // Canvas end
    IgniteBootstrap.INSTANCE = this;
    this.engine = new ModsImpl();
  }

  private void run(final String@NotNull [] args) {
    final List<String> arguments = Arrays.asList(args);
    final List<String> launchArguments = new ArrayList<>(arguments);

    // Print the runtime information for this launch.
    Logger.info(
      "Running {} v{} (API: {}, ASM: {}, Java: {})",
      IgniteConstants.API_TITLE,
      IgniteConstants.IMPLEMENTATION_VERSION,
      IgniteConstants.API_VERSION,
      ASM.getVersionString(),
      JavaVersion.current()
    );

    // Initialize the blackboard and populate it with the startup
    // flags.
    // Canvas start
    // Blackboard.compute(Blackboard.DEBUG, () -> Boolean.parseBoolean(System.getProperty(Blackboard.DEBUG.name())));
    // Blackboard.compute(Blackboard.GAME_LOCATOR, () -> System.getProperty(Blackboard.GAME_LOCATOR.name()));
    // Blackboard.compute(Blackboard.GAME_JAR, () -> Paths.get(System.getProperty(Blackboard.GAME_JAR.name())));
    // Blackboard.compute(Blackboard.GAME_TARGET, () -> System.getProperty(Blackboard.GAME_TARGET.name()));
    // Blackboard.compute(Blackboard.GAME_LIBRARIES, () -> Paths.get(System.getProperty(Blackboard.GAME_LIBRARIES.name())));
    // Blackboard.compute(Blackboard.MODS_DIRECTORY, () -> Paths.get(System.getProperty(Blackboard.MODS_DIRECTORY.name())));
    // Canvas end

    // Get a suitable game locator and game provider.
    final GameLocatorService gameLocator;
    final GameProvider gameProvider;
    {
      final Optional<String> requiredGameLocator = Optional.of("canvas"); // Canvas
      final ServiceLoader<GameLocatorService> gameLocatorLoader = ServiceLoader.load(GameLocatorService.class);
      final Optional<GameLocatorService> gameLocatorProvider = requiredGameLocator.map(locatorIdentifier -> IgniteCollections.stream(gameLocatorLoader)
        .filter(locator -> locator.id().equalsIgnoreCase(locatorIdentifier))
        .findFirst()).orElseGet(() -> IgniteCollections.stream(gameLocatorLoader)
        .sorted(Comparator.comparingInt(GameLocatorService::priority))
        .filter(GameLocatorService::shouldApply)
        .findFirst()
      );

      if(!gameLocatorProvider.isPresent()) {
        Logger.error("Failed to start game: Unable to find a suitable GameLocator service.");
        System.exit(1);
        return;
      }

      gameLocator = gameLocatorProvider.get();

      Logger.info("Detected game locator: {}", gameLocator.name());

      try {
        gameLocator.apply(this);
      } catch(final Throwable throwable) {
        Logger.error(throwable, "Failed to start game: Unable to apply GameLocator service.");
        System.exit(1);
        return;
      }

      gameProvider = gameLocator.locate();
    }

    Logger.info("Preparing the game...");

    // Add the game.
    final Path gameJar = Blackboard.raw(Blackboard.GAME_JAR);
    try {
      IgniteAgent.addJar(gameJar);

      Logger.trace("Added game jar: {}", gameJar);
    } catch(final IOException exception) {
      Logger.error(exception, "Failed to resolve game jar: {}", gameJar);
      System.exit(1);
      return;
    }

    // Add the game libraries.
    gameProvider.gameLibraries().forEach(path -> {
      if(!path.toString().endsWith(".jar")) return;

      try {
        IgniteAgent.addJar(path);

        Logger.trace("Added game library jar: {}", path);
      } catch(final IOException exception) {
        Logger.error(exception, "Failed to resolve game library jar: {}", path);
      }
    });

    Logger.info("Launching the game...");

    // Initialize the API.
    Ignite.initialize(new PlatformImpl());

    // Launch the game.
    Ember.launch(launchArguments.toArray(new String[0]));
  }

  /**
   * Returns the mod engine.
   *
   * @return the mod engine
   * @since 1.0.0
   */
  public @NotNull ModsImpl engine() {
    return this.engine;
  }
}
