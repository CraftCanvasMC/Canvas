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
package space.vectrix.ignite.api.config;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.loader.ConfigurationLoader;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A configuration wrapper to assist in managing the configuration loader
 * and configuration object.
 *
 * @param <T> the configuration object type
 * @param <N> the configuration node type
 * @since 0.5.0
 */
public final class Configuration<T, N extends ConfigurationNode> {
  /**
   * Returns a new {@link Key} for the specified {@link Class}
   * and {@link Path} to be associated with a {@link Configuration}.
   *
   * @param type the configuration object class
   * @param path the configuration path
   * @param <T> the configuration object type
   * @return a configuration key
   * @since 0.5.0
   */
  public static @NonNull <T> Key<T> key(final @NonNull Class<T> type, final @NonNull Path path) {
    return new Key<>(type, type.getSimpleName(), path);
  }

  /**
   * Returns a new {@link Key} for the specified {@link Class},
   * with the {@link String} identifier, and {@link Path} to be associated with
   * a {@link Configuration}.
   *
   * @param type the configuration object class
   * @param id the configuration identifier
   * @param path the configuration path
   * @param <T> the configuration object type
   * @return a configuration key
   * @since 0.5.0
   */
  public static @NonNull <T> Key<T> key(final @NonNull Class<T> type, final @NonNull String id, final @NonNull Path path) {
    return new Key<>(type, id, path);
  }

  private final Key<T> key;
  private final ConfigurationLoader<N> loader;

  private N node;
  private T instance;

  /* package */ Configuration(final @NonNull Key<T> key, final @NonNull ConfigurationLoader<N> loader) {
    this.key = key;
    this.loader = loader;
  }

  /**
   * Loads the configuration from the {@link ConfigurationLoader} if it exists.
   *
   * @throws ConfigurateException if the configuration could not be loaded
   * @since 0.5.0
   */
  public void load() throws ConfigurateException {
    this.node = this.loader.load();
    this.instance = this.node.get(this.key.type());
  }

  /**
   * Saves the configuration to the {@link ConfigurationLoader}.
   *
   * @throws ConfigurateException if the configuration could not be saved
   * @since 0.5.0
   */
  public void save() throws ConfigurateException {
    if(this.node == null) this.node = this.loader.createNode();
    if(this.instance != null) this.node.set(this.key.type(), this.instance);
    this.loader.save(this.node);
  }

  /**
   * Returns the {@link Key}.
   *
   * @return the configuration key
   * @since 0.5.0
   */
  public @NonNull Key<T> key() {
    return this.key;
  }

  /**
   * Returns the {@code N} node.
   *
   * @return the configuration node
   * @since 0.5.0
   */
  public @MonotonicNonNull N node() {
    return this.node;
  }

  /**
   * Returns the {@code T} configuration object.
   *
   * @return the configuration object
   * @since 0.5.0
   */
  public @MonotonicNonNull T instance() {
    return this.instance;
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.key, this.loader, this.node, this.instance);
  }

  @Override
  public boolean equals(final @Nullable Object other) {
    if(this == other) return true;
    if(!(other instanceof Configuration)) return false;
    final Configuration<?, ?> that = (Configuration<?, ?>) other;
    return Objects.equals(this.key, that.key)
      && Objects.equals(this.loader, that.loader)
      && Objects.equals(this.node, that.node)
      && Objects.equals(this.instance, that.instance);
  }

  @Override
  public String toString() {
    return "Configuration{key=" + this.key +
      ", loader=" + this.loader +
      ", node=" + this.node +
      ", instance=" + this.instance +
      "}";
  }

  /**
   * Represents a key to a potential {@link Configuration} that could exist or
   * be created.
   *
   * @param <T> the configuration object type
   * @since 0.5.0
   */
  public static final class Key<T> {
    private final Class<T> type;
    private final String id;
    private final Path path;

    /* package */ Key(final @NonNull Class<T> type, final @NonNull String id, final @NonNull Path path) {
      this.type = type;
      this.id = id;
      this.path = path;
    }

    /**
     * Returns the configuration object {@link Class}.
     *
     * @return the configuration object class
     * @since 0.5.0
     */
    public @NonNull Class<T> type() {
      return this.type;
    }

    /**
     * Returns the configuration {@link String} identifier.
     *
     * @return the configuration identifier
     * @since 0.5.0
     */
    public @NonNull String id() {
      return this.id;
    }

    /**
     * Returns the configuration {@link Path}.
     *
     * @return the configuration path
     * @since 0.5.0
     */
    public @NonNull Path path() {
      return this.path;
    }

    @Override
    public int hashCode() {
      return Objects.hash(this.type, this.id, this.path);
    }

    @Override
    public boolean equals(final @Nullable Object other) {
      if (this == other) return true;
      if (!(other instanceof Configuration.Key)) return false;
      final Key<?> that = (Key<?>) other;
      return Objects.equals(this.type, that.type)
        && Objects.equals(this.id, that.id)
        && Objects.equals(this.path, that.path);
    }

    @Override
    public String toString() {
      return "Configuration.Key{type=" + this.type +
        ", id=" + this.id +
        ", path=" + this.path +
        "}";
    }
  }
}
