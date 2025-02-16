package io.canvasmc.canvas.server.chunk;

import java.util.Arrays;

public class DynamicChunkPosLongSet {
    private static final long EMPTY_KEY = Long.MIN_VALUE;

    private long[] table;
    private int mask;
    private int size;
    private int threshold;

    public DynamicChunkPosLongSet(int initialCapacity) {
        int capacity = Integer.highestOneBit(Math.max(2, initialCapacity)) << 1;
        this.mask = capacity - 1;
        this.table = new long[capacity];
        Arrays.fill(this.table, EMPTY_KEY);
        this.size = 0;
        this.threshold = capacity >> 2;
    }

    private int indexFor(long key) {
        return (int) (key & mask);
    }

    public boolean containsLong(long key) {
        return table[indexFor(key)] == key;
    }

    public synchronized void add(long key) {
        if (size >= threshold) {
            resize(table.length << 1);
        }

        int index = indexFor(key);
        table[index] = key;
        size++;
    }

    public synchronized void remove(long key) {
        int index = indexFor(key);
        if (table[index] == key) {
            table[index] = EMPTY_KEY;
            size--;
        }
    }

    private void resize(int newCapacity) {
        long[] oldTable = table;
        this.table = new long[newCapacity];
        Arrays.fill(this.table, EMPTY_KEY);
        this.mask = newCapacity - 1;
        this.threshold = newCapacity >> 2;
        this.size = 0;

        for (long key : oldTable) {
            if (key != EMPTY_KEY) {
                add(key);
            }
        }
    }

    public int count() {
        return size;
    }
}
