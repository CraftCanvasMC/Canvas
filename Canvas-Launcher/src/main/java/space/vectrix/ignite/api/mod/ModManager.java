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
package space.vectrix.ignite.api.mod;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collection;
import java.util.Optional;

/**
 * Provides access to manage mods.
 *
 * @since 0.5.0
 */
public interface ModManager {
  /**
   * Returns the {@link ModContainer} for the specified {@link String}
   * identifier, if it exists.
   *
   * @param mod the mod identifier
   * @return the mod container
   * @since 0.5.0
   */
  @NonNull Optional<ModContainer> getContainer(final @NonNull String mod);

  /**
   * Returns the {@link ModContainer} for the specified {@link Object}
   * mod instance, if it exists.
   *
   * @param mod the mod instance
   * @return the mod container
   * @since 0.5.0
   */
  @NonNull Optional<ModContainer> getContainer(final @NonNull Object mod);

  /**
   * Returns {@code true} if a mod with the specified {@link String}
   * mod identifier if loaded.
   *
   * @param mod the mod identifier
   * @return true if the mod is loaded, otherwise false
   * @since 0.5.0
   */
  boolean isLoaded(final @NonNull String mod);

  /**
   * Returns {@code true} if the specified {@link Object} is a mod
   * instance.
   *
   * @param mod the possible mod instance
   * @return true if the object is a mod instance, otherwise false
   * @since 0.5.0
   */
  boolean isInstance(final @NonNull Object mod);

  /**
   * Returns a {@link Collection} of {@link ModContainer}s that have
   * been loaded.
   *
   * @return a collection of loaded mod containers
   * @since 0.5.0
   */
  @NonNull Collection<ModContainer> getContainers();
}
