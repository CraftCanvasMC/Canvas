package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class Int2ObjectConcurrentHashMap<V> implements Int2ObjectMap<V> {

    private final ConcurrentHashMap<Integer, V> backing;
    private V defaultReturnValue;

    public Int2ObjectConcurrentHashMap() {
        this(16);
    }

    public Int2ObjectConcurrentHashMap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        this.backing = new ConcurrentHashMap<>(initialCapacity);
    }

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

    public V compute(int key, java.util.function.BiFunction<? super Integer, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        return backing.compute(key, remappingFunction);
    }

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

    public V getOrDefault(int key, V defaultValue) {
        V value = get(key);
        return value != null ? value : defaultValue;
    }

    public V putIfAbsent(int key, V value) {
        return backing.putIfAbsent(key, value);
    }

    public boolean remove(int key, Object value) {
        return backing.remove(key, value);
    }

    public boolean replace(int key, V oldValue, V newValue) {
        return backing.replace(key, oldValue, newValue);
    }

    public V replace(int key, V value) {
        return backing.replace(key, value);
    }
}
