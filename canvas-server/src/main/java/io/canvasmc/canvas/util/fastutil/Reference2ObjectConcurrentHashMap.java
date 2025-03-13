package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

public class Reference2ObjectConcurrentHashMap<K, V> implements Reference2ObjectMap<K, V> {
    private final ConcurrentHashMap<K, V> backing;
    private V defaultReturnValue;

    public Reference2ObjectConcurrentHashMap() {
        this(16);
    }

    public Reference2ObjectConcurrentHashMap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        this.backing = new ConcurrentHashMap<>(initialCapacity);
    }

    public Reference2ObjectConcurrentHashMap(@NotNull Map<K, V> map) {
        this(Math.max(16, map.size()));
        putAll(Objects.requireNonNull(map, "Source map cannot be null"));
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
    public void putAll(Map<? extends K, ? extends V> m) {
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
    public ObjectSet<Entry<K, V>> reference2ObjectEntrySet() {
        return FastUtilHackUtil.entrySetRefWrap(backing);
    }

    @Override
    public @NotNull ObjectCollection<V> values() {
        return FastUtilHackUtil.wrap(backing.values());
    }

    @Override
    public boolean containsKey(Object key) {
        return backing.containsKey(key);
    }

    @Override
    public V put(K key, V value) {
        return backing.put(key, value);
    }

    @Override
    public V remove(Object key) {
        return backing.remove(key);
    }

    @Override
    public @NotNull ReferenceSet<K> keySet() {
        return FastUtilHackUtil.wrap(backing.keySet());
    }

    @Override
    public void clear() {
        backing.clear();
    }

    public V compute(K key, java.util.function.BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction);
        return backing.compute(key, remappingFunction);
    }

    public ConcurrentHashMap<K, V> concurrentView() {
        return backing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reference2ObjectMap<?, ?> that)) return false;

        if (size() != that.size()) return false;
        return reference2ObjectEntrySet().containsAll(that.reference2ObjectEntrySet());
    }

    @Override
    public int hashCode() {
        return backing.hashCode();
    }

    @Override
    public String toString() {
        return backing.toString();
    }

    public V putIfAbsent(K key, V value) {
        return backing.putIfAbsent(key, value);
    }

    public boolean replace(K key, V oldValue, V newValue) {
        return backing.replace(key, oldValue, newValue);
    }

    public V replace(K key, V value) {
        return backing.replace(key, value);
    }
}
