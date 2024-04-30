package space.vectrix.ignite.game;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;
import space.vectrix.ignite.Blackboard;
import space.vectrix.ignite.IgniteBootstrap;
import space.vectrix.ignite.agent.IgniteAgent;
import space.vectrix.ignite.agent.transformer.SpigotTransformer;
import space.vectrix.ignite.util.BlackboardMap;

/**
 * Provides a game locator for Spigot.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class SpigotGameLocator implements GameLocatorService {
  private static final BlackboardMap.@NotNull Key<Path> SPIGOT_BUNDLER = Blackboard.key("ignite.spigot.bundler", Path.class, Paths.get("./bundler"));
  private static final BlackboardMap.@NotNull Key<Path> SPIGOT_JAR = Blackboard.key("ignite.spigot.jar", Path.class, Paths.get("./spigot.jar"));
  private static final BlackboardMap.@NotNull Key<String> SPIGOT_TARGET = Blackboard.key("ignite.spigot.target", String.class, "org.bukkit.craftbukkit.bootstrap.Main");
  private static final BlackboardMap.@NotNull Key<String> SPIGOT_VERSION = Blackboard.key("ignite.spigot.version", String.class, "1.20.5-R0.1-SNAPSHOT");

  private static final String SPIGOT_VERSION_PATTERN = "META-INF/versions/[^/].[^/]+\\.jar";

  private SpigotGameProvider provider;

  @Override
  public @NotNull String id() {
    return "spigot";
  }

  @Override
  public @NotNull String name() {
    return "Spigot";
  }

  @Override
  public int priority() {
    return 50;
  }

  @Override
  public boolean shouldApply() {
    /*final Path path = Blackboard.raw(SpigotGameLocator.SPIGOT_JAR); // Canvas
    try(final JarFile jarFile = new JarFile(path.toFile())) {
      final Enumeration<JarEntry> entries = jarFile.entries();
      while(entries.hasMoreElements()) {
        final JarEntry entry = entries.nextElement();
        Logger.trace("Found entry: {}", entry.getName());
        if(entry.getName().matches(SpigotGameLocator.SPIGOT_VERSION_PATTERN)) {
          return true;
        }
      }

      return false;
    } catch(final IOException exception) {
      return false;
    }*/ // Canvas
    return false; // Canvas
  }

  @Override
  public void apply(final @NotNull IgniteBootstrap bootstrap) throws Throwable {
    // Populate the blackboard.
    Blackboard.compute(SpigotGameLocator.SPIGOT_BUNDLER, () -> Paths.get(System.getProperty(SpigotGameLocator.SPIGOT_BUNDLER.name())));
    Blackboard.compute(SpigotGameLocator.SPIGOT_JAR, () -> Paths.get(System.getProperty(SpigotGameLocator.SPIGOT_JAR.name())));
    Blackboard.compute(SpigotGameLocator.SPIGOT_TARGET, () -> System.getProperty(SpigotGameLocator.SPIGOT_TARGET.name()));
    Blackboard.compute(SpigotGameLocator.SPIGOT_VERSION, () -> System.getProperty(SpigotGameLocator.SPIGOT_VERSION.name()));

    // Add the transformer to replace the system exits.
    IgniteAgent.addTransformer(new SpigotTransformer(Blackboard.raw(SpigotGameLocator.SPIGOT_TARGET).replace('.', '/')));

    // Clear the bundler main class, we launch the game ourselves.
    System.setProperty("bundlerMainClass", "");

    // Add the spigot jar.
    try {
      IgniteAgent.addJar(Blackboard.raw(SpigotGameLocator.SPIGOT_JAR));
    } catch(final IOException exception) {
      throw new IllegalStateException("Unable to add spigot jar to classpath!", exception);
    }

    // Run spigot.
    try {
      final Class<?> spigotClass = Class.forName(Blackboard.raw(SpigotGameLocator.SPIGOT_TARGET));
      spigotClass
        .getMethod("main", String[].class)
        .invoke(null, (Object) new String[0]);
    } catch(final ClassNotFoundException exception) {
      throw new IllegalStateException("Unable to execute spigot jar!", exception);
    }

    // Create the game provider.
    if(this.provider == null) {
      this.provider = this.createProvider();
    }

    // Locate the game jar.
    if(!Blackboard.get(Blackboard.GAME_JAR).isPresent()) {
      Blackboard.put(Blackboard.GAME_JAR, this.provider.gamePath());
    }

    // Remove the bundler main class flag.
    System.getProperties().remove("bundlerMainClass");
  }

  @Override
  public @NotNull GameProvider locate() {
    return this.provider;
  }

  private SpigotGameProvider createProvider() throws Throwable {
    // Extract game information from spigot.
    final List<String> libraries = new ArrayList<>();
    String game = null;

    final Path path = Blackboard.raw(SpigotGameLocator.SPIGOT_JAR);
    final File file = path.toFile();
    if(!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
    if(file.isDirectory() || !file.getName().endsWith(".jar")) throw new IOException("Provided path is not a jar file: " + path);

    try(final JarFile jarFile = new JarFile(file)) {
      // Determine where the game jar is located.
      {
        if(!Blackboard.get(SpigotGameLocator.SPIGOT_VERSION).isPresent()) {
          // Read the version.list for the game to launch.
          final JarEntry entry = jarFile.getJarEntry("META-INF/versions.list");
          if(entry != null) {
            try(final InputStream inputStream = jarFile.getInputStream(entry); final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
              String line;
              while((line = reader.readLine()) != null) {
                final String[] values = line.split(" \\*");

                if(values.length >= 2) {
                  game = String.format("./versions/%s", values[1]);
                  Logger.trace("Located spigot jar from versions.list: {}", game);
                  break;
                }
              }
            }
          }
        }

        if(game == null) {
          final String version = Blackboard.raw(SpigotGameLocator.SPIGOT_VERSION);
          game = String.format("./versions/spigot-%s.jar", version);
          Logger.trace("Located paper jar from command argument: {}", game);
        }
      }

      // Read the libraries the game should launch with.
      {
        final JarEntry entry = jarFile.getJarEntry("META-INF/libraries.list");
        if(entry != null) {
          try(final InputStream inputStream = jarFile.getInputStream(entry); final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while((line = reader.readLine()) != null) {
              final String[] values = line.split(" \\*");

              if(values.length >= 2) {
                final String value = values[1];

                // Spigot doesn't actually store the minecraft server in the
                // library directory. So we need to skip it.
                if(value.startsWith("minecraft-server-")) continue;

                libraries.add(values[1]);
              }
            }
          }
        }
      }
    }

    return new SpigotGameProvider(libraries, game);
  }

  /* package */ static final class SpigotGameProvider implements GameProvider {
    private final List<String> libraries;
    private final String game;

    /* package */ SpigotGameProvider(final @NotNull List<String> libraries, final @NotNull String game) {
      this.libraries = libraries;
      this.game = game;
    }

    @Override
    public @NotNull Stream<Path> gameLibraries() {
      final Path libraryPath = Blackboard.raw(SpigotGameLocator.SPIGOT_BUNDLER)
        .resolve(Blackboard.raw(Blackboard.GAME_LIBRARIES).getFileName());

      return this.libraries.stream().map(libraryPath::resolve);
    }

    @Override
    public @NotNull Path gamePath() {
      final Path bundlePath = Blackboard.raw(SpigotGameLocator.SPIGOT_BUNDLER);
      return bundlePath.resolve(this.game);
    }
  }
}
