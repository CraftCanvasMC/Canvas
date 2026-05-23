package io.canvasmc.canvas.util;

import org.jspecify.annotations.NonNull;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

public class LockedReference<E> {
    private final ReentrantLock writeLock = new ReentrantLock(true);
    private volatile E value;

    public LockedReference(final E default_) {
        this.value = default_;
    }

    public void swapValue(final @NonNull UnaryOperator<E> operator) {
        writeLock.lock();
        try {
            value = operator.apply(value);
        } finally {
            writeLock.unlock();
        }
    }

    public E swapAndGet(final @NonNull UnaryOperator<E> operator) {
        writeLock.lock();
        try {
            return value = operator.apply(value);
        } finally {
            writeLock.unlock();
        }
    }

    public E getValue() {
        return value;
    }
}
