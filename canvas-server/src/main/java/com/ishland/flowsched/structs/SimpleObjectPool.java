package com.ishland.flowsched.structs;

import com.ishland.flowsched.util.Assertions;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class SimpleObjectPool<T> {

    private final Function<SimpleObjectPool<T>, T> constructor;
    private final Consumer<T> initializer;
    private final Consumer<T> postRelease;
    private final int size;

    private final Object[] cachedObjects;
    private int allocatedCount = 0;

    public SimpleObjectPool(Function<SimpleObjectPool<T>, T> constructor, Consumer<T> initializer, Consumer<T> postRelease, int size) {
        this.constructor = Objects.requireNonNull(constructor);
        this.initializer = Objects.requireNonNull(initializer);
        this.postRelease = Objects.requireNonNull(postRelease);
        Assertions.assertTrue(size > 0);
        this.cachedObjects = new Object[size];
        this.size = size;

        for (int i = 0; i < size; i++) {
            final T object = constructor.apply(this);
            this.cachedObjects[i] = object;
        }
    }

    public T alloc() {
        final T object;
        synchronized (this) {
            if (this.allocatedCount >= this.size) { // oversized, falling back to normal alloc
                object = this.constructor.apply(this);
                return object;
            }

            // get an object from the array
            final int ordinal = this.allocatedCount++;
            object = (T) this.cachedObjects[ordinal];
            this.cachedObjects[ordinal] = null;
        }

        this.initializer.accept(object); // initialize the object

        return object;
    }

    public void release(T object) {
        synchronized (this) {
            if (this.allocatedCount == 0) return; // pool is full
            this.postRelease.accept(object);
            this.cachedObjects[--this.allocatedCount] = object; // store the object into the pool
        }
    }

}
