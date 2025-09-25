package io.canvasmc.canvas.util;

import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.Nullable;

public final class ExpiringAtomicReference<T> {

    private static final long RETENTION_MS = 8_000;
    private final AtomicReference<Entry<T>> ref = new AtomicReference<>();

    private long now() {
        return System.currentTimeMillis();
    }

    private void purgeIfExpired() {
        Entry<T> e = ref.get();
        if (e != null && e.isRemoved()) {
            long cutoff = now() - RETENTION_MS;
            if (e.removalTimestamp < cutoff) {
                ref.compareAndSet(e, null);
            }
        }
    }

    /**
     * Sets the value (non-null)
     */
    public void set(T value) {
        if (value == null) throw new IllegalArgumentException("null not allowed, use clear()");
        ref.set(new Entry<>(value, 0L));
    }

    /**
     * Clears the current value, but retains it for 15s
     */
    public void clear() {
        Entry<T> e = ref.get();
        if (e != null && !e.isRemoved()) {
            ref.set(new Entry<>(e.value, now()));
        }
    }

    /**
     * Returns current value (ignores cache)
     */
    public @Nullable T get() {
        purgeIfExpired();
        Entry<T> e = ref.get();
        return (e == null || e.isRemoved()) ? null : e.value;
    }

    /**
     * Returns current value if set, otherwise the last removed value
     * if still within retention window, otherwise null.
     */
    public @Nullable T getCurrentOrCached() {
        purgeIfExpired();
        Entry<T> e = ref.get();
        return (e == null) ? null : e.value;
    }

    /**
     * Atomically sets to the given value and returns the previous value,
     * or null if none. If newValue is null, acts like clear().
     */
    public @Nullable T getAndSet(T newValue) {
        Entry<T> prev;
        if (newValue == null) {
            do {
                prev = ref.get();
                if (prev == null || prev.isRemoved()) {
                    // nothing to clear
                    return (prev == null ? null : prev.value);
                }
            } while (!ref.compareAndSet(prev, new Entry<>(prev.value, now())));
        } else {
            prev = ref.getAndSet(new Entry<>(newValue, 0L));
        }
        return prev == null ? null : prev.value;
    }

    private record Entry<T>(T value, long removalTimestamp) {
        boolean isRemoved() {
            return removalTimestamp > 0L;
        }
    }
}
