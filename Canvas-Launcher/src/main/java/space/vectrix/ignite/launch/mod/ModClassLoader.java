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
package space.vectrix.ignite.launch.mod;

import org.checkerframework.checker.nullness.qual.NonNull;
import space.vectrix.ignite.launch.IgniteLaunch;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public final class ModClassLoader extends URLClassLoader {
  private static final Set<ModClassLoader> LOADERS = new CopyOnWriteArraySet<>();

  static {
    ClassLoader.registerAsParallelCapable();
  }

  public ModClassLoader(final @NonNull URL[] urls) {
    super(urls, IgniteLaunch.class.getClassLoader());
  }

  public void addLoaders() {
    ModClassLoader.LOADERS.add(this);
  }

  @Override
  protected Class<?> loadClass(final @NonNull String name, final boolean resolve) throws ClassNotFoundException {
    return this.loadClass0(name, resolve, true);
  }

  @Override
  public void close() throws IOException {
    ModClassLoader.LOADERS.remove(this);
    super.close();
  }

  private Class<?> loadClass0(final @NonNull String name, final boolean resolve, final boolean checkOther) throws ClassNotFoundException {
    try {
      return super.loadClass(name, resolve);
    } catch (ClassNotFoundException ignored) {
      // Ignored: we'll try others
    }

    if (checkOther) {
      for (final ModClassLoader loader : ModClassLoader.LOADERS) {
        if (loader != this) {
          try {
            return loader.loadClass0(name, resolve, false);
          } catch (final ClassNotFoundException ignored) {
            // We're trying others, safe to ignore
          }
        }
      }
    }

    throw new ClassNotFoundException(name);
  }
}
