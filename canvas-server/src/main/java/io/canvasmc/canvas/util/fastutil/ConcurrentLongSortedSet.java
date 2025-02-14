package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongComparator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;
import org.jetbrains.annotations.NotNull;

/**
 * A thread-safe implementation of LongSortedSet backed by ConcurrentSkipListSet.
 * Provides concurrent access and maintains elements in sorted order.
 */
public final class ConcurrentLongSortedSet implements LongSortedSet {

    private final ConcurrentSkipListSet<Long> backing;

    /**
     * Creates a new empty concurrent sorted set
     */
    public ConcurrentLongSortedSet() {
        this.backing = new ConcurrentSkipListSet<>();
    }

    /**
     * Creates a new concurrent sorted set containing elements from the given collection
     *
     * @param collection initial elements
     * @throws NullPointerException if collection is null
     */
    public ConcurrentLongSortedSet(Collection<Long> collection) {
        this();
        addAll(Objects.requireNonNull(collection, "Initial collection cannot be null"));
    }

    @Override
    public LongBidirectionalIterator iterator(long fromElement) {
        return FastUtilHackUtil.wrap(backing.tailSet(fromElement).iterator());
    }

    @Override
    public LongBidirectionalIterator iterator() {
        return FastUtilHackUtil.wrap(backing.iterator());
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @NotNull
    @Override
    public Object[] toArray() {
        return backing.toArray();
    }

    @NotNull
    @Override
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
    public boolean addAll(@NotNull Collection<? extends Long> collection) {
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
    public boolean add(long key) {
        return backing.add(key);
    }

    @Override
    public boolean contains(long key) {
        return backing.contains(key);
    }

    @Override
    public long[] toLongArray() {
        return backing.stream()
                      .mapToLong(Long::longValue)
                      .toArray();
    }

    @Override
    public long[] toArray(long[] array) {
        Objects.requireNonNull(array, "Array cannot be null");
        long[] result = toLongArray();
        if (array.length < result.length) {
            return result;
        }
        System.arraycopy(result, 0, array, 0, result.length);
        if (array.length > result.length) {
            array[result.length] = 0L; // Set terminating null as per Collection convention
        }
        return array;
    }

    @Override
    public boolean addAll(LongCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        return c.stream().map(backing::add).reduce(false, (a, b) -> a || b);
    }

    @Override
    public boolean containsAll(LongCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        return c.stream().allMatch(this::contains);
    }

    @Override
    public boolean removeAll(LongCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        return c.stream().map(this::remove).reduce(false, (a, b) -> a || b);
    }

    @Override
    public boolean retainAll(LongCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        return backing.retainAll(c);
    }

    @Override
    public boolean remove(long k) {
        return backing.remove(k);
    }

    @Override
    public LongSortedSet subSet(long fromElement, long toElement) {
        // Используем min/max для определения правильного диапазона
        long actualFromElement = Math.min(fromElement, toElement);
        long actualToElement = Math.max(fromElement, toElement);
        return new ConcurrentLongSortedSet(backing.subSet(actualFromElement, actualToElement));
    }

    @Override
    public LongSortedSet headSet(long toElement) {
        return new ConcurrentLongSortedSet(backing.headSet(toElement));
    }

    @Override
    public LongSortedSet tailSet(long fromElement) {
        return new ConcurrentLongSortedSet(backing.tailSet(fromElement));
    }

    @Override
    public LongComparator comparator() {
        return null; // Natural ordering is used
    }

    @Override
    public long firstLong() {
        if (isEmpty()) {
            throw new IllegalStateException("Set is empty");
        }
        return backing.first();
    }

    @Override
    public long lastLong() {
        if (isEmpty()) {
            throw new IllegalStateException("Set is empty");
        }
        return backing.last();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LongSortedSet that)) return false;
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
