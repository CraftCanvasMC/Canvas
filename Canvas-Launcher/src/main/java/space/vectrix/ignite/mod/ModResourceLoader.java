package space.vectrix.ignite.mod;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;
import space.vectrix.ignite.util.IgniteConstants;

/**
 * Represents the mod resource loader.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class ModResourceLoader {
  /* package */ @NotNull List<ModContainerImpl> loadResources(final @NotNull ModsImpl engine) {
    final List<ModContainerImpl> containers = new ArrayList<>();

    for(final ModResource resource : engine.resources()) {
      if(resource.locator().equals(ModResourceLocator.LAUNCHER_LOCATOR) || resource.locator().equals(ModResourceLocator.GAME_LOCATOR)) {
        final ModConfig config = new ModConfig(
          IgniteConstants.API_TITLE,
          IgniteConstants.API_VERSION
        );

        containers.add(new ModContainerImpl(Logger.tag(config.id()), resource, config));
        continue;
      }

      final Path resourcePath = resource.path();
      try(final JarFile jarFile = new JarFile(resourcePath.toFile())) {
        final JarEntry jarEntry = jarFile.getJarEntry(IgniteConstants.MOD_CONFIG);
        if(jarEntry == null) continue;

        final InputStream inputStream = jarFile.getInputStream(jarEntry);
        final ModConfig config = IgniteConstants.GSON.fromJson(new InputStreamReader(inputStream), ModConfig.class);

        containers.add(new ModContainerImpl(Logger.tag(config.id()), resource, config));
      } catch(final IOException exception) {
        // Ignore
      }
    }

    return containers;
  }
}
