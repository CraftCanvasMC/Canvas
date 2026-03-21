package io.canvasmc.canvas.util.tick;

import it.unimi.dsi.fastutil.HashCommon;
import java.util.AbstractQueue;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import net.minecraft.world.ticks.ScheduledTick;

public final class OrderedTickQueue<T> extends AbstractQueue<ScheduledTick<T>> {
    private static final int INITIAL_CAPACITY = 16;
    private static final Comparator<ScheduledTick<?>> COMPARATOR = Comparator.comparingLong(ScheduledTick::subTickOrder);

    private ScheduledTick<T>[] arr;
    private int lastIndexExclusive;
    private int firstIndex;
    private long currentMaxSubTickOrder = Long.MIN_VALUE;
    private boolean isSorted;
    private ScheduledTick<T> unsortedPeekResult;

    @SuppressWarnings("unchecked")
    public OrderedTickQueue(int capacity) {
        this.arr = (ScheduledTick<T>[]) new ScheduledTick[capacity];
        this.isSorted = true;
    }

    public OrderedTickQueue() {
        this(INITIAL_CAPACITY);
    }

    @Override
    public void clear() {
        Arrays.fill(this.arr, null);
        this.lastIndexExclusive = 0;
        this.firstIndex = 0;
        this.currentMaxSubTickOrder = Long.MIN_VALUE;
        this.isSorted = true;
        this.unsortedPeekResult = null;
    }

    @Override
    public Iterator<ScheduledTick<T>> iterator() {
        if (this.isEmpty()) {
            return Collections.emptyIterator();
        }

        this.sort();
        return new Iterator<>() {
            private int nextIndex = OrderedTickQueue.this.firstIndex;

            @Override
            public boolean hasNext() {
                return this.nextIndex < OrderedTickQueue.this.lastIndexExclusive;
            }

            @Override
            public ScheduledTick<T> next() {
                return OrderedTickQueue.this.arr[this.nextIndex++];
            }
        };
    }

    @Override
    public ScheduledTick<T> poll() {
        if (this.isEmpty()) {
            return null;
        }

        if (!this.isSorted) {
            this.sort();
        }

        int polledIndex = this.firstIndex++;
        ScheduledTick<T> nextTick = this.arr[polledIndex];
        this.arr[polledIndex] = null;
        return nextTick;
    }

    @Override
    public ScheduledTick<T> peek() {
        if (!this.isSorted) {
            return this.unsortedPeekResult;
        }

        return this.lastIndexExclusive > this.firstIndex ? this.arr[this.firstIndex] : null;
    }

    @Override
    public boolean offer(ScheduledTick<T> tick) {
        if (this.lastIndexExclusive >= this.arr.length) {
            this.arr = copyArray(this.arr, HashCommon.nextPowerOfTwo(this.arr.length + 1));
        }

        if (tick.subTickOrder() <= this.currentMaxSubTickOrder) {
            ScheduledTick<T> firstTick = this.isSorted ? (this.size() > 0 ? this.arr[this.firstIndex] : null) : this.unsortedPeekResult;
            this.isSorted = false;
            this.unsortedPeekResult = firstTick == null || tick.subTickOrder() < firstTick.subTickOrder() ? tick : firstTick;
        } else {
            this.currentMaxSubTickOrder = tick.subTickOrder();
        }

        this.arr[this.lastIndexExclusive++] = tick;
        return true;
    }

    @Override
    public int size() {
        return this.lastIndexExclusive - this.firstIndex;
    }

    public ScheduledTick<T> getTickAtIndex(int index) {
        if (!this.isSorted) {
            throw new IllegalStateException("Unexpected access on unsorted queue");
        }

        return this.arr[index];
    }

    public void setTickAtIndex(int index, ScheduledTick<T> tick) {
        if (!this.isSorted) {
            throw new IllegalStateException("Unexpected access on unsorted queue");
        }

        this.arr[index] = tick;
    }

    public void sort() {
        if (this.isSorted) {
            return;
        }

        this.removeNullsAndConsumed();
        Arrays.sort(this.arr, this.firstIndex, this.lastIndexExclusive, COMPARATOR);
        this.isSorted = true;
        this.unsortedPeekResult = null;
    }

    public void removeNullsAndConsumed() {
        int src = this.firstIndex;
        int dst = 0;
        while (src < this.lastIndexExclusive) {
            ScheduledTick<T> tick = this.arr[src];
            if (tick != null) {
                this.arr[dst++] = tick;
            }
            src++;
        }

        this.handleCompaction(dst);
    }

    private void handleCompaction(int size) {
        if (this.arr.length > INITIAL_CAPACITY && size < this.arr.length / 2) {
            this.arr = copyArray(this.arr, size);
        } else {
            Arrays.fill(this.arr, size, this.arr.length, null);
        }

        this.firstIndex = 0;
        this.lastIndexExclusive = size;
        if (size == 0 || !this.isSorted) {
            this.currentMaxSubTickOrder = Long.MIN_VALUE;
        } else {
            ScheduledTick<T> tick = this.arr[size - 1];
            this.currentMaxSubTickOrder = tick == null ? Long.MIN_VALUE : tick.subTickOrder();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> ScheduledTick<T>[] copyArray(ScheduledTick<T>[] src, int size) {
        ScheduledTick<T>[] copy = new ScheduledTick[Math.max(INITIAL_CAPACITY, size)];
        if (size != 0) {
            System.arraycopy(src, 0, copy, 0, Math.min(src.length, size));
        }
        return copy;
    }
}
