package io.canvasmc.canvas.tick;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Comparator;
import it.unimi.dsi.fastutil.objects.ObjectArrays;
import it.unimi.dsi.fastutil.objects.ObjectHeaps;

public class TickPriorityQueue<K> {
	protected transient K[] arr;
	protected int size;
	protected Comparator<? super K> c;

	@SuppressWarnings("unchecked")
	public TickPriorityQueue(int capacity, Comparator<? super K> c, Class<K> classOf) {
        if (capacity <= 0) capacity = 1;
		this.arr = (K[]) Array.newInstance(classOf, capacity);
		this.c = c;
	}

	public void offer(K element) {
        if (element == null) throw new NullPointerException("Provided element cannot be null");
		if (size == arr.length) arr = ObjectArrays.grow(arr, size + 1);
		arr[size++] = element;
		ObjectHeaps.upHeap(arr, size, size - 1, c);
	}

    public boolean remove(K element) {
        for (int i = 0; i < size; i++) {
            if (arr[i].equals(element)) {
                arr[i] = arr[--size];
                arr[size] = null;
                if (size > i) {
                    ObjectHeaps.upHeap(arr, size, i, c);
                    ObjectHeaps.downHeap(arr, size, i, c);
                }
                return true;
            }
        }
        return false;
    }

	public K poll() {
		if (size == 0) return null;
		final K result = arr[0];
		arr[0] = arr[--size];
		arr[size] = null;
		if (size != 0) ObjectHeaps.downHeap(arr, size, 0, c);
		return result;
	}

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
		return c;
	}
}
