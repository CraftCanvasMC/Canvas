package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.longs.Long2ObjectFunction;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.io.Serial;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongFunction;

/**
 * Thread-safe implementation of Long2ObjectOpenHashMap using ConcurrentHashMap as backing storage.
 * Provides concurrent access and maintains thread safety for all operations.
 *
 * @param <V> the type of values maintained by this map
 */
public class Long2ObjectOpenConcurrentHashMap<V> extends Long2ObjectOpenHashMap<V> {

    @Serial
    private static final long serialVersionUID = -121514116954680057L;

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private final ConcurrentHashMap<Long, V> backing;
    private V defaultReturnValue;

    /**
     * Constructs an empty map with default initial capacity
     */
    public Long2ObjectOpenConcurrentHashMap() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Constructs an empty map with specified initial capacity
     *
     * @param initialCapacity initial capacity of the map
     */
    public Long2ObjectOpenConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Constructs an empty map with specified initial capacity and load factor
     *
     * @param initialCapacity initial capacity of the map
     * @param loadFactor      load factor for the map
     */
    public Long2ObjectOpenConcurrentHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        if (loadFactor <= 0) {
            throw new IllegalArgumentException("Load factor must be positive: " + loadFactor);
        }
        this.backing = new ConcurrentHashMap<>(initialCapacity, loadFactor);
    }

    /**
     * Constructs a new map with the same mappings as the specified map
     *
     * @param map the map whose mappings are to be placed in this map
     */
    public Long2ObjectOpenConcurrentHashMap(Map<? extends Long, ? extends V> map) {
        this(Math.max(DEFAULT_INITIAL_CAPACITY, map.size()));
        putAll(Objects.requireNonNull(map, "Source map cannot be null"));
    }

    @Override
    public V get(long key) {
        V value = backing.get(key);
        return (value == null && !backing.containsKey(key)) ? defaultReturnValue : value;
    }

    @Override
    public V get(Object key) {
        V value = backing.get(key);
        return (value == null && !backing.containsKey(key)) ? defaultReturnValue : value;
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public boolean containsValue(Object value) {
        return backing.containsValue(value);
    }

    @Override
    public void putAll(Map<? extends Long, ? extends V> m) {
        Objects.requireNonNull(m, "Source map cannot be null");
        backing.putAll(m);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public void defaultReturnValue(V rv) {
        this.defaultReturnValue = rv;
    }

    @Override
    public V defaultReturnValue() {
        return defaultReturnValue;
    }

    @Override
    public FastEntrySet<V> long2ObjectEntrySet() {
        return FastUtilHackUtil.entrySetLongWrapFast(backing);
    }

    @Override
    public LongSet keySet() {
        return FastUtilHackUtil.wrapLongSet(backing.keySet());
    }

    @Override
    public ObjectCollection<V> values() {
        return FastUtilHackUtil.wrap(backing.values());
    }

    @Override
    public boolean containsKey(long key) {
        return backing.containsKey(key);
    }

    @Override
    public V put(long key, V value) {
        V previous = backing.put(key, value);
        return (previous == null && !backing.containsKey(key)) ? defaultReturnValue : previous;
    }

    @Override
    public V put(Long key, V value) {
        Objects.requireNonNull(key, "Key cannot be null");
        return put(key.longValue(), value);
    }

    @Override
    public V remove(long key) {
        V previous = backing.remove(key);
        return (previous == null && !backing.containsKey(key)) ? defaultReturnValue : previous;
    }

    @Override
    public boolean trim() {
        return true;
    }

    @Override
    public boolean trim(final int n) {
        return true;
    }

    @Override
    public boolean replace(final long k, final V oldValue, final V newValue) {
        return backing.replace(k, oldValue, newValue);
    }

    @Override
    public V replace(final long k, final V v) {
        V previous = backing.replace(k, v);
        return (previous == null && !backing.containsKey(k)) ? defaultReturnValue : previous;
    }

    @Override
    public boolean replace(final Long k, final V oldValue, final V newValue) {
        Objects.requireNonNull(k, "Key cannot be null");
        return replace(k.longValue(), oldValue, newValue);
    }

    @Override
    public V replace(final Long k, final V v) {
        Objects.requireNonNull(k, "Key cannot be null");
        return replace(k.longValue(), v);
    }

    @Override
    public boolean remove(final long k, final Object v) {
        return backing.remove(k, v);
    }

    @Override
    public V putIfAbsent(final long k, final V v) {
        V previous = backing.putIfAbsent(k, v);
        return (previous == null && !backing.containsKey(k)) ? defaultReturnValue : previous;
    }

    @Override
    public V putIfAbsent(final Long k, final V v) {
        Objects.requireNonNull(k, "Key cannot be null");
        return putIfAbsent(k.longValue(), v);
    }

    @Override
    public V merge(final long k, final V v, final BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction, "Remapping function cannot be null");
        V newValue = backing.merge(k, v, remappingFunction);
        return (newValue == null && !backing.containsKey(k)) ? defaultReturnValue : newValue;
    }

    @Override
    public V merge(Long k, final V v, final BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(k, "Key cannot be null");
        return merge(k.longValue(), v, remappingFunction);
    }

    @Override
    public V getOrDefault(final long k, final V defaultValue) {
        V value = backing.get(k);
        return (value == null && !backing.containsKey(k)) ? defaultValue : value;
    }

    @Override
    public V getOrDefault(Object k, final V defaultValue) {
        if (k instanceof Long key) {
            return getOrDefault(key.longValue(), defaultValue);
        }
        return defaultValue;
    }

    @Override
    public V computeIfPresent(final long k, final BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction, "Remapping function cannot be null");
        V newValue = backing.computeIfPresent(k, remappingFunction);
        return (newValue == null && !backing.containsKey(k)) ? defaultReturnValue : newValue;
    }

    @Override
    public V computeIfPresent(final Long k, final BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(k, "Key cannot be null");
        return computeIfPresent(k.longValue(), remappingFunction);
    }

    @Override
    public V computeIfAbsent(final long k, final LongFunction<? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction, "Mapping function cannot be null");
        V newValue = backing.computeIfAbsent(k, mappingFunction::apply);
        return (newValue == null && !backing.containsKey(k)) ? defaultReturnValue : newValue;
    }

    @Override
    public V computeIfAbsentPartial(final long key, final Long2ObjectFunction<? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction, "Mapping function cannot be null");
        if (!mappingFunction.containsKey(key)) {
            return defaultReturnValue;
        }
        return computeIfAbsent(key, mappingFunction::apply);
    }

    @Override
    public V compute(final long k, final BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction, "Remapping function cannot be null");
        V newValue = backing.compute(k, remappingFunction);
        return (newValue == null && !backing.containsKey(k)) ? defaultReturnValue : newValue;
    }

    @Override
    public V compute(final Long k, final BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(k, "Key cannot be null");
        return compute(k.longValue(), remappingFunction);
    }

    @Override
    public Long2ObjectOpenHashMap<V> clone() {
        return new Long2ObjectOpenConcurrentHashMap<>(this);
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public ObjectSet<Map.Entry<Long, V>> entrySet() {
        return new FastUtilHackUtil.ConvertingObjectSet<>(
            backing.entrySet(),
            Function.identity(),
            Function.identity()
        );
    }

    @Override
    public V remove(Object key) {
        if (key instanceof Long k) {
            return remove(k.longValue());
        }
        return defaultReturnValue;
    }

    @Override
    public boolean remove(Object key, Object value) {
        if (key instanceof Long k) {
            return remove(k.longValue(), value);
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Map<?, ?> that)) return false;
        return backing.equals(that);
    }

    @Override
    public int hashCode() {
        return backing.hashCode();
    }

    @Override
    public String toString() {
        return backing.toString();
    }
}
