package io.canvasmc.canvas.util;

import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class LockedReference<E> {
    private final ReentrantLock writeLock = new ReentrantLock(true);
    private E value;

    public LockedReference(final @Nullable E default_) {
        this.value = default_;
    }

    public Optional<E> asOptional() {
        return Optional.ofNullable(getValue());
    }

    public boolean isSet() {
        return value != null;
    }

    public void unset() {
        swapValue((_) -> null);
    }

    public void swapValue(final @Nullable E newValue) {
        swapValue((_) -> newValue);
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
