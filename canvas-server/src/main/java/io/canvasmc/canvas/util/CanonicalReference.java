package io.canvasmc.canvas.util;

import org.jspecify.annotations.Nullable;

public class CanonicalReference<T> {
    private T v;

    public CanonicalReference() {
        this(null);
    }

    public CanonicalReference(T val) {
        this.v = val;
    }

    public void setValue(final T value) {
        if (this.v != null) throw new IllegalStateException("Value already set");
        this.v = value;
    }

    public T value() {
        if (this.v == null) throw new IllegalStateException("Value not set");
        return v;
    }

    public @Nullable T valueSafe() {
        return v;
    }
}
