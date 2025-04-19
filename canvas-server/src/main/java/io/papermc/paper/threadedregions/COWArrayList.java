package io.papermc.paper.threadedregions;

import java.lang.reflect.Array;
import java.util.Arrays;

public final class COWArrayList<E> {

    private volatile E[] array;

    public COWArrayList(final Class<E> clazz) {
        this.array = (E[])Array.newInstance(clazz, 0);
    }

    public E[] getArray() {
        return this.array;
    }

    public boolean contains(final E test) {
        for (final E elem : this.array) {
            if (elem == test) {
                return true;
            }
        }

        return false;
    }

    public void add(final E element) {
        synchronized (this) {
            final E[] array = this.array;

            final E[] copy = Arrays.copyOf(array, array.length + 1);
            copy[array.length] = element;

            this.array = copy;
        }
    }

    public boolean remove(final E element) {
        synchronized (this) {
            final E[] array = this.array;
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

            final E[] copy = (E[])Array.newInstance(array.getClass().getComponentType(), array.length - 1);

            System.arraycopy(array, 0, copy, 0, index);
            System.arraycopy(array, index + 1, copy, index, (array.length - 1) - index);

            this.array = copy;
        }

        return true;
    }
}
