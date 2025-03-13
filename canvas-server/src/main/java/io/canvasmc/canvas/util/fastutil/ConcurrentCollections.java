package io.canvasmc.canvas.util.fastutil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class ConcurrentCollections {

    private static final Logger LOGGER = LogManager.getLogger(ConcurrentCollections.class);

    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private ConcurrentCollections() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    public static <T> Set<T> newHashSet() {
        return newHashSet(DEFAULT_INITIAL_CAPACITY);
    }

    public static <T> Set<T> newHashSet(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        return Collections.newSetFromMap(new ConcurrentHashMap<>(initialCapacity));
    }

    public static <K, V> Map<K, V> newHashMap() {
        return new ConcurrentHashMap<>(DEFAULT_INITIAL_CAPACITY);
    }

    public static <K, V> Map<K, V> newHashMap(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity cannot be negative: " + initialCapacity);
        }
        return new ConcurrentHashMap<>(initialCapacity);
    }

    public static <T> Collector<T, ?, List<T>> toList() {
        return Collectors.toCollection(CopyOnWriteArrayList::new);
    }

    public static <T> Queue<T> newArrayDeque() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Creating new concurrent linked deque");
        }
        return new ConcurrentLinkedDeque<>();
    }

    public static <T> List<T> newArrayList() {
        return new CopyOnWriteArrayList<>();
    }

    public static <T> List<T> newArrayList(Collection<? extends T> elements) {
        Objects.requireNonNull(elements, "Elements collection cannot be null");
        return new CopyOnWriteArrayList<>(elements);
    }

    public static <K, V> Map<K, V> unmodifiableMap(ConcurrentHashMap<K, V> map) {
        Objects.requireNonNull(map, "Map cannot be null");
        return Collections.unmodifiableMap(map);
    }

    public static <T> Collection<T> synchronizedCollection(Collection<T> collection) {
        Objects.requireNonNull(collection, "Collection cannot be null");
        return Collections.synchronizedCollection(collection);
    }
}
