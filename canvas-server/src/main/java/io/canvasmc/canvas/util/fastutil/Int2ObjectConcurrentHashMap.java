package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe implementation of Int2ObjectMap using ConcurrentHashMap as backing storage.
 * Provides concurrent access and high performance for integer-keyed maps.
 *
 * @param <V> the type of values maintained by this map
 */
public final class Int2ObjectConcurrentHashMap<V> implements Int2ObjectMap<V> {

    private final ConcurrentHashMap<Integer, V> backing;
    private V defaultReturnValue;

    /**
     * Creates a new empty concurrent map
     */
    public Int2ObjectConcurrentHashMap() {
        this(16);
    }

    /**
     * Creates a new concurrent map with the specified initial capacity
     *
     * @param initialCapacity the initial capacity of the map
     * @throws IllegalArgumentException if initialCapacity is negative
     */
    public Int2ObjectConcurrentHashMap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        this.backing = new ConcurrentHashMap<>(initialCapacity);
    }

    /**
     * Creates a new concurrent map with the contents of the given map
     *
     * @param map the map whose mappings are to be placed in this map
     * @throws NullPointerException if map is null
     */
    public Int2ObjectConcurrentHashMap(Map<Integer, V> map) {
        this(Math.max(16, map.size()));
        putAll(Objects.requireNonNull(map, "Source map cannot be null"));
    }

    @Override
    public V get(int key) {
        V value = backing.get(key);
        return value != null ? value : defaultReturnValue;
    }

    @Override
    public V get(Object key) {
        V value = backing.get(key);
        return value != null ? value : defaultReturnValue;
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
    public void putAll(Map<? extends Integer, ? extends V> m) {
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
    public ObjectSet<Entry<V>> int2ObjectEntrySet() {
        return FastUtilHackUtil.entrySetIntWrap(backing);
    }

    @Override
    public IntSet keySet() {
        return FastUtilHackUtil.wrapIntSet(backing.keySet());
    }

    @Override
    public ObjectCollection<V> values() {
        return FastUtilHackUtil.wrap(backing.values());
    }

    @Override
    public boolean containsKey(int key) {
        return backing.containsKey(key);
    }

    @Override
    public V put(int key, V value) {
        return backing.put(key, value);
    }

    @Override
    public V remove(int key) {
        return backing.remove(key);
    }

    @Override
    public void clear() {
        backing.clear();
    }

    /**
     * Attempts to compute a mapping for the specified key and its current mapped value (or null if no
     * current mapping exists).
     *
     * @param key key with which the specified value is to be associated
     * @param remappingFunction the function to compute a value
     * @return the new value associated with the specified key, or null if none
     * @throws NullPointerException if the remappingFunction is null
     */
    public V compute(int key, java.util.function.BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        return backing.compute(key, remappingFunction);
    }

    /**
     * Returns a concurrent map view of this map where every operation preserves key primitiveness
     *
     * @return a concurrent map view of this map
     */
    public ConcurrentHashMap<Integer, V> concurrentView() {
        return backing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Int2ObjectMap<?> that)) return false;
        
        if (size() != that.size()) return false;
        return int2ObjectEntrySet().containsAll(that.int2ObjectEntrySet());
    }

    @Override
    public int hashCode() {
        return backing.hashCode();
    }

    @Override
    public String toString() {
        return backing.toString();
    }

    /**
     * Returns the value to which the specified key is mapped, or the default value if
     * this map contains no mapping for the key.
     *
     * @param key the key whose associated value is to be returned
     * @param defaultValue the default mapping of the key
     * @return the value to which the specified key is mapped, or defaultValue
     */
    public V getOrDefault(int key, V defaultValue) {
        V value = get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * If the specified key is not already associated with a value, associates it with
     * the given value and returns null, else returns the current value.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or null
     */
    public V putIfAbsent(int key, V value) {
        return backing.putIfAbsent(key, value);
    }

    /**
     * Removes the entry for the specified key only if it is currently mapped to the specified value.
     *
     * @param key key with which the specified value is associated
     * @param value value expected to be associated with the specified key
     * @return true if the value was removed
     */
    public boolean remove(int key, Object value) {
        return backing.remove(key, value);
    }

    /**
     * Replaces the entry for the specified key only if currently mapped to the specified value.
     *
     * @param key key with which the specified value is associated
     * @param oldValue value expected to be associated with the specified key
     * @param newValue value to be associated with the specified key
     * @return true if the value was replaced
     */
    public boolean replace(int key, V oldValue, V newValue) {
        return backing.replace(key, oldValue, newValue);
    }

    /**
     * Replaces the entry for the specified key only if it is currently mapped to some value.
     *
     * @param key key with which the specified value is associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with the specified key, or null
     */
    public V replace(int key, V value) {
        return backing.replace(key, value);
    }
}
