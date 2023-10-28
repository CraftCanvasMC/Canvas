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
package space.vectrix.ignite.launch;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.checkerframework.checker.nullness.qual.NonNull;
import space.vectrix.ignite.api.Blackboard;
import space.vectrix.ignite.api.Platform;
import space.vectrix.ignite.applaunch.mod.ModEngine;
import space.vectrix.ignite.launch.inject.IgniteModule;
import space.vectrix.ignite.launch.mod.IgniteModManager;

import java.nio.file.Files;

public final class IgniteLaunch {
  private static IgniteLaunch instance;

  public static IgniteLaunch getInstance() {
    return IgniteLaunch.instance;
  }

  public static void main(final @NonNull ModEngine engine, final @NonNull String[] args) {
    new IgniteLaunch().launch(engine, args);
  }

  private final Injector injector;

  public IgniteLaunch() {
    IgniteLaunch.instance = this;
    this.injector = Guice.createInjector(new IgniteModule());
  }

  public void launch(final @NonNull ModEngine engine, final @NonNull String[] args) {
    // Platform
    final IgnitePlatform platform = (IgnitePlatform) this.injector.getInstance(Platform.class);
    ((IgniteModManager) platform.getModManager()).loadPlugins(engine);

    // Launch
    try {
      if (!Files.exists(Blackboard.getProperty(Blackboard.LAUNCH_JAR))) {
        throw new IllegalStateException("No launch jar was found!");
      } else {
        // Invoke the main method on the provided ClassLoader.
        Class.forName(Blackboard.getProperty(Blackboard.LAUNCH_TARGET), true, IgniteLaunch.class.getClassLoader())
          .getMethod("main", String[].class)
          .invoke(null, (Object) args);
      }
    } catch (final Throwable throwable) {
      throw new RuntimeException(throwable);
    }
  }

  public Injector getInjector() {
    return this.injector;
  }
}
