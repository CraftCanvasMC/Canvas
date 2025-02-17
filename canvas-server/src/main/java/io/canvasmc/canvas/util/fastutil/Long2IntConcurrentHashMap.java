package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * A thread-safe implementation of Long2IntMap using ConcurrentHashMap as backing storage.
 */
public final class Long2IntConcurrentHashMap implements Long2IntMap {

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private final ConcurrentHashMap<Long, Integer> backing;
    private int defaultReturnValue;

    public Long2IntConcurrentHashMap() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public Long2IntConcurrentHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public Long2IntConcurrentHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        if (loadFactor <= 0) {
            throw new IllegalArgumentException("Load factor must be positive: " + loadFactor);
        }
        this.backing = new ConcurrentHashMap<>(initialCapacity, loadFactor);
    }

    public Long2IntConcurrentHashMap(Map<? extends Long, ? extends Integer> map) {
        this(Math.max(DEFAULT_INITIAL_CAPACITY, map.size()));
        putAll(Objects.requireNonNull(map, "Source map cannot be null"));
    }

    @Override
    public int get(long key) {
        Integer value = backing.get(key);
        return value != null ? value : defaultReturnValue;
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public void putAll(Map<? extends Long, ? extends Integer> m) {
        Objects.requireNonNull(m, "Source map cannot be null");
        backing.putAll(m);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public void defaultReturnValue(int rv) {
        this.defaultReturnValue = rv;
    }

    @Override
    public int defaultReturnValue() {
        return defaultReturnValue;
    }

    @Override
    public ObjectSet<Entry> long2IntEntrySet() {
        throw new UnsupportedOperationException("Entry set is not supported");
    }

    @Override
    public LongSet keySet() {
        return FastUtilHackUtil.wrapLongSet(backing.keySet());
    }

    @Override
    public IntCollection values() {
        return FastUtilHackUtil.wrapInts(backing.values());
    }

    @Override
    public boolean containsKey(long key) {
        return backing.containsKey(key);
    }

    @Override
    public boolean containsValue(int value) {
        return backing.containsValue(value);
    }

    @Override
    public int put(long key, int value) {
        Integer previous = backing.put(key, value);
        return previous != null ? previous : defaultReturnValue;
    }

    @Override
    public int remove(long key) {
        Integer previous = backing.remove(key);
        return previous != null ? previous : defaultReturnValue;
    }

    @Override
    public void clear() {
        backing.clear();
    }

    public int getOrDefault(long key, int defaultValue) {
        Integer value = backing.get(key);
        return value != null ? value : defaultValue;
    }

    public int putIfAbsent(long key, int value) {
        Integer previous = backing.putIfAbsent(key, value);
        return previous != null ? previous : defaultReturnValue;
    }

    public boolean remove(long key, int value) {
        return backing.remove(key, value);
    }

    public boolean replace(long key, int oldValue, int newValue) {
        return backing.replace(key, oldValue, newValue);
    }

    public int replace(long key, int value) {
        Integer previous = backing.replace(key, value);
        return previous != null ? previous : defaultReturnValue;
    }

    public int compute(long key, BiFunction<? super Long, ? super Integer, ? extends Integer> remappingFunction) {
        Objects.requireNonNull(remappingFunction, "Remapping function cannot be null");
        Integer newValue = backing.compute(key, remappingFunction);
        return newValue != null ? newValue : defaultReturnValue;
    }

    public int computeIfPresent(long key, BiFunction<? super Long, ? super Integer, ? extends Integer> remappingFunction) {
        Objects.requireNonNull(remappingFunction, "Remapping function cannot be null");
        Integer newValue = backing.computeIfPresent(key, remappingFunction);
        return newValue != null ? newValue : defaultReturnValue;
    }

    public int computeIfAbsent(long key, java.util.function.LongFunction<? extends Integer> mappingFunction) {
        Objects.requireNonNull(mappingFunction, "Mapping function cannot be null");
        Integer newValue = backing.computeIfAbsent(key, k -> mappingFunction.apply(k));
        return newValue != null ? newValue : defaultReturnValue;
    }

    public ConcurrentHashMap<Long, Integer> concurrentView() {
        return backing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Long2IntMap that)) return false;

        if (size() != that.size()) return false;
        try {
            for (Entry entry : that.long2IntEntrySet()) {
                if (get(entry.getLongKey()) != entry.getIntValue()) {
                    return false;
                }
            }
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
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
