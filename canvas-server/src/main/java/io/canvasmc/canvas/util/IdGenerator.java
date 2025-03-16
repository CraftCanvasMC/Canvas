package io.canvasmc.canvas.util;

import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

// threadsafe id generator that generates ids
// in a synchronized order meaning when an id
// is popped, the id generator will recycle it
// making a system like:
// POLL: 1,2,3,4,5
// POP: 2,4
// NEXT POLL: 2,4,6,7,8,9,10...
public class IdGenerator {
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final Queue<Integer> recycledIds = new PriorityBlockingQueue<>();

    public synchronized int poll() {
        if (!recycledIds.isEmpty()) {
            return recycledIds.poll();
        }
        return nextId.getAndIncrement();
    }

    public synchronized void pop(int id) {
        if (id > 0 && id < nextId.get()) {
            recycledIds.offer(id);
        }
    }
}
