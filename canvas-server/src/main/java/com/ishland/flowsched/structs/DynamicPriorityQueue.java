package com.ishland.flowsched.structs;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * A priority queue with fixed number of priorities and allows changing priorities of elements.
 *
 * @param <E> the type of elements held in this collection
 */
public class DynamicPriorityQueue<E> {

    public final AtomicIntegerArray taskCount;
    public final ConcurrentLinkedQueue<E>[] priorities;
    public final ConcurrentHashMap<E, Integer> priorityMap = new ConcurrentHashMap<>();

    public DynamicPriorityQueue(int priorityCount) {
        this.taskCount = new AtomicIntegerArray(priorityCount);
        //noinspection unchecked
        this.priorities = new ConcurrentLinkedQueue[priorityCount];
        for (int i = 0; i < priorityCount; i++) {
            this.priorities[i] = new ConcurrentLinkedQueue<>();
        }
    }

    public void enqueue(E element, int priority) {
        if (priority < 0 || priority >= priorities.length)
            throw new IllegalArgumentException("Priority out of range");
        if (this.priorityMap.putIfAbsent(element, priority) != null)
            throw new IllegalArgumentException("Element already in queue");

        this.priorities[priority].add(element);
        this.taskCount.incrementAndGet(priority);
    }

    // behavior is undefined when changing priority for one item concurrently
    public boolean changePriority(E element, int priority) {
        if (priority < 0 || priority >= priorities.length)
            throw new IllegalArgumentException("Priority out of range");

        int currentPriority = this.priorityMap.getOrDefault(element, -1);
        if (currentPriority == -1 || currentPriority == priority) {
            return false; // a clear failure
        }
        final boolean removedFromQueue = this.priorities[currentPriority].remove(element);
        if (!removedFromQueue) {
            return false; // the element is dequeued while we are changing priority
        }
        this.taskCount.decrementAndGet(currentPriority);
        final Integer put = this.priorityMap.put(element, priority);
        final boolean changeSuccess = put != null && put == currentPriority;
        if (!changeSuccess) {
            return false; // something else may have called remove()
        }
        this.priorities[priority].add(element);
        this.taskCount.incrementAndGet(priority);
        return true;
    }

    public E dequeue() {
        for (int i = 0; i < this.priorities.length; i ++) {
            if (this.taskCount.get(i) == 0) continue;
            E element = priorities[i].poll();
            if (element != null) {
                this.taskCount.decrementAndGet(i);
                this.priorityMap.remove(element);
                return element;
            }
        }
        return null;
    }

    public boolean contains(E element) {
        return priorityMap.containsKey(element);
    }

    public void remove(E element) {
        final Integer remove = this.priorityMap.remove(element);
        // Canvas start
        if (remove == null) {
            return;
        }
        // Canvas end
        boolean removed = this.priorities[remove].remove(element); // best-effort
        if (removed) this.taskCount.decrementAndGet(remove);
    }

    public int size() {
        return priorityMap.size();
    }

    // Canvas start
    public boolean isEmpty() {
        return size() == 0;
    }
    // Canvas end
}
