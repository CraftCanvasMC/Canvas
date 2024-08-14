package space.vectrix.ignite.game;

import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;
import space.vectrix.ignite.Blackboard;
import space.vectrix.ignite.IgniteBootstrap;
import space.vectrix.ignite.agent.IgniteAgent;
import space.vectrix.ignite.agent.transformer.PaperclipTransformer;
import space.vectrix.ignite.util.BlackboardMap;
import space.vectrix.ignite.util.IgniteConstants;

/**
 * Provides a game locator for Paper.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class PaperGameLocator implements GameLocatorService {
  private static final BlackboardMap.@NotNull Key<Path> PAPER_JAR = Blackboard.key("ignite.paper.jar", Path.class, Paths.get("./.launcher/canvas-1.21.1-R0.1-SNAPSHOT.jar")); // Canvas
  private static final BlackboardMap.@NotNull Key<String> PAPER_TARGET = Blackboard.key("ignite.paper.target", String.class, "io.papermc.paperclip.Paperclip");
  private static final BlackboardMap.@NotNull Key<String> PAPER_VERSION = Blackboard.key("ignite.paper.version", String.class, "1.21.1");

  private PaperGameProvider provider;

  @Override
  public @NotNull String id() {
    return "canvas";
  }

  @Override
  public @NotNull String name() {
    return "Canvas"; // Canvas
  }

  @Override
  public int priority() {
    return 50;
  }

  @Override
  public boolean shouldApply() {
    final Path path = Blackboard.raw(PaperGameLocator.PAPER_JAR);
    try(final JarFile jarFile = new JarFile(path.toFile())) {
      return jarFile.getJarEntry("version.json") != null;
    } catch(final IOException exception) {
      return false;
    }
  }

  @Override
  public void apply(final @NotNull IgniteBootstrap bootstrap) throws Throwable {
    // Populate the blackboard.
    Blackboard.compute(PaperGameLocator.PAPER_JAR, () -> Paths.get(System.getProperty(PaperGameLocator.PAPER_JAR.name())));
    Blackboard.compute(PaperGameLocator.PAPER_TARGET, () -> System.getProperty(PaperGameLocator.PAPER_TARGET.name()));
    Blackboard.compute(PaperGameLocator.PAPER_VERSION, () -> System.getProperty(PaperGameLocator.PAPER_VERSION.name()));

    // Add the transformer to replace the system exits.
    IgniteAgent.addTransformer(new PaperclipTransformer(Blackboard.raw(PaperGameLocator.PAPER_TARGET).replace('.', '/')));

    // Set paperclip to patch only, we launch the game ourselves.
    System.setProperty("paperclip.patchonly", "true");

    // Add the paperclip jar.
    try {
      IgniteAgent.addJar(Blackboard.raw(PaperGameLocator.PAPER_JAR));
    } catch(final IOException exception) {
      throw new IllegalStateException("Unable to add paperclip jar to classpath!", exception);
    }

    // Run paperclip.
    try {
      final Class<?> paperclipClass = Class.forName(Blackboard.raw(PaperGameLocator.PAPER_TARGET));
      paperclipClass
        .getMethod("main", String[].class)
        .invoke(null, (Object) new String[0]);
    } catch(final ClassNotFoundException exception) {
      throw new IllegalStateException("Unable to execute paperclip jar!", exception);
    }

    // Create the game provider.
    if(this.provider == null) {
      this.provider = this.createProvider();
    }

    // Locate the game jar.
    if(!Blackboard.get(Blackboard.GAME_JAR).isPresent()) {
      Blackboard.put(Blackboard.GAME_JAR, this.provider.gamePath());
    }

    // Remove the patchonly flag.
    System.getProperties().remove("paperclip.patchonly");
  }

  @Override
  public @NotNull GameProvider locate() {
    return this.provider;
  }

  private PaperGameProvider createProvider() throws Throwable {
    // Extract game information from paperclip.
    final List<String> libraries = new ArrayList<>();
    String game = null;

    final Path path = Blackboard.raw(PaperGameLocator.PAPER_JAR);
    final File file = path.toFile();
    if(!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
    if(file.isDirectory() || !file.getName().endsWith(".jar")) throw new IOException("Provided path is not a jar file: " + path);

    try(final JarFile jarFile = new JarFile(file)) {
      // Determine where the game jar is located.
      {
        if(!Blackboard.get(PaperGameLocator.PAPER_VERSION).isPresent()) {
          // Read the version.list for the game to launch.
          JarEntry entry = jarFile.getJarEntry("META-INF/versions.list");
          if(entry != null) {
            try(final InputStream inputStream = jarFile.getInputStream(entry); final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
              String line;
              while((line = reader.readLine()) != null) {
                final String[] values = line.split("\t");

                if(values.length >= 3) {
                  game = String.format("./versions/%s", values[2]);
                  Logger.trace("Located paper jar from versions.list: {}", game);
                  break;
                }
              }
            }
          }

          // Read the version.json if the version.list is not specifically set.
          entry = jarFile.getJarEntry("version.json");
          if(game == null && entry != null) {
            final InputStream inputStream = jarFile.getInputStream(entry);
            final JsonObject versionObject = IgniteConstants.GSON.fromJson(new InputStreamReader(inputStream), JsonObject.class);

            final String version = versionObject.getAsJsonPrimitive("id").getAsString();
            game = String.format("./versions/%s/paper-%s.jar", version, version);
            Logger.trace("Located paper jar from version.json: {}", game);
          }
        }

        if(game == null) {
          final String version = Blackboard.raw(PaperGameLocator.PAPER_VERSION);
          game = String.format("./versions/%s/paper-%s.jar", version, version);
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
              final String[] values = line.split("\t");

              if(values.length >= 3) {
                libraries.add(values[2]);
              }
            }
          }
        }
      }
    }

    return new PaperGameProvider(game, libraries);
  }

  /* package */ static final class PaperGameProvider implements GameProvider {
    private final List<String> libraries;
    private final String game;

    /* package */ PaperGameProvider(final @NotNull String game, final @NotNull List<String> libraries) {
      this.game = game;
      this.libraries = libraries;
    }

    @Override
    public @NotNull Stream<Path> gameLibraries() {
      final Path libraryPath = Blackboard.raw(Blackboard.GAME_LIBRARIES);
      return this.libraries.stream().map(libraryPath::resolve);
    }

    @Override
    public @NotNull Path gamePath() {
      return Paths.get(this.game);
    }
  }
}
