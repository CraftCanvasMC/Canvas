package io.canvasmc.canvas.util.fastutil;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * Factory methods for creating thread-safe collection instances.
 * Provides convenient methods to create concurrent collections with standard interfaces.
 */
public final class ConcurrentCollections {

    private static final Logger LOGGER = LogManager.getLogger(ConcurrentCollections.class);
    
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private ConcurrentCollections() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Creates a new thread-safe set
     *
     * @param <T> the type of elements maintained by this set
     * @return a new concurrent hash set
     */
    public static <T> Set<T> newHashSet() {
        return newHashSet(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Creates a new thread-safe set with the specified initial capacity
     *
     * @param <T> the type of elements maintained by this set
     * @param initialCapacity initial capacity of the set
     * @return a new concurrent hash set
     * @throws IllegalArgumentException if initialCapacity is negative
     */
    public static <T> Set<T> newHashSet(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        return Collections.newSetFromMap(new ConcurrentHashMap<>(initialCapacity));
    }

    /**
     * Creates a new thread-safe map
     *
     * @param <K> the type of keys maintained by this map
     * @param <V> the type of mapped values
     * @return a new concurrent hash map
     */
    public static <K, V> Map<K, V> newHashMap() {
        return new ConcurrentHashMap<>(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Creates a new thread-safe map with the specified initial capacity
     *
     * @param <K> the type of keys maintained by this map
     * @param <V> the type of mapped values
     * @param initialCapacity initial capacity of the map
     * @return a new concurrent hash map
     * @throws IllegalArgumentException if initialCapacity is negative
     */
    public static <K, V> Map<K, V> newHashMap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        return new ConcurrentHashMap<>(initialCapacity);
    }

    /**
     * Creates a collector that accumulates elements into a thread-safe list
     *
     * @param <T> the type of elements in the list
     * @return a collector that accumulates elements into a CopyOnWriteArrayList
     */
    public static <T> Collector<T, ?, List<T>> toList() {
        return Collectors.toCollection(CopyOnWriteArrayList::new);
    }

    /**
     * Creates a new thread-safe deque
     *
     * @param <T> the type of elements maintained by this deque
     * @return a new concurrent linked deque
     */
    public static <T> Queue<T> newArrayDeque() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating new concurrent linked deque");
        }
        return new ConcurrentLinkedDeque<>();
    }

    /**
     * Creates a new thread-safe list
     *
     * @param <T> the type of elements maintained by this list
     * @return a new copy-on-write array list
     */
    public static <T> List<T> newArrayList() {
        return new CopyOnWriteArrayList<>();
    }

    /**
     * Creates a new thread-safe list with the specified initial elements
     *
     * @param <T> the type of elements maintained by this list
     * @param elements collection containing elements to be placed in the list
     * @return a new copy-on-write array list containing the specified elements
     * @throws NullPointerException if elements is null
     */
    public static <T> List<T> newArrayList(Collection<? extends T> elements) {
        Objects.requireNonNull(elements, "Elements collection cannot be null");
        return new CopyOnWriteArrayList<>(elements);
    }

    /**
     * Creates an unmodifiable view of the specified concurrent map
     *
     * @param <K> the type of keys maintained by this map
     * @param <V> the type of mapped values
     * @param map the map for which an unmodifiable view is to be created
     * @return an unmodifiable view of the specified map
     * @throws NullPointerException if map is null
     */
    public static <K, V> Map<K, V> unmodifiableMap(ConcurrentHashMap<K, V> map) {
        Objects.requireNonNull(map, "Map cannot be null");
        return Collections.unmodifiableMap(map);
    }

    /**
     * Creates a synchronized (thread-safe) view of the specified collection
     *
     * @param <T> the type of elements in the collection
     * @param collection the collection for which a synchronized view is to be created
     * @return a synchronized view of the specified collection
     * @throws NullPointerException if collection is null
     */
    public static <T> Collection<T> synchronizedCollection(Collection<T> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return Collections.synchronizedCollection(collection);
    }
}
