package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.shorts.ShortCollection;
import it.unimi.dsi.fastutil.shorts.ShortIterator;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;

/**
 * A thread-safe implementation of ShortSet using ConcurrentHashMap.KeySetView as backing storage.
 * This implementation provides concurrent access and high performance for concurrent operations.
 */
public final class ConcurrentShortHashSet implements ShortSet {

    private final ConcurrentHashMap.KeySetView<Short, Boolean> backing;

    /**
     * Creates a new empty concurrent short set
     */
    public ConcurrentShortHashSet() {
        this.backing = ConcurrentHashMap.newKeySet();
    }

    /**
     * Creates a new concurrent short set containing all elements from the given collection
     *
     * @param collection initial elements
     * @throws NullPointerException if collection is null
     */
    public ConcurrentShortHashSet(Collection<Short> collection) {
        this();
        addAll(Objects.requireNonNull(collection, "Initial collection cannot be null"));
    }

    /**
     * Creates a new concurrent short set with the specified initial capacity
     *
     * @param initialCapacity the initial capacity of the set
     * @throws IllegalArgumentException if initialCapacity is negative
     */
    public ConcurrentShortHashSet(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        this.backing = ConcurrentHashMap.newKeySet(initialCapacity);
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public ShortIterator iterator() {
        return FastUtilHackUtil.itrShortWrap(backing);
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return backing.toArray();
    }

    @NotNull
    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(@NotNull T[] array) {
        Objects.requireNonNull(array, "Array cannot be null");
        return backing.toArray(array);
    }

    @Override
    public boolean containsAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return backing.containsAll(collection);
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends Short> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return backing.addAll(collection);
    }

    @Override
    public boolean removeAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return backing.removeAll(collection);
    }

    @Override
    public boolean retainAll(@NotNull Collection<?> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return backing.retainAll(collection);
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public boolean add(short key) {
        return backing.add(key);
    }

    @Override
    public boolean contains(short key) {
        return backing.contains(key);
    }

    @Override
    public short[] toShortArray() {
        Object[] objects = backing.toArray();
        short[] result = new short[objects.length];
        for (int i = 0; i < objects.length; i++) {
            result[i] = (Short) objects[i];
        }
        return result;
    }

    @Override
    public short[] toArray(short[] array) {
        Objects.requireNonNull(array, "Array cannot be null");
        short[] result = toShortArray();
        if (array.length < result.length) {
            return result;
        }
        System.arraycopy(result, 0, array, 0, result.length);
        if (array.length > result.length) {
            array[result.length] = 0;
        }
        return array;
    }

    @Override
    public boolean addAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        boolean modified = false;
        ShortIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            modified |= add(iterator.nextShort());
        }
        return modified;
    }

    @Override
    public boolean containsAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        ShortIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            if (!contains(iterator.nextShort())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean removeAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        boolean modified = false;
        ShortIterator iterator = c.iterator();
        while (iterator.hasNext()) {
            modified |= remove(iterator.nextShort());
        }
        return modified;
    }

    @Override
    public boolean retainAll(ShortCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        return backing.retainAll(c);
    }

    @Override
    public boolean remove(short k) {
        return backing.remove(k);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ShortSet that)) return false;

        if (size() != that.size()) return false;
        return containsAll(that);
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
