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
package space.vectrix.ignite.api;

import org.checkerframework.checker.nullness.qual.NonNull;
import space.vectrix.ignite.api.event.EventManager;
import space.vectrix.ignite.api.mod.ModManager;

import java.nio.file.Path;

/**
 * Represents the platform Ignite is starting on, to be used
 * by mods.
 *
 * @since 0.5.0
 */
public interface Platform {
  /**
   * Returns the {@link ModManager} mod manager.
   *
   * @return the mod manager
   * @since 0.5.0
   */
  @NonNull ModManager getModManager();

  /**
   * Returns the {@link EventManager} event manager.
   *
   * @return the event manager
   * @since 0.5.0
   */
  @NonNull EventManager getEventManager();

  /**
   * Returns the {@link Path} configurations directory.
   *
   * @return the configurations directory
   * @since 0.5.0
   */
  @NonNull Path getConfigs();

  /**
   * Return the {@link Path} mods directory.
   *
   * @return the mods directory
   * @since 0.5.0
   */
  @NonNull Path getMods();
}
