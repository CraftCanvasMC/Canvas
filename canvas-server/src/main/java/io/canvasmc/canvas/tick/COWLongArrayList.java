package io.canvasmc.canvas.tick;

import java.lang.reflect.Array;
import java.util.Arrays;

public final class COWLongArrayList {

    private volatile long[] array;

    public COWLongArrayList() {
        this.array = new long[0];
    }

    public long[] getArray() {
        return this.array;
    }

    public boolean contains(final long test) {
        for (final long elem : this.array) {
            if (elem == test) {
                return true;
            }
        }

        return false;
    }

    public void add(final long element) {
        synchronized (this) {
            final long[] array = this.array;

            final long[] copy = Arrays.copyOf(array, array.length + 1);
            copy[array.length] = element;

            this.array = copy;
        }
    }

    public boolean remove(final long element) {
        synchronized (this) {
            final long[] array = this.array;
            int index = -1;
            for (int i = 0, len = array.length; i < len; ++i) {
                if (array[i] == element) {
                    index = i;
                    break;
                }
            }

            if (index == -1) {
                return false;
            }

            final long[] copy = (long[]) Array.newInstance(long.class, array.length - 1);

            System.arraycopy(array, 0, copy, 0, index);
            System.arraycopy(array, index + 1, copy, index, (array.length - 1) - index);

            this.array = copy;
        }

        return true;
    }

    public void clear() {
        synchronized (this) {
            this.array = new long[0];
        }
    }
}
