package space.vectrix.ignite.mod;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import space.vectrix.ignite.Blackboard;
import space.vectrix.ignite.IgniteBootstrap;
import space.vectrix.ignite.util.IgniteConstants;

/**
 * Represents the mod resource locator.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class ModResourceLocator {
  public static final String JAVA_LOCATOR = "java_locator";
  public static final String LAUNCHER_LOCATOR = "launcher_locator";
  public static final String GAME_LOCATOR = "game_locator";

  /* package */ @NotNull List<ModResourceImpl> locateResources() {
    final List<ModResourceImpl> resources = new ArrayList<>();

    // Add the launcher and game resources.
    resources.add(this.createLauncherResource());
    resources.add(this.createGameResource());

    // Retrieve the mods from the mods directory.
    final Path modDirectory = Blackboard.raw(Blackboard.MODS_DIRECTORY);
    try {
      if(modDirectory == null) {
        throw new RuntimeException("Failed to get mods directory!");
      }

      if(Files.notExists(modDirectory)) {
        //noinspection ResultOfMethodCallIgnored
        modDirectory.toFile().mkdirs();
      }

      //noinspection resource
      for(final Path childDirectory : Files.walk(modDirectory).collect(Collectors.toList())) {
        if(!Files.isRegularFile(childDirectory) || !childDirectory.getFileName().toString().endsWith(".jar")) {
          continue;
        }

        try(final JarFile jarFile = new JarFile(childDirectory.toFile())) {
          final JarEntry jarEntry = jarFile.getJarEntry(IgniteConstants.MOD_CONFIG);
          if(jarEntry == null) continue;

          resources.add(new ModResourceImpl(ModResourceLocator.JAVA_LOCATOR, childDirectory, jarFile.getManifest()));
        }
      }
    } catch(final Throwable throwable) {
      throw new RuntimeException("Failed to walk the mods directory!", throwable);
    }

    return resources;
  }

  private @NotNull ModResourceImpl createLauncherResource() {
    final File launcherFile;
    try {
      launcherFile = new File(IgniteBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    } catch(final URISyntaxException exception) {
      throw new RuntimeException("Failed to get launcher path!", exception);
    }

    try(final JarFile jarFile = new JarFile(launcherFile)) {
      return new ModResourceImpl(ModResourceLocator.LAUNCHER_LOCATOR, launcherFile.toPath(), jarFile.getManifest());
    } catch(final Exception exception) {
      throw new RuntimeException("Failed to get launcher manifest!", exception);
    }
  }

  private @NotNull ModResourceImpl createGameResource() {
    final File gameFile = Blackboard.raw(Blackboard.GAME_JAR).toFile();
    try(final JarFile jarFile = new JarFile(gameFile)) {
      return new ModResourceImpl(ModResourceLocator.GAME_LOCATOR, gameFile.toPath(), jarFile.getManifest());
    } catch(final Exception exception) {
      throw new RuntimeException("Failed to get game manifest!", exception);
    }
  }
}
