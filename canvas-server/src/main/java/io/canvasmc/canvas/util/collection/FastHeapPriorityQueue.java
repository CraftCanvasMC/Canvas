package io.canvasmc.canvas.util.collection;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import it.unimi.dsi.fastutil.objects.ObjectHeaps;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A priority queue based off {@link it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue} from fastutil offering minor
 * tweaks and new utility methods
 *
 * @param <K>
 *     the generic type of the queue
 *
 * @author dueris
 */
public class FastHeapPriorityQueue<K> {
    protected int size;
    protected Comparator<? super K> comp;
    public transient K[] arr;

    @SuppressWarnings("unchecked")
    public FastHeapPriorityQueue(final int capacity, final Comparator<? super K> comp, final Class<K> classOf) {
        this.arr = (K[]) Array.newInstance(classOf, Math.max(1, capacity));
        this.comp = comp;
    }

    public void offer(final K element) {
        Objects.requireNonNull(element, "null elements cannot be inserted");
        if (size == arr.length) arr = ObjectArrays.grow(arr, Math.max(size * 2, 1));
        arr[size++] = element;
        ObjectHeaps.upHeap(arr, size, size - 1, comp);
    }

    public boolean remove(final K element) {
        Objects.requireNonNull(element, "cannot remove null element");

        for (int i = 0; i < size; i++) {
            if (arr[i].equals(element)) {
                arr[i] = arr[--size];
                arr[size] = null;
                if (size > i) {
                    ObjectHeaps.upHeap(arr, size, i, comp);
                    ObjectHeaps.downHeap(arr, size, i, comp);
                }
                return true;
            }
        }

        return false;
    }

    @Nullable
    public K poll() {
        if (size == 0) return null;
        final K result = arr[0];
        arr[0] = arr[--size];
        arr[size] = null;
        if (size != 0) ObjectHeaps.downHeap(arr, size, 0, comp);
        return result;
    }

    @Nullable
    public K peek() {
        if (size == 0) return null;
        return arr[0];
    }

    public int size() {
        return size;
    }

    public void clear() {
        Arrays.fill(arr, 0, size, null);
        size = 0;
    }

    public void trim() {
        arr = ObjectArrays.trim(arr, size);
    }

    public Comparator<? super K> comparator() {
        return comp;
    }

    public boolean contains(final K element) {
        for (int i = 0; i < size; i++) {
            final K k = this.arr[i];
            if (k == element) return true;
        }
        return false;
    }
}
