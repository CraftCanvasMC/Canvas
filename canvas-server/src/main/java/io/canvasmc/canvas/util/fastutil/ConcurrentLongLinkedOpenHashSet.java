package io.canvasmc.canvas.util.fastutil;

import it.unimi.dsi.fastutil.longs.*;
import java.io.Serial;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Thread-safe implementation of LongLinkedOpenHashSet using ConcurrentSkipListSet as backing storage.
 * This implementation provides concurrent access and maintains elements in sorted order.
 */
public class ConcurrentLongLinkedOpenHashSet extends LongLinkedOpenHashSet {

    @Serial
    private static final long serialVersionUID = -5532128240738069111L;
    
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    
    private final ConcurrentSkipListSet<Long> backing;

    /**
     * Constructs an empty set with default initial capacity
     */
    public ConcurrentLongLinkedOpenHashSet() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Constructs an empty set with the specified initial capacity
     *
     * @param initial the initial capacity
     */
    public ConcurrentLongLinkedOpenHashSet(final int initial) {
        backing = new ConcurrentSkipListSet<>();
    }

    /**
     * Constructs an empty set with the specified initial capacity and load factor
     *
     * @param initial initial capacity
     * @param loadFactor load factor (ignored in this implementation)
     */
    public ConcurrentLongLinkedOpenHashSet(final int initial, final float loadFactor) {
        this(initial);
    }

    /**
     * Constructs a new set with the elements from the specified iterator
     *
     * @param iterator the iterator providing elements
     */
    public ConcurrentLongLinkedOpenHashSet(final Iterator<Long> iterator) {
        this();
        addAll(iterator);
    }

    /**
     * Constructs a new set with the elements from the specified iterator
     *
     * @param iterator the iterator providing elements
     */
    public ConcurrentLongLinkedOpenHashSet(final LongIterator iterator) {
        this();
        addAll(iterator);
    }

    /**
     * Constructs a new set with elements from array segment
     *
     * @param array source array
     * @param offset starting position
     * @param length number of elements
     */
    public ConcurrentLongLinkedOpenHashSet(final long[] array, final int offset, final int length) {
        this(Math.max(length, 0));
        Objects.requireNonNull(array, "Source array cannot be null");
        LongArrays.ensureOffsetLength(array, offset, length);
        
        for (int i = 0; i < length; i++) {
            add(array[offset + i]);
        }
    }

    /**
     * Constructs a new set with all elements from the array
     *
     * @param array source array
     */
    public ConcurrentLongLinkedOpenHashSet(final long[] array) {
        this(array, 0, array.length);
    }

    // Private helper methods for adding elements from iterators
    private void addAll(LongIterator iterator) {
        Objects.requireNonNull(iterator, "Iterator cannot be null");
        iterator.forEachRemaining(this::add);
    }

    private void addAll(Iterator<Long> iterator) {
        Objects.requireNonNull(iterator, "Iterator cannot be null");
        iterator.forEachRemaining(this::add);
    }

    @Override
    public boolean add(final long k) {
        return backing.add(k);
    }

    @Override
    public boolean addAll(LongCollection c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        return addAll((Collection<Long>) c);
    }

    @Override
    public boolean addAll(Collection<? extends Long> c) {
        Objects.requireNonNull(c, "Collection cannot be null");
        return backing.addAll(c);
    }

    @Override
    public boolean addAndMoveToFirst(final long k) {
        return add(k); // Order is maintained by ConcurrentSkipListSet
    }

    @Override
    public boolean addAndMoveToLast(final long k) {
        return add(k); // Order is maintained by ConcurrentSkipListSet
    }

    @Override
    public void clear() {
        backing.clear();
    }

    @Override
    public LongLinkedOpenHashSet clone() {
        return new ConcurrentLongLinkedOpenHashSet(backing.iterator());
    }

    @Override
    public boolean contains(final long k) {
        return backing.contains(k);
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
    public boolean isEmpty() {
        return backing.isEmpty();
    }

    @Override
    public LongListIterator iterator() {
        return FastUtilHackUtil.wrap(backing.iterator());
    }

    @Override
    public boolean remove(final long k) {
        return backing.remove(k);
    }

    @Override
    public long removeFirstLong() {
        if (isEmpty()) {
            throw new IllegalStateException("Set is empty");
        }
        long first = firstLong();
        remove(first);
        return first;
    }

    @Override
    public long removeLastLong() {
        if (isEmpty()) {
            throw new IllegalStateException("Set is empty");
        }
        long last = lastLong();
        remove(last);
        return last;
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public LongSortedSet subSet(long from, long to) {
        throw new UnsupportedOperationException("subSet operation is not supported");
    }

    @Override
    public LongSortedSet headSet(long to) {
        throw new UnsupportedOperationException("headSet operation is not supported");
    }

    @Override
    public LongListIterator iterator(long from) {
        throw new UnsupportedOperationException("Iterator from position is not supported");
    }

    @Override
    public LongSortedSet tailSet(long from) {
        throw new UnsupportedOperationException("tailSet operation is not supported");
    }

    @Override
    public LongComparator comparator() {
        return null; // Natural ordering is used
    }

    @Override
    public boolean trim() {
        return true; // No-op since ConcurrentSkipListSet handles its own memory
    }

    @Override
    public boolean trim(final int n) {
        return true; // No-op since ConcurrentSkipListSet handles its own memory
    }

    @Override
    public int hashCode() {
        return backing.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ConcurrentLongLinkedOpenHashSet other)) return false;
        return backing.equals(other.backing);
    }

    @Override
    public String toString() {
        return backing.toString();
    }
}
