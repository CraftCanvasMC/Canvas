package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * A thread-safe implementation of Long2ObjectMap using ConcurrentHashMap as backing storage.
 * Provides concurrent access and high performance for long-to-object mappings.
 *
 * @param <V> the type of values maintained by this map
 */
public final class Long2ObjectConcurrentHashMap<V> implements Long2ObjectMap<V> {

    private final ConcurrentHashMap<Long, V> backing;
    private V defaultReturnValue;

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * Creates a new empty concurrent map with default initial capacity
     */
    public Long2ObjectConcurrentHashMap() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Creates a new empty concurrent map with specified initial capacity
     *
     * @param initialCapacity the initial capacity of the map
     * @throws IllegalArgumentException if initialCapacity is negative
     */
    public Long2ObjectConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new empty concurrent map with specified initial capacity and load factor
     *
     * @param initialCapacity initial capacity of the map
     * @param loadFactor load factor of the map
     * @throws IllegalArgumentException if initialCapacity is negative or loadFactor is non-positive
     */
    public Long2ObjectConcurrentHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        if (loadFactor <= 0) {
            throw new IllegalArgumentException("Load factor must be positive: " + loadFactor);
        }
        this.backing = new ConcurrentHashMap<>(initialCapacity, loadFactor);
    }

    /**
     * Creates a new concurrent map containing the same mappings as the specified map
     *
     * @param map the map whose mappings are to be placed in this map
     * @throws NullPointerException if map is null
     */
    public Long2ObjectConcurrentHashMap(Map<? extends Long, ? extends V> map) {
        this(Math.max(DEFAULT_INITIAL_CAPACITY, map.size()));
        putAll(Objects.requireNonNull(map, "Source map cannot be null"));
    }

    @Override
    public V get(long key) {
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
    public ObjectSet<Entry<V>> long2ObjectEntrySet() {
        return FastUtilHackUtil.entrySetLongWrap(backing);
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
    public V remove(long key) {
        V previous = backing.remove(key);
        return (previous == null && !backing.containsKey(key)) ? defaultReturnValue : previous;
    }

    @Override
    public void clear() {
        backing.clear();
    }

    /**
     * Returns the value to which the specified key is mapped, or defaultValue if
     * this map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @param defaultValue the default mapping of the key
     * @return the value to which the specified key is mapped, or defaultValue
     */
    public V getOrDefault(long key, V defaultValue) {
        V value = backing.get(key);
        return (value == null && !backing.containsKey(key)) ? defaultValue : value;
    }

    /**
     * Associates the specified value with the specified key if no value is present
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value or defaultReturnValue if none
     */
    public V putIfAbsent(long key, V value) {
        V previous = backing.putIfAbsent(key, value);
        return (previous == null && !backing.containsKey(key)) ? defaultReturnValue : previous;
    }

    /**
     * Removes the entry for the specified key only if it is currently mapped to the specified value
     *
     * @param key key with which the specified value is associated
     * @param value value expected to be associated with the specified key
     * @return true if the value was removed
     */
    public boolean remove(long key, Object value) {
        return backing.remove(key, value);
    }

    /**
     * Replaces the entry for the specified key only if it is currently mapped to the specified value
     *
     * @param key key with which the specified value is associated
     * @param oldValue value expected to be associated with the specified key
     * @param newValue value to be associated with the specified key
     * @return true if the value was replaced
     */
    public boolean replace(long key, V oldValue, V newValue) {
        return backing.replace(key, oldValue, newValue);
    }

    /**
     * Replaces the entry for the specified key only if it is currently mapped to some value
     *
     * @param key key with which the specified value is associated
     * @param value value to be associated with the specified key
     * @return the previous value or defaultReturnValue if none
     */
    public V replace(long key, V value) {
        V previous = backing.replace(key, value);
        return (previous == null && !backing.containsKey(key)) ? defaultReturnValue : previous;
    }

    /**
     * Attempts to compute a mapping for the specified key and its current mapped value
     *
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or defaultReturnValue if none
     */
    @Override
    public V compute(long key, BiFunction<? super Long, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction, "Remapping function cannot be null");
        V newValue = backing.compute(key, remappingFunction);
        return (newValue == null && !backing.containsKey(key)) ? defaultReturnValue : newValue;
    }

    /**
     * Returns the concurrent map backing this primitive map
     *
     * @return the backing concurrent map
     */
    public ConcurrentHashMap<Long, V> concurrentView() {
        return backing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Long2ObjectMap<?> that)) return false;
        
        if (size() != that.size()) return false;
        return long2ObjectEntrySet().containsAll(that.long2ObjectEntrySet());
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
