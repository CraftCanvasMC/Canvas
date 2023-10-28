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
package space.vectrix.ignite.applaunch.mod;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import space.vectrix.ignite.api.mod.ModConfig;
import space.vectrix.ignite.api.mod.ModContainer;
import space.vectrix.ignite.api.mod.ModResource;
import space.vectrix.ignite.applaunch.IgniteBootstrap;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ModResourceLoader {
  private final Gson gson = new Gson();

  public final @NonNull List<ModContainer> loadResources(final @NonNull ModEngine engine) {
    final List<ModContainer> containers = new ArrayList<>();

    for (final ModResource resource : engine.getResources()) {
      if (resource.getLocator().equals(ModResourceLocator.ENGINE_LOCATOR) || resource.getLocator().equals(ModResourceLocator.LAUNCH_LOCATOR)) {
        final ModConfig config = new ModConfig(
          IgniteBootstrap.class.getPackage().getSpecificationTitle(),
          IgniteBootstrap.class.getPackage().getImplementationVersion()
        );

        containers.add(new ModContainer(engine.getLogger(), resource, config));
        continue;
      }

      final Path resourcePath = resource.getPath();
      try (final JarFile jarFile = new JarFile(resourcePath.toFile())) {
        final JarEntry jarEntry = jarFile.getJarEntry(engine.getResourceLocator().getConfigPath());
        if (jarEntry == null) {
          engine.getLogger().debug("The resource '" + jarFile.getName() + "' does not contain any mod configuration so it is not a mod. Skipping...");
          continue;
        }

        final JsonReader reader = new JsonReader(new InputStreamReader(jarFile.getInputStream(jarEntry), StandardCharsets.UTF_8));
        final ModConfig config = this.gson.fromJson(reader, ModConfig.class);
        if (config.getId() == null || config.getVersion() == null) {
          engine.getLogger().error("Attempted to load the resource '" + jarFile.getName() + "', but found an invalid configuration! Skipping...");
          continue;
        }

        if (engine.hasContainer(config.getId())) {
          engine.getLogger().warn("The mod '" + config.getId() + "' is already loaded! Skipping...");
          continue;
        }

        final Logger logger = LogManager.getLogger(config.getId());
        containers.add(new ModContainer(logger, resource, config));
      } catch (final IOException exception) {
        engine.getLogger().warn("Failed to read resource '" + resourcePath + "'!");
      }
    }

    return containers;
  }
}
