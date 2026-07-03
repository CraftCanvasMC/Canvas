package io.canvasmc.canvas.util;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import org.jspecify.annotations.Nullable;

/**
 * A list that returns itself for methods that modify the backing set, similar in style to a builder-like object
 *
 * @param <T>
 *     the generic type
 *
 * @author dueris
 * @implNote This is not thread-safe, as the delegate backing this list is not thread-safe
 */
public class CollectingList<T> {
    private final ObjectArrayList<T> delegate = new ObjectArrayList<>();

    /**
     * Get the size of the list
     *
     * @return the size
     */
    public int size() {
        return this.delegate.size();
    }

    /**
     * Get if the list is empty or not
     *
     * @return {@code true} if the list has no elements, {@code false} otherwise
     */
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    /**
     * Gets the iterator for this list
     *
     * @return the iterator instance for the list
     */
    public Iterator<T> iterator() {
        return this.delegate.iterator();
    }

    /**
     * Copies and returns the delegating list
     *
     * @return a copy of the backing list
     */
    public Collection<T> copyOf() {
        return new ObjectArrayList<>(this.delegate);
    }

    /**
     * Casts all values in the list to the specified class type
     *
     * @param castTo
     *     the type of class to be cast to
     * @param <N>
     *     the new generic type
     *
     * @return a new list with all values cast to the new class type
     *
     * @implNote This does not return the same object as {@code this}, and will return a new {@link CollectingList}
     *     object
     */
    public <N> CollectingList<N> castAllTo(final Class<N> castTo) {
        return new CollectingList<N>().addAll(this.delegate.stream().map(castTo::cast).toList());
    }

    /**
     * Adds all the elements in the provided collection if the backing list in this collection does not contain the
     * element already
     *
     * @param toAdd
     *     the collection of elements to attempt to add to this list
     */
    public CollectingList<T> addAllNotPresent(final Collection<T> toAdd) {
        for (final T element : toAdd) {
            if (this.delegate.contains(element)) {
                continue;
            }
            this.delegate.add(element);
        }
        return this;
    }

    /**
     * Adds all the elements in the provided collection to the backing list, equivalent to
     * {@link java.util.List#addAll(java.util.Collection)}
     *
     * @param toAdd
     *     the collection of elements to add to this list
     */
    public CollectingList<T> addAll(final Collection<T> toAdd) {
        this.delegate.addAll(toAdd);
        return this;
    }

    /**
     * Adds the provided element to the collection
     *
     * @param element
     *     the element to add
     */
    public CollectingList<T> add(final T element) {
        this.delegate.add(element);
        return this;
    }

    /**
     * Removes the element provided from the collection
     *
     * @param element
     *     the element to remove
     */
    public CollectingList<T> remove(final T element) {
        this.delegate.remove(element);
        return this;
    }

    /**
     * Sorts the list governed on the comparator provided
     *
     * @param comparator
     *     the comparator to sort with
     */
    public CollectingList<T> sort(final @Nullable Comparator<? super T> comparator) {
        this.delegate.sort(comparator);
        return this;
    }

    /**
     * Clears the collection of all elements
     */
    public CollectingList<T> clear() {
        this.delegate.clear();
        return this;
    }

    /**
     * Gets the element at the specified position in the list
     *
     * @param index
     *     the position in the list
     */
    public T get(final int index) {
        return this.delegate.get(index);
    }

    /**
     * Gets the first element in the list
     *
     * @return the head element
     */
    public T getFirst() {
        return this.delegate.getFirst();
    }

    /**
     * Gets the last element in the list
     *
     * @return the tail element
     */
    public T getLast() {
        return this.delegate.getLast();
    }
}
