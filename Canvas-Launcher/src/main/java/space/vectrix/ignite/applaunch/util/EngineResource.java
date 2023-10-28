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
package space.vectrix.ignite.applaunch.util;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import space.vectrix.ignite.api.Blackboard;
import space.vectrix.ignite.api.mod.ModResource;
import space.vectrix.ignite.applaunch.IgniteBootstrap;
import space.vectrix.ignite.applaunch.mod.ModEngine;
import space.vectrix.ignite.applaunch.mod.ModResourceLocator;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public final class EngineResource {
  public static @NonNull ModResource createEngineResource(final ModEngine engine) {
    final Path path = EngineResource.getEnginePath(engine);
    final Manifest manifest = EngineResource.getEngineManifest(engine);
    if (path == null || manifest == null) throw new IllegalStateException("Unable to create engine container!");
    return new ModResource(ModResourceLocator.ENGINE_LOCATOR, path, manifest);
  }

  public static @NonNull ModResource createLaunchResource(final ModEngine engine) {
    final Path path = Blackboard.getProperty(Blackboard.LAUNCH_JAR);
    final Manifest manifest = EngineResource.getLaunchManifest(engine, path);
    if (path == null || manifest == null) throw new IllegalStateException("Unable to create launch container!");
    return new ModResource(ModResourceLocator.LAUNCH_LOCATOR, path, manifest);
  }

  private static @Nullable Manifest getEngineManifest(final ModEngine engine) {
    try {
      final Enumeration<URL> resources = IgniteBootstrap.class.getClassLoader().getResources(IgniteConstants.META_INF + "/" + IgniteConstants.MANIFEST);
      while (resources.hasMoreElements()) {
        final Manifest manifest = new Manifest(resources.nextElement().openStream());
        final Attributes attributes = manifest.getAttributes("space/vectrix/ignite/applaunch/");
        if (attributes != null) return manifest;
      }
    } catch (final IOException exception) {
      engine.getLogger().error("Failed to read launcher manifest!", exception);
    }

    return null;
  }

  private static @Nullable Manifest getLaunchManifest(final ModEngine engine, final Path path) {
    try (final JarFile jarFile = new JarFile(path.toFile())) {
      return jarFile.getManifest();
    } catch (final IOException exception) {
      engine.getLogger().error("Failed to walk the launch jar '" + path + "'!", exception);
    }

    return null;
  }

  private static @Nullable Path getEnginePath(final ModEngine engine) {
    try {
      return new File(IgniteBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath();
    } catch (final URISyntaxException exception) {
      engine.getLogger().error("Failed to determine launcher path!", exception);
    }

    return null;
  }
}
