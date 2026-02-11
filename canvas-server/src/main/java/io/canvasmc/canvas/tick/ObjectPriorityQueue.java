package io.canvasmc.canvas.tick;

import it.unimi.dsi.fastutil.objects.ObjectArrays;
import it.unimi.dsi.fastutil.objects.ObjectHeaps;
import java.util.Arrays;
import java.util.Comparator;

// based off it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue
public class ObjectPriorityQueue<K> {
	protected transient K[] arr;
	protected int size;
	protected Comparator<? super K> c;

	@SuppressWarnings("unchecked")
	public ObjectPriorityQueue(int capacity, Comparator<? super K> c) {
        if (capacity <= 0) throw new IllegalArgumentException("Must have capacity higher than 0");
        this.arr = (K[])new Object[capacity];
		this.c = c;
	}

	public void add(K x) {
		if (size == arr.length) arr = ObjectArrays.grow(arr, size + 1);
		arr[size++] = x;
		ObjectHeaps.upHeap(arr, size, size - 1, c);
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
		return arr[0];
	}

	public void changed() {
		ObjectHeaps.downHeap(arr, size, 0, c);
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

    public boolean contains(K x) {
        for (int i = 0; i < size; i++) {
            if (arr[i] == x) return true;
        }
        return false;
    }
}
