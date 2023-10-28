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

import org.apache.logging.log4j.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Objects;

/**
 * Represents a mod that can be loaded.
 *
 * @since 0.5.0
 */
public final class ModContainer {
  private final Logger logger;
  private final ModResource resource;
  private final ModConfig config;

  public ModContainer(final @NonNull Logger logger,
                      final @NonNull ModResource resource,
                      final @NonNull ModConfig config) {
    this.logger = logger;
    this.resource = resource;
    this.config = config;
  }

  /**
   * Returns the {@link Logger} logger for this container.
   *
   * @return the container logger
   * @since 0.5.0
   */
  public final @NonNull Logger getLogger() {
    return this.logger;
  }

  /**
   * Returns the {@link String} identifier for this container.
   *
   * @return the container identifier
   * @since 0.5.0
   */
  public final @NonNull String getId() {
    return this.config.getId();
  }

  /**
   * Returns the {@link String} version for this container.
   *
   * @return the container version
   * @since 0.5.0
   */
  public final @NonNull String getVersion() {
    return this.config.getVersion();
  }

  /**
   * Returns the {@link ModResource} resource for this container.
   *
   * @return the container resource
   * @since 0.5.0
   */
  public final @NonNull ModResource getResource() {
    return this.resource;
  }

  /**
   * Returns the {@link ModConfig} config for this container.
   *
   * @return the container config
   * @since 0.5.0
   */
  public final @NonNull ModConfig getConfig() {
    return this.config;
  }

  @Override
  public final int hashCode() {
    return Objects.hash(this.resource, this.config);
  }

  @Override
  public final boolean equals(final @Nullable Object other) {
    if (this == other) return true;
    if (!(other instanceof ModContainer)) return false;
    final ModContainer that = (ModContainer) other;
    return Objects.equals(this.resource, that.resource)
      && Objects.equals(this.config, that.config);
  }

  @Override
  public final @NonNull String toString() {
    return this.getId() + "@" + this.getVersion();
  }
}
