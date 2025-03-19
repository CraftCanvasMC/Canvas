package com.ishland.flowsched.structs;

import ca.spottedleaf.concurrentutil.util.Priority;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * A priority queue with fixed number of priorities and allows changing priorities of elements.
 *
 * @param <E> the type of elements held in this collection
 */
public class DynamicPriorityQueue<E> {

    private final AtomicIntegerArray taskCount;
    private final ConcurrentLinkedQueue<E>[] priorities;
    private final ConcurrentHashMap<E, Priority> priorityMap = new ConcurrentHashMap<>();

    public DynamicPriorityQueue() {
        Priority[] values = Priority.values();
        this.taskCount = new AtomicIntegerArray(values.length);
        //noinspection unchecked
        this.priorities = new ConcurrentLinkedQueue[values.length];
        for (int i = 0; i < values.length; i++) {
            this.priorities[i] = new ConcurrentLinkedQueue<>();
        }
    }

    public void enqueue(E element, Priority priority) {
        if (priority == null) throw new IllegalArgumentException("Priority cannot be null");
        if (this.priorityMap.putIfAbsent(element, priority) != null)
            throw new IllegalArgumentException("Element already in queue");

        int priorityIndex = priority.ordinal();
        this.priorities[priorityIndex].add(element);
        this.taskCount.incrementAndGet(priorityIndex);
    }

    public boolean changePriority(E element, Priority newPriority) {
        if (newPriority == null) throw new IllegalArgumentException("Priority cannot be null");

        Priority currentPriority = this.priorityMap.get(element);
        if (currentPriority == null || currentPriority == newPriority) {
            return false; // a clear failure
        }

        int currentIndex = currentPriority.ordinal();
        boolean removedFromQueue = this.priorities[currentIndex].remove(element);
        if (!removedFromQueue) {
            return false; // the element is dequeued while we are changing priority
        }

        this.taskCount.decrementAndGet(currentIndex);
        final boolean changeSuccess = this.priorityMap.replace(element, currentPriority, newPriority);
        if (!changeSuccess) {
            return false; // something else may have called remove()
        }

        int newIndex = newPriority.ordinal();
        this.priorities[newIndex].add(element);
        this.taskCount.incrementAndGet(newIndex);
        return true;
    }

    public E dequeue() {
        for (int i = 0; i < this.priorities.length; i++) {
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
        Priority priority = this.priorityMap.remove(element);
        if (priority == null) return;

        boolean removed = this.priorities[priority.ordinal()].remove(element); // best-effort
        if (removed) this.taskCount.decrementAndGet(priority.ordinal());
    }

    public int size() {
        return priorityMap.size();
    }

    public boolean isEmpty() {
        return size() == 0;
    }
}
