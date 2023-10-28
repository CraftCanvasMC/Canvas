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
package space.vectrix.ignite.applaunch.handler;

import cpw.mods.gross.Java9ClassLoaderUtil;
import cpw.mods.modlauncher.api.ILaunchHandlerService;
import cpw.mods.modlauncher.api.ITransformingClassLoader;
import cpw.mods.modlauncher.api.ITransformingClassLoaderBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import space.vectrix.ignite.api.Blackboard;
import space.vectrix.ignite.api.mod.ModResource;
import space.vectrix.ignite.applaunch.IgniteBootstrap;
import space.vectrix.ignite.applaunch.mod.ModEngine;
import space.vectrix.ignite.applaunch.mod.ModResourceLocator;
import space.vectrix.ignite.applaunch.util.IgniteConstants;
import space.vectrix.ignite.applaunch.util.IgniteExclusions;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class IgniteLaunchService implements ILaunchHandlerService {
  private static final String JAVA_HOME_PATH = System.getProperty("java.home");
  private static final Optional<Manifest> DEFAULT_MANIFEST = Optional.of(new Manifest());

  private final ConcurrentMap<URL, Optional<Manifest>> manifests = new ConcurrentHashMap<>();
  private final Logger logger = LogManager.getLogger("Ignite Launch");

  @Override
  public final @NonNull String name() {
    return IgniteConstants.IGNITE_LAUNCH_SERVICE;
  }

  @Override
  public void configureTransformationClassLoader(final @NonNull ITransformingClassLoaderBuilder builder) {
    for (final URL url : Java9ClassLoaderUtil.getSystemClassPathURLs()) {
      try {
        final URI uri = url.toURI();
        if (!this.isTransformable(uri)) {
          this.logger.debug("Skipped adding transformation path for '" + uri + "'!");
          continue;
        }

        builder.addTransformationPath(Paths.get(url.toURI()));
        this.logger.debug("Added transformation path for '" + uri + "'");
      } catch (final URISyntaxException | IOException exception) {
        this.logger.error("Failed to add transformation path for '" + url + "'!", exception);
      }
    }

    builder.setResourceEnumeratorLocator(this.getResourceLocator());
    builder.setManifestLocator(this.getManifestLocator());
  }

  @Override
  public final @NonNull Callable<Void> launchService(final @NonNull String @NonNull [] arguments, final @NonNull ITransformingClassLoader launchClassLoader) {
    launchClassLoader.addTargetPackageFilter(packageLocation -> {
      for (final String packageTest : IgniteExclusions.TRANSFORMATION_EXCLUDED_PACKAGES) {
        if (packageLocation.startsWith(packageTest)) {
          return false;
        }
      }

      return true;
    });

    return () -> {
      this.launchService0(arguments, launchClassLoader);
      return null;
    };
  }

  private final @NonNull Function<String, Enumeration<URL>> getResourceLocator() {
    return resourceLocation -> {
      for (final String test : IgniteExclusions.RESOURCE_EXCLUDED_PATHS) {
        if (resourceLocation.startsWith(test)) {
          return Collections.emptyEnumeration();
        }
      }

      return new Enumeration<URL>() {
        private final Iterator<ModResource> resources = IgniteBootstrap.getInstance().getModEngine().getResources().iterator();
        private URL next = this.computeNext();

        @Override
        public final boolean hasMoreElements() {
          return this.next != null;
        }

        @Override
        public final @NonNull URL nextElement() {
          final URL current = this.next;
          if (current == null) throw new NoSuchElementException();
          this.next = this.computeNext();
          return current;
        }

        private URL computeNext() {
          ModResource resource = null;
          while (this.resources.hasNext() && resource == null) {
            final Path resolved = (resource = this.resources.next()).getFileSystem().getPath(resourceLocation);
            if (!resource.getLocator().equals(ModResourceLocator.JAVA_LOCATOR)) {
              continue;
            }

            if (Files.exists(resolved)) {
              try {
                return resolved.toUri().toURL();
              } catch (final MalformedURLException exception) {
                IgniteLaunchService.this.logger.error("Failed to compute resource path for '" + resolved + "'!", exception);
              }
            }
          }

          return null;
        }
      };
    };
  }

  private final @NonNull Function<URLConnection, Optional<Manifest>> getManifestLocator() {
    return connection -> {
      if (connection instanceof JarURLConnection) {
        final URL url = ((JarURLConnection) connection).getJarFileURL();
        final Optional<Manifest> manifest = this.manifests.computeIfAbsent(url, key -> {
          for (final ModResource resource : IgniteBootstrap.getInstance().getModEngine().getResources()) {
            if (!resource.getLocator().equals(ModResourceLocator.JAVA_LOCATOR)) {
              continue;
            }

            try {
              if (resource.getPath().toAbsolutePath().normalize().equals(Paths.get(key.toURI()).toAbsolutePath().normalize())) {
                return Optional.ofNullable(resource.getManifest());
              }
            } catch (final URISyntaxException exception) {
              this.logger.error("Failed to load manifest from jar '" + url + "'!", exception);
            }
          }

          return IgniteLaunchService.DEFAULT_MANIFEST;
        });

        try {
          if (manifest == IgniteLaunchService.DEFAULT_MANIFEST) {
            return Optional.ofNullable(((JarURLConnection) connection).getManifest());
          } else {
            return manifest;
          }
        } catch (final IOException exception) {
          this.logger.error("Failed to load manifest from connection for '" + url + "'!", exception);
        }
      }

      return Optional.empty();
    };
  }

  /**
   * Launch the service.
   *
   * @param arguments The arguments to launch the service with
   * @param launchClassLoader The transforming class loader to load classes with
   */
  private void launchService0(final @NonNull String[] arguments, final @NonNull ITransformingClassLoader launchClassLoader) throws Exception {
    Thread.currentThread().setContextClassLoader((ClassLoader) launchClassLoader);

    final Path launchJar = Blackboard.getProperty(Blackboard.LAUNCH_JAR);
    if (launchJar != null && Files.exists(launchJar)) {
      // Invoke the main method on the provided ClassLoader.
      Class.forName("space.vectrix.ignite.launch.IgniteLaunch", true, launchClassLoader.getInstance())
        .getMethod("main", ModEngine.class, String[].class)
        .invoke(null, IgniteBootstrap.getInstance().getModEngine(), arguments);
    } else {
      throw new IllegalStateException("No launch jar was found!");
    }
  }

  private boolean isTransformable(final URI uri) throws URISyntaxException, IOException {
    final File bootstrap = new File(IgniteBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    final File file = new File(uri);

    // Ensure JVM internals are not transformable.
    if (file.getAbsolutePath().startsWith(IgniteLaunchService.JAVA_HOME_PATH)) {
      return false;
    }

    if (!bootstrap.toPath().toAbsolutePath().normalize().equals(file.toPath().toAbsolutePath().normalize())) {
      if (file.isDirectory()) {
        for (final String test : IgniteExclusions.TRANSFORMATION_EXCLUDED_PATHS) {
          if (new File(file, test).exists()) {
            return false;
          }
        }
      } else if (file.isFile()) {
        try (final JarFile jarFile = new JarFile(new File(uri))) {
          for (final String test : IgniteExclusions.TRANSFORMATION_EXCLUDED_PATHS) {
            if (jarFile.getEntry(test) != null) {
              return false;
            }
          }
        }
      }
    }

    return true;
  }
}
