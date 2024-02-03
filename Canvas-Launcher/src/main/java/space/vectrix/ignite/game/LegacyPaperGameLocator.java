package space.vectrix.ignite.game;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;
import space.vectrix.ignite.Blackboard;
import space.vectrix.ignite.IgniteBootstrap;
import space.vectrix.ignite.agent.IgniteAgent;
import space.vectrix.ignite.util.BlackboardMap;

/**
 * Provides a game locator for Legacy Paper.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class LegacyPaperGameLocator implements GameLocatorService {
  private static final BlackboardMap.@NotNull Key<Path> PAPER_JAR = Blackboard.key("ignite.paper.jar",Path.class, Paths.get("./paper.jar"));
  private static final BlackboardMap.@NotNull Key<String> PAPER_TARGET = Blackboard.key("ignite.paper.target", String.class, "io.papermc.paperclip.Paperclip");
  private static final BlackboardMap.@NotNull Key<String> PAPER_VERSION = Blackboard.key("ignite.paper.version", String.class, "1.12.2");

  private LegacyPaperGameProvider provider;

  @Override
  public @NotNull String id() {
    return "legacy_paper";
  }

  @Override
  public @NotNull String name() {
    return "Legacy Paper";
  }

  @Override
  public int priority() {
    return 100;
  }

  @Override
  public boolean shouldApply() {
    /*final Path path = Blackboard.raw(LegacyPaperGameLocator.PAPER_JAR); // Canvas
    try(final JarFile jarFile = new JarFile(path.toFile())) {
      return jarFile.getJarEntry("patch.properties") != null;
    } catch(final IOException exception) {
      return false;
    }*/ // Canvas
    return false; // Canvas
  }

  @Override
  public void apply(final @NotNull IgniteBootstrap bootstrap) throws Throwable {
    // Populate the blackboard.
    Blackboard.compute(LegacyPaperGameLocator.PAPER_JAR, () -> Paths.get(System.getProperty(LegacyPaperGameLocator.PAPER_JAR.name())));
    Blackboard.compute(LegacyPaperGameLocator.PAPER_TARGET, () -> System.getProperty(LegacyPaperGameLocator.PAPER_TARGET.name()));
    Blackboard.compute(LegacyPaperGameLocator.PAPER_VERSION, () -> System.getProperty(LegacyPaperGameLocator.PAPER_VERSION.name()));

    // Set paperclip to patch only, we launch the game ourselves.
    System.setProperty("paperclip.patchonly", "true");

    final SecurityManager original = System.getSecurityManager();
    try {
      // Set the security manager to a custom one for handling paperclip.
      System.setSecurityManager(new PaperclipExitHandler());

      // Add the paperclip jar.
      try {
        IgniteAgent.addJar(Blackboard.raw(LegacyPaperGameLocator.PAPER_JAR));
      } catch(final IOException exception) {
        throw new IllegalStateException("Unable to add paperclip jar to classpath!", exception);
      }

      // Run paperclip.
      try {
        final Class<?> paperclipClass = Class.forName(Blackboard.raw(LegacyPaperGameLocator.PAPER_TARGET));
        paperclipClass
          .getMethod("main", String[].class)
          .invoke(null, (Object) new String[0]);
      } catch(final ClassNotFoundException exception) {
        throw new IllegalStateException("Unable to execute paperclip jar!", exception);
      }
    } catch(final Throwable throwable) {
      if(throwable instanceof InvocationTargetException) {
        final Throwable target = ((InvocationTargetException) throwable).getTargetException();
        if(target instanceof PaperclipException) {
          final int code = ((PaperclipException) target).code();
          if(code != 0) throw new RuntimeException(String.format("Launcher %s stopped, with exit code: %d", this.name(), code));
        }
      } else {
        throw new RuntimeException(throwable);
      }
    }

    // Set the security manager back.
    System.setSecurityManager(original);

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

  private LegacyPaperGameProvider createProvider() throws Throwable {
    String game = null;

    final Path path = Blackboard.raw(LegacyPaperGameLocator.PAPER_JAR);
    final File file = path.toFile();
    if(!file.exists()) throw new FileNotFoundException(file.getAbsolutePath());
    if(file.isDirectory() || !file.getName().endsWith(".jar")) throw new IOException("Provided path is not a jar file: " + path);

    try(final JarFile jarFile = new JarFile(file)) {
      if(!Blackboard.get(LegacyPaperGameLocator.PAPER_VERSION).isPresent()) {
        // Read the patch.properties to locate the version.
        final JarEntry entry = jarFile.getJarEntry("patch.properties");
        if(entry != null) {
          try(final InputStream inputStream = jarFile.getInputStream(entry); final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while((line = reader.readLine()) != null) {
              final String[] values = line.split("=");

              if(values.length >= 2 && values[0].equalsIgnoreCase("version")) {
                game = String.format("./cache/patched_%s.jar", values[1]);
                Logger.trace("Located paper jar from patch.properties: {}", game);
                break;
              }
            }
          }
        }
      }

      if(game == null) {
        final String version = Blackboard.raw(LegacyPaperGameLocator.PAPER_VERSION);
        game = String.format("./cache/patched_%s.jar", version);
        Logger.trace("Located paper jar from command argument: {}", game);
      }
    }

    return new LegacyPaperGameProvider(game);
  }

  /* package */ static final class LegacyPaperGameProvider implements GameProvider {
    private final String game;

    /* package */ LegacyPaperGameProvider(final @NotNull String game) {
      this.game = game;
    }

    @Override
    public @NotNull Stream<Path> gameLibraries() {
      return Stream.empty();
    }

    @Override
    public @NotNull Path gamePath() {
      return Paths.get(this.game);
    }
  }

  /* package */ static class PaperclipException extends SecurityException {
    private static final long serialVersionUID = 1;
    private final int code;

    /* package */ PaperclipException(final int code) {
      this.code = code;
    }

    /* package */ int code() {
      return this.code;
    }
  }

  /* package */ static class PaperclipExitHandler extends SecurityManager {
    @Override
    public void checkPermission(final @NotNull Permission permission) {
      if(!permission.getName().startsWith("exitVM")) return;
      final int code = Integer.parseInt(permission.getName().split(Pattern.quote("."))[1]);
      throw new PaperclipException(code);
    }
  }
}
