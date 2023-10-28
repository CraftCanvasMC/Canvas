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

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents the mod config, which is usually populated using
 * {@link Gson}.
 *
 * @since 0.5.0
 */
public final class ModConfig {
  private @SerializedName("id") String id;
  private @SerializedName("version") String version;
  private @SerializedName("entry") String entry;
  private @SerializedName("dependencies") List<String> requiredDependencies;
  private @SerializedName("optional_dependencies") List<String> optionalDependencies;
  private @SerializedName("mixins") List<String> mixins;
  private @SerializedName("access_wideners") List<String> accessWideners;
  private @SerializedName("canvas_extensions") List<String> canvas_extensions; // Canvas

  public ModConfig() {}

  public ModConfig(final @NonNull String id,
                   final @NonNull String version) {
    this.id = id;
    this.version = version;
  }

  public ModConfig(final @NonNull String id,
                   final @NonNull String version,
                   final @Nullable String entry,
                   final @Nullable List<String> requiredDependencies,
                   final @Nullable List<String> optionalDependencies,
                   final @Nullable List<String> mixins,
                   final @Nullable List<String> accessWideners,
                   final @Nullable List<String> canvas_extensions) { // Canvas
    this.id = id;
    this.version = version;
    this.entry = entry;
    this.requiredDependencies = requiredDependencies;
    this.optionalDependencies = optionalDependencies;
    this.mixins = mixins;
    this.accessWideners = accessWideners;
    this.canvas_extensions = canvas_extensions; // Canvas
  }
  // Canvas start
  /**
   * Returns the canvas extensions
   * 
   * @return the canvas extenders
   */
  public final @Nullable List<String> getCanvasExtensions(){
    return this.canvas_extensions;
  }
  // Canvas end

  /**
   * Returns the mod identifier.
   *
   * @return the mod identifier
   * @since 0.5.0
   */
  public final @MonotonicNonNull String getId() {
    return this.id;
  }

  /**
   * Returns the mod version.
   *
   * @return the mod version
   * @since 0.5.0
   */
  public final @MonotonicNonNull String getVersion() {
    return this.version;
  }

  /**
   * Returns the mod entry point.
   *
   * @return the mod entry point
   * @since 0.5.0
   */
  public final @Nullable String getEntry() {
    return this.entry;
  }

  /**
   * Returns a list of required dependency identifiers.
   *
   * @return a list of required dependency
   * @since 0.5.0
   */
  public final @Nullable List<String> getRequiredDependencies() {
    return this.requiredDependencies;
  }

  /**
   * Returns a list of optional dependency identifiers.
   *
   * @return a list of optional dependency
   * @since 0.5.0
   */
  public final @Nullable List<String> getOptionalDependencies() {
    return this.optionalDependencies;
  }

  /**
   * Returns a list of mixin configurations.
   *
   * @return a list of mixins
   * @since 0.5.0
   */
  public final @Nullable List<String> getMixins() {
    return this.mixins;
  }

  /**
   * Returns a list of access widener files.
   *
   * @return a list of access wideners
   * @since 0.5.0
   */
  public final @Nullable List<String> getAccessWideners() {
    return this.accessWideners;
  }

  @Override
  public final int hashCode() {
    return Objects.hash(this.id, this.version, this.entry, this.requiredDependencies, this.optionalDependencies, this.mixins);
  }

  @Override
  public final boolean equals(final @Nullable Object other) {
    if (this == other) return true;
    if (!(other instanceof ModConfig)) return false;
    final ModConfig that = (ModConfig) other;
    return Objects.equals(this.id, that.id)
      && Objects.equals(this.version, that.version)
      && Objects.equals(this.entry, that.entry)
      && Objects.deepEquals(this.requiredDependencies, that.requiredDependencies)
      && Objects.deepEquals(this.optionalDependencies, that.optionalDependencies)
      && Objects.deepEquals(this.mixins, that.mixins);
  }

  @Override
  public final @NonNull String toString() {
    return "ModConfig{id=" + this.id +
      ", version=" + this.version +
      ", target=" + this.entry +
      ", requiredDependencies=" + (this.requiredDependencies != null ? Arrays.toString(this.requiredDependencies.toArray(new String[0])) : "[]") +
      ", optionalDependencies=" + (this.optionalDependencies != null ? Arrays.toString(this.optionalDependencies.toArray(new String[0])) : "[]") +
      ", mixins=" + (this.mixins != null ? Arrays.toString(this.mixins.toArray(new String[0])) : "[]") +
      ", accessWideners=" + (this.accessWideners != null ? Arrays.toString(this.accessWideners.toArray(new String[0])) : "[]") +
      ", canvas_extensions=" + (this.canvas_extensions != null ? Arrays.toString(this.canvas_extensions.toArray(new String[0])) : "[]") + // Canvas
      "}";
  }
}
