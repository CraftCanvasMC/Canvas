package io.canvasmc.canvas.tick;

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

    public void clear() {
        synchronized (this) {
            this.array = (E[])Array.newInstance(array.getClass().getComponentType(), 0);
        }
    }

    public boolean removeIf(java.util.function.Predicate<? super E> filter) {
        synchronized (this) {
            final E[] array = this.array;
            int len = array.length;
            if (len == 0) {
                return false;
            }

            int newSize = 0;
            for (E e : array) {
                if (!filter.test(e)) {
                    newSize++;
                }
            }

            if (newSize == len) {
                return false;
            }

            @SuppressWarnings("unchecked")
            final E[] newArray = (E[]) Array.newInstance(array.getClass().getComponentType(), newSize);
            int idx = 0;
            for (E e : array) {
                if (!filter.test(e)) {
                    newArray[idx++] = e;
                }
            }

            this.array = newArray;
            return true;
        }
    }

}
