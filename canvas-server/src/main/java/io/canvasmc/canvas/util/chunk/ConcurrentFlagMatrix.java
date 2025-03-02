package io.canvasmc.canvas.util.chunk;

import net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConcurrentFlagMatrix extends WoodlandMansionPieces.SimpleGrid {
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    public ConcurrentFlagMatrix(int n, int m, int fallback) {
        super(n, m, fallback);
    }

    public void set(int i, int j, int value) {
        this.rwLock.writeLock().lock();

        try {
            super.set(i, j, value);
        } finally {
            this.rwLock.writeLock().unlock();
        }

    }

    public void set(int i0, int j0, int i1, int j1, int value) {
        this.rwLock.writeLock().lock();

        try {
            super.set(i0, j0, i1, j1, value);
        } finally {
            this.rwLock.writeLock().unlock();
        }

    }

    public int get(int i, int j) {
        this.rwLock.readLock().lock();

        int var3;
        try {
            var3 = super.get(i, j);
        } finally {
            this.rwLock.readLock().unlock();
        }

        return var3;
    }

    public void setif(int i, int j, int expected, int newValue) {
        if (this.get(i, j) == expected) {
            this.set(i, j, newValue);
        }

    }

    public boolean edgesTo(int i, int j, int value) {
        this.rwLock.readLock().lock();

        boolean var4;
        try {
            var4 = super.edgesTo(i, j, value);
        } finally {
            this.rwLock.readLock().unlock();
        }

        return var4;
    }
}
