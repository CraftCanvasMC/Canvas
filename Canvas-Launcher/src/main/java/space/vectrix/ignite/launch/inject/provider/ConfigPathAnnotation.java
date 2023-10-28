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
package space.vectrix.ignite.launch.inject.provider;

import org.checkerframework.checker.nullness.qual.Nullable;
import space.vectrix.ignite.api.config.path.ConfigPath;

import java.lang.annotation.Annotation;

public final class ConfigPathAnnotation implements ConfigPath {
  public static final ConfigPath NON_SHARED = new ConfigPathAnnotation(false);
  public static final ConfigPath SHARED = new ConfigPathAnnotation(true);

  private final boolean shared;

  private ConfigPathAnnotation(final boolean shared) {
    this.shared = shared;
  }

  @Override
  public boolean shared() {
    return this.shared;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    return ConfigPath.class;
  }

  @Override
  public int hashCode() {
    return (127 * "shared".hashCode()) ^ Boolean.valueOf(this.shared()).hashCode();
  }

  @Override
  public boolean equals(final @Nullable Object other) {
    if(this == other) return true;
    if(!(other instanceof ConfigPath)) return false;
    final ConfigPath that = (ConfigPath) other;
    return this.shared() == that.shared();
  }

  @Override
  public String toString() {
    return "@ConfigPath(shared=" + this.shared() + ")";
  }
}
