package io.canvasmc.canvas.util;

import org.jspecify.annotations.Nullable;
import java.util.function.Supplier;

public class CanonicalReference<T> {
    private T v;

    public CanonicalReference() {
        this(null);
    }

    public CanonicalReference(T val) {
        this.v = val;
    }

    public T setValue(final T value) {
        if (this.v != null) throw new IllegalStateException("Value already set");
        return this.v = value;
    }

    public T value() {
        if (this.v == null) throw new IllegalStateException("Value not set");
        return v;
    }

    public @Nullable T valueSafe() {
        return v;
    }

    public T getOrSet(Supplier<T> empty) {
        return valueSafe() == null ? setValue(empty.get()) : valueSafe();
    }

    public boolean isSet() {
        return valueSafe() != null;
    }
}
