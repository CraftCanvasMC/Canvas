package io.canvasmc.canvas.util;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A reference that is only able to be set once, or else it will throw
 *
 * @param <T>
 *     the type of object it is
 */
public class CanonicalReference<T> {
    private T val;

    public CanonicalReference() {
        this(null);
    }

    public CanonicalReference(T val) {
        this.val = val;
    }

    public T setValue(final T value) {
        if (this.val != null) throw new IllegalStateException("Value already set");
        return this.val = value;
    }

    public T value() {
        if (this.val == null) throw new IllegalStateException("Value not set");
        return val;
    }

    public @Nullable T valueSafe() {
        return val;
    }

    public T getOrSet(Supplier<T> empty) {
        return valueSafe() == null ? setValue(empty.get()) : valueSafe();
    }

    public boolean isSet() {
        return valueSafe() != null;
    }
}
