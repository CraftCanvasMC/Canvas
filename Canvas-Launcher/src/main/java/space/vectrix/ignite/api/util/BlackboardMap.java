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
package space.vectrix.ignite.api.util;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class BlackboardMap {
  private final ConcurrentHashMap<Key<Object>, Object> blackboardMap = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Key<Object>> blackboardKeys = new ConcurrentHashMap<>();

  public BlackboardMap() {}

  public <V> @NonNull Optional<V> get(final @NonNull Key<V> key) {
    return Optional.ofNullable(key.type.cast(this.blackboardMap.get(key)));
  }

  public <V> void put(final @NonNull Key<V> key, final V value) {
    put(this.blackboardMap, key, value);
  }

  public <V> @Nullable V computeIfAbsent(final @NonNull Key<V> key, final @NonNull Function<? super Key<V>, ? extends V> valueFunction) {
    return computeIfAbsent(this.blackboardMap, key, valueFunction);
  }

  @SuppressWarnings("unchecked")
  private <C1, C2, V> void put(final ConcurrentHashMap<C1, C2> map, final Key<V> key, final V value) {
    map.put((C1) key, (C2) value);
  }

  @SuppressWarnings("unchecked")
  private <C1, C2, V> V computeIfAbsent(final ConcurrentHashMap<C1, C2> map, final Key<V> key, final Function<? super Key<V>, ? extends V> valueFunction) {
    return (V) map.computeIfAbsent((C1) key, (Function<? super C1, ? extends C2>) valueFunction);
  }

  private ConcurrentHashMap<String, Key<Object>> getKeys() {
    return this.blackboardKeys;
  }

  /**
   * Unique blackboard key
   */
  public static final class Key<T> implements Comparable<Key<T>> {
    @SuppressWarnings("unchecked")
    public static <V> @NonNull Key<V> getOrCreate(final @NonNull BlackboardMap owner, final @NonNull String name, final @NonNull Class<? super V> clazz) {
      final Key<V> result = (Key<V>) owner.getKeys().computeIfAbsent(name, (n) -> new Key<>(n, (Class<Object>) clazz));

      if (result.type != clazz) {
        throw new IllegalArgumentException("Invalid type!");
      }

      return result;
    }

    private static final AtomicLong ID_GENERATOR = new AtomicLong();

    private final String name;
    private final long identifier;
    private final Class<T> type;

    private Key(final String name, final Class<T> type) {
      this.type = type;
      this.name = name;

      this.identifier = Key.ID_GENERATOR.getAndIncrement();
    }

    public final @NonNull String getName() {
      return this.name;
    }

    @Override
    public int hashCode() {
      return (int) (this.identifier ^ (this.identifier >>> 32));
    }

    @Override
    public boolean equals(final @Nullable Object other) {
      if (other == null) return false;
      if (!(other instanceof Key)) return false;
      final Key<?> that = (Key<?>) other;
      return Objects.equals(this.identifier, that.identifier);
    }

    @Override
    public int compareTo(final @NonNull Key<T> other) {
      if (this == other) return 0;
      if (this.identifier < other.identifier) return -1;
      if (this.identifier > other.identifier) return 1;
      throw new RuntimeException("Unable to compare provided key!");
    }
  }
}
