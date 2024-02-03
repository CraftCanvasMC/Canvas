package space.vectrix.ignite.util;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import static java.util.Objects.requireNonNull;

/**
 * Represents a typed map.
 *
 * @author vectrix
 * @since 1.0.0
 */
public final class BlackboardMap {
  /**
   * Returns a new {@link BlackboardMap}.
   *
   * @return a new blackboard map
   * @since 1.0.0
   */
  public static @NotNull BlackboardMap create() {
    return new BlackboardMap();
  }

  private final Map<String, Key<Object>> keys = new ConcurrentHashMap<>();
  private final Map<Key<Object>, Object> values = new ConcurrentHashMap<>();

  private BlackboardMap() {
  }

  /**
   * Returns the value associated with the given {@link Key}.
   *
   * @param key the key
   * @param <V> the value type
   * @return the value
   * @since 1.0.0
   */
  public <V> @NotNull Optional<V> get(final @NotNull Key<V> key) {
    requireNonNull(key, "key");
    return Optional.ofNullable(key.type().cast(this.values.get(key)));
  }

  /**
   * Sets the given value associated with the given {@link Key}.
   *
   * @param key the key
   * @param value the value
   * @param <V> the value type
   * @since 1.0.0
   */
  public <V> void put(final @NotNull Key<V> key, final @Nullable V value) {
    requireNonNull(key, "key");

    if(value == null) return;
    if(Objects.equals(key.defaultValue(), value)) {
      this.values.remove(key);
    } else {
      this.put(this.values, key, value);
    }
  }

  @SuppressWarnings("unchecked")
  private <C1, C2, V> void put(final @NotNull Map<C1, C2> map, final @NotNull Key<V> key, final @NotNull V value) {
    map.put((C1) key, (C2) value);
  }

  private @NotNull Map<String, Key<Object>> keys() {
    return this.keys;
  }

  /**
   * Represents a key for a {@link BlackboardMap}.
   *
   * @author vectrix
   * @param <T> the value type
   * @since 1.0.0
   */
  public static final class Key<T> implements Comparable<Key<T>> {
    /**
     * Returns a new blackboard key for the given {@link BlackboardMap}, with
     * the given name and type.
     *
     * @param map the blackboard
     * @param name the name
     * @param type the type
     * @param defaultValue the default value
     * @param <V> the value type
     * @return a new key
     * @since 1.0.0
     */
    @SuppressWarnings("unchecked")
    public static <V> @NotNull Key<V> of(final @NotNull BlackboardMap map, final @NotNull String name, final @NotNull Class<? super V> type, final @UnknownNullability V defaultValue) {
      final Key<V> result = (Key<V>) map.keys().computeIfAbsent(name, key -> new Key<>(key, (Class<Object>) type, defaultValue));
      if(result.type != type) throw new IllegalArgumentException("Mismatched type!");
      return result;
    }

    private static final AtomicLong ID_GENERATOR = new AtomicLong();

    private final long identifier;
    private final String name;
    private final Class<T> type;
    private final T defaultValue;

    private Key(final @NotNull String name, final @NotNull Class<T> type, final @UnknownNullability T defaultValue) {
      this.identifier = Key.ID_GENERATOR.getAndIncrement();
      this.name = name;
      this.type = type;
      this.defaultValue = defaultValue;
    }

    /**
     * Returns the name.
     *
     * @return the name
     * @since 1.0.0
     */
    public @NotNull String name() {
      return this.name;
    }

    /**
     * Returns the type.
     *
     * @return the type
     * @since 1.0.0
     */
    public @NotNull Class<T> type() {
      return this.type;
    }

    /**
     * Returns the default value.
     *
     * @return the default value
     * @since 1.0.0
     */
    public @UnknownNullability T defaultValue() {
      return this.defaultValue;
    }

    @Override
    public int hashCode() {
      return (int) (this.identifier ^ (this.identifier >>> 32));
    }

    @Override
    public boolean equals(final @Nullable Object other) {
      if(this == other) return true;
      if(!(other instanceof Key<?>)) return false;
      final Key<?> that = (Key<?>) other;
      return Objects.equals(this.identifier, that.identifier);
    }

    @Override
    public String toString() {
      return "Key{identifier=" + this.identifier + ", name=" + this.name + ", type=" + this.type + "}";
    }

    @Override
    public int compareTo(final @NotNull Key<T> other) {
      if(this == other) return 0;
      if(this.identifier < other.identifier) return -1;
      if(this.identifier > other.identifier) return 1;
      throw new RuntimeException("Unable to compare the given key!");
    }
  }
}
