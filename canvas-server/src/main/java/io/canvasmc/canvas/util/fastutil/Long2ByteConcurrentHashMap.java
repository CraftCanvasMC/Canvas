package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.bytes.ByteCollection;
import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe implementation of Long2ByteMap using ConcurrentHashMap as backing storage.
 * Provides concurrent access and high performance for primitive long-to-byte mappings.
 */
public final class Long2ByteConcurrentHashMap implements Long2ByteMap {

    private final ConcurrentHashMap<Long, Byte> backing;
    private byte defaultReturnValue;

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /**
     * Creates a new empty concurrent map with default initial capacity and load factor
     */
    public Long2ByteConcurrentHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    /**
     * Creates a new empty concurrent map with specified initial capacity and load factor
     *
     * @param initialCapacity initial capacity of the map
     * @param loadFactor load factor of the map
     * @throws IllegalArgumentException if initialCapacity is negative or loadFactor is non-positive
     */
    public Long2ByteConcurrentHashMap(int initialCapacity, float loadFactor) {
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
    public Long2ByteConcurrentHashMap(Map<? extends Long, ? extends Byte> map) {
        this(Math.max(DEFAULT_INITIAL_CAPACITY, map.size()));
        putAll(Objects.requireNonNull(map, "Source map cannot be null"));
    }

    /**
     * Creates a new empty concurrent map with specified initial capacity
     *
     * @param initialCapacity initial capacity of the map
     * @throws IllegalArgumentException if initialCapacity is negative
     */
    public Long2ByteConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    @Override
    public byte get(long key) {
        Byte value = backing.get(key);
        return value != null ? value : defaultReturnValue;
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public boolean containsValue(byte value) {
        return backing.containsValue(value);
    }

    @Override
    public void putAll(Map<? extends Long, ? extends Byte> m) {
        Objects.requireNonNull(m, "Source map cannot be null");
        backing.putAll(m);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public void defaultReturnValue(byte rv) {
        this.defaultReturnValue = rv;
    }

    @Override
    public byte defaultReturnValue() {
        return defaultReturnValue;
    }

    @Override
    public ObjectSet<Entry> long2ByteEntrySet() {
        return FastUtilHackUtil.entrySetLongByteWrap(backing);
    }

    @Override
    public LongSet keySet() {
        return FastUtilHackUtil.wrapLongSet(backing.keySet());
    }

    @Override
    public ByteCollection values() {
        return FastUtilHackUtil.wrapBytes(backing.values());
    }

    @Override
    public boolean containsKey(long key) {
        return backing.containsKey(key);
    }

    @Override
    public byte put(long key, byte value) {
        Byte previous = backing.put(key, value);
        return previous != null ? previous : defaultReturnValue;
    }

    @Override
    public byte remove(long key) {
        Byte previous = backing.remove(key);
        return previous != null ? previous : defaultReturnValue;
    }

    @Override
    public void clear() {
        backing.clear();
    }

    /**
     * Returns the value associated with the specified key, or the default value
     * if no mapping exists.
     *
     * @param key key to look up
     * @param defaultValue value to return if key is not found
     * @return the value associated with key, or defaultValue if not found
     */
    public byte getOrDefault(long key, byte defaultValue) {
        Byte value = backing.get(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Associates the specified value with the specified key if no value is present
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value or defaultReturnValue if none
     */
    public byte putIfAbsent(long key, byte value) {
        Byte previous = backing.putIfAbsent(key, value);
        return previous != null ? previous : defaultReturnValue;
    }

    /**
     * Removes the entry for the specified key only if it is currently mapped to the specified value
     *
     * @param key key with which the specified value is associated
     * @param value value expected to be associated with the key
     * @return true if the value was removed
     */
    public boolean remove(long key, byte value) {
        return backing.remove(key, value);
    }

    /**
     * Replaces the entry for the specified key only if it is currently mapped to the specified value
     *
     * @param key key with which the specified value is associated
     * @param oldValue value expected to be associated with the key
     * @param newValue value to be associated with the key
     * @return true if the value was replaced
     */
    public boolean replace(long key, byte oldValue, byte newValue) {
        return backing.replace(key, oldValue, newValue);
    }

    /**
     * Returns the concurrent map backing this primitive map
     *
     * @return the backing concurrent map
     */
    public ConcurrentHashMap<Long, Byte> concurrentView() {
        return backing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Long2ByteMap that)) return false;
        
        if (size() != that.size()) return false;
        return long2ByteEntrySet().containsAll(that.long2ByteEntrySet());
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
