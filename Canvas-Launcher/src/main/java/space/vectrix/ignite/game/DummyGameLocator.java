package space.vectrix.ignite.game;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;
import space.vectrix.ignite.Blackboard;
import space.vectrix.ignite.IgniteBootstrap;

/**
 * Provides a general game locator.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class DummyGameLocator implements GameLocatorService {
  private DummyGameProvider provider;

  @Override
  public @NotNull String id() {
    return "dummy";
  }

  @Override
  public @NotNull String name() {
    return "Dummy";
  }

  @Override
  public int priority() {
    return Integer.MAX_VALUE;
  }

  @Override
  public boolean shouldApply() {
    return true;
  }

  @Override
  public void apply(final @NotNull IgniteBootstrap bootstrap) throws Throwable {
    Logger.warn("Using the dummy game provider means that all the jars found in the game libraries directory");
    Logger.warn("will be loaded into the classpath. If this causes an unexpected problem, please delete the");
    Logger.warn("libraries directory and try launch again.");

    if(this.provider == null) {
      this.provider = new DummyGameProvider();
    }
  }

  @Override
  public @NotNull GameProvider locate() {
    return this.provider;
  }

  /* package */ static final class DummyGameProvider implements GameProvider {
    /* package */ DummyGameProvider() {
    }

    @Override
    public @NotNull Stream<Path> gameLibraries() {
      final Path libraryPath = Blackboard.raw(Blackboard.GAME_LIBRARIES);
      try(final Stream<Path> stream = Files.walk(libraryPath)) {
        return stream
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().endsWith(".jar"));
      } catch(final Throwable throwable) {
        return Stream.empty();
      }
    }

    @Override
    public @NotNull Path gamePath() {
      return Blackboard.raw(Blackboard.GAME_JAR);
    }
  }
}
