package io.canvasmc.canvas.server.chunk;

import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.NewChunkHolder;
import org.jetbrains.annotations.NotNull;

public class DynamicChunkHolderArray {
    private volatile NewChunkHolder[] holders;
    private long[] keys;

    public DynamicChunkHolderArray(int initialCapacity) {
        int size = Integer.highestOneBit(initialCapacity) << 1;
        this.holders = new NewChunkHolder[size];
        this.keys = new long[size];
    }

    private int indexFor(long key) {
        int hash = Long.hashCode(key);
        return Math.abs(hash) % keys.length;
    }

    public NewChunkHolder getByLong(long key) {
        int index = indexFor(key);

        if (keys[index] == 0) {
            return null;
        }

        if (keys[index] == key) {
            return holders[index];
        }

        int i = 1;
        while (keys[(index + i) % keys.length] != 0) {
            if (keys[(index + i) % keys.length] == key) {
                return holders[(index + i) % keys.length];
            }
            i++;
        }

        return null;
    }

    public synchronized void putEntry(@NotNull NewChunkHolder holder, long key) {
        int index = indexFor(key);

        if (keys[index] != 0 && keys[index] != key) {
            resizeIfNeeded();
        }

        keys[index] = key;
        holders[index] = holder;
    }

    public synchronized void remove(long key) {
        int hash = indexFor(key);

        if (keys[hash] == key) {
            holders[hash] = null;
            keys[hash] = 0;
        }
    }

    private void resizeIfNeeded() {
        int totalEntries = 0;
        for (long key : keys) {
            if (key != 0) {
                totalEntries++;
            }
        }

        if (totalEntries >= keys.length / 2) {
            resize(keys.length * 2);
        }
    }

    private void resize(int newSize) {
        newSize = Integer.highestOneBit(newSize) << 1;
        NewChunkHolder[] newHolders = new NewChunkHolder[newSize];
        long[] newKeys = new long[newSize];

        for (int i = 0; i < keys.length; i++) {
            if (keys[i] != 0) {
                long key = keys[i];
                int index = indexFor(key);

                while (newKeys[index] != 0) {
                    index = (index + 1) % newSize;
                }

                newKeys[index] = key;
                newHolders[index] = holders[i];
            }
        }

        holders = newHolders;
        keys = newKeys;
    }

    public int length() {
        return holders.length;
    }
}
